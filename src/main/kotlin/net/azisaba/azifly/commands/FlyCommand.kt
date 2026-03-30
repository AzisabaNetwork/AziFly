package net.azisaba.azifly.commands

import net.azisaba.azifly.AziFly
import net.azisaba.azifly.hook.EconomyHandler
import net.azisaba.azifly.manager.MessageManager
import net.azisaba.azifly.manager.sendLangMessage
import net.azisaba.lifetutorialassist.sql.DBConnector
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import kotlin.math.ceil

class FlyCommand(private val plugin : AziFly) : CommandExecutor, TabCompleter {

    private fun parseTime(input: String): Int? {
        val regex = Regex("^(\\d+)([smh]?)$", RegexOption.IGNORE_CASE)
        val matchResult = regex.find(input) ?: return input.toIntOrNull()
        
        val value = matchResult.groupValues[1].toInt()
        val unit = matchResult.groupValues[2].lowercase()
        
        return when (unit) {
            "s" -> value
            "m" -> value * 60
            "h" -> value * 3600
            else -> value
        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isNotEmpty() && args[0].equals("reload", true)) {
            if (!sender.hasPermission("azifly.admin")) {
                sender.sendLangMessage("no-permission")
                return true
            }
            plugin.reloadConfig()
            plugin.saveDefaultConfig()
            sender.sendLangMessage("reload-complete")
            return true
        }

        val costPerMinute = plugin.config.getDouble("cost-per-minutes", 300.0)
        var seconds: Int = plugin.config.getInt("default-time-minutes", 10) * 60
        var targetPlayer: Player? = null

        if (args.isEmpty()) {
            if (sender !is Player) {
                sender.sendLangMessage("console-error")
                return true
            }
            targetPlayer = sender
        } else {
            val firstArgTime = parseTime(args[0])
            if (firstArgTime != null) {
                if (sender !is Player) {
                    sender.sendLangMessage("console-error")
                    return true
                }
                targetPlayer = sender
                seconds = firstArgTime
            } else {
                if (!sender.hasPermission("azifly.admin")) {
                    sender.sendLangMessage("no-permission")
                    return true
                }

                targetPlayer = Bukkit.getPlayer(args[0])
                if (targetPlayer == null) {
                    sender.sendLangMessage("player-not-found")
                    return true
                }
                if (args.size >= 2) {
                    parseTime(args[1])?.let { seconds = it }
                }
            }
        }

        if (plugin.flightSessions.containsKey(targetPlayer.uniqueId)) {
            plugin.stopFlight(targetPlayer, true)
            return true
        } else if (targetPlayer.allowFlight) {
            sender.sendLangMessage("already-flying")
            return true
        }

        val disabledWorlds = plugin.config.getStringList("disabled-worlds")
        if (disabledWorlds.contains(targetPlayer.world.name) && !targetPlayer.hasPermission("azifly.admin")) {
            sender.sendLangMessage("world-disabled")
            return true
        }

        var cost = 0.0
        val isAdmin = sender.hasPermission("azifly.admin")
        val isSelf = (sender is Player && sender.uniqueId == targetPlayer.uniqueId)

        if (isSelf && !isAdmin) {
            val minutes = ceil(seconds / 60.0)
            cost = minutes * costPerMinute
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            /*
            var isTutorialIncomplete = false
            if (cost > 0) {
                try {
                    val taskId = plugin.config.getString("tutorial-task-id", "fly_intro") ?: "fly_intro"
                    val isCompleted = DBConnector.isCompleted(targetPlayer.uniqueId, taskId)
                    isTutorialIncomplete = !isCompleted
                } catch (e: Exception) {
                    plugin.logger.warning("Failed to fetch tutorial status from LifeTutorialAssist: ${e.message}")
                }
            }
             */

            Bukkit.getScheduler().runTask(plugin, Runnable {
                var finalCost = cost

                /*
                if (isTutorialIncomplete) {
                    finalCost = 0.0
                    targetPlayer.sendLangMessage("tutorial-free")
                }
                 */

                if (finalCost > 0) {
                    val balance = EconomyHandler.getBalance(sender as Player)
                    if (balance < finalCost) {
                        sender.sendLangMessage("insufficient-funds", "amount" to finalCost.toInt().toString())
                        return@Runnable
                    }
                    EconomyHandler.withdraw(sender as Player, finalCost)
                    sender.sendLangMessage("fly-paid", "amount" to finalCost.toInt().toString())
                }

                targetPlayer.allowFlight = true
                targetPlayer.isFlying = true

                val h = seconds / 3600
                val m = (seconds % 3600) / 60
                val s = seconds % 60
                val unitH = MessageManager.getMessage(targetPlayer, "unit-hour")
                val unitM = MessageManager.getMessage(targetPlayer, "unit-minute")
                val unitS = MessageManager.getMessage(targetPlayer, "unit-second")
                val timeStr = buildString {
                    if (h > 0) append("${h}$unitH")
                    if (m > 0) append("${m}$unitM")
                    if (s > 0 || (h == 0 && m == 0)) append("${s}$unitS")
                }

                if (isAdmin || !isSelf) {
                    targetPlayer.sendLangMessage("admin-bypass")
                    if (sender != targetPlayer) {
                        sender.sendLangMessage("fly-enabled-by-admin", "name" to targetPlayer.name)
                    }
                } else {
                    targetPlayer.sendLangMessage("fly-enabled", "time" to timeStr)
                }

                val taskId = if (isAdmin || !isSelf) {
                    -1
                } else {
                    Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                        if (targetPlayer.isOnline) {
                            plugin.stopFlight(targetPlayer, false)
                        }
                    }, seconds.toLong() * 20L).taskId
                }

                val session = AziFly.FlightSession(taskId, System.currentTimeMillis(), finalCost, seconds)
                plugin.flightSessions[targetPlayer.uniqueId] = session
            })
        })
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String>? {
        if (args.size == 1) {
            val suggestions = mutableListOf<String>()
            if (sender.hasPermission("azifly.admin")) {
                suggestions.add("reload")
                Bukkit.getOnlinePlayers().forEach { suggestions.add(it.name) }
            }
            suggestions.addAll(listOf("10m", "30m", "1h"))
            return suggestions.filter { it.startsWith(args[0], true) }
        }
        if (args.size == 2) {
            if (sender.hasPermission("azifly.admin") && parseTime(args[0]) == null && args[0].lowercase() != "reload") {
                return listOf("10m", "30m", "1h").filter { it.startsWith(args[1], true) }
            }
        }
        return emptyList()
    }
}