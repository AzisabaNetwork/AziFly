package net.azisaba.azifly.commands

import net.azisaba.azifly.AziFly
import net.azisaba.lifetutorialassist.sql.DBConnector
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import kotlin.math.ceil

class FlyCommand(private val plugin : AziFly) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isNotEmpty() && args[0].equals("reload", true)) {
            if (!sender.hasPermission("azifly.admin")) {
                sender.sendMessage(plugin.getMessage("no-permission"))
                return true
            }
            plugin.reloadConfig()
            plugin.saveDefaultConfig()
            sender.sendMessage(plugin.getMessage("reload-complete"))
            return true
        }

        val costPerMinute = plugin.config.getDouble("cost-per-minutes", 300.0)
        var seconds: Int = plugin.config.getInt("default-time-minutes", 10) * 60
        var targetPlayer: Player? = null

        if (args.isEmpty()) {
            if (sender !is Player) {
                sender.sendMessage(plugin.getMessage("console-error"))
                return true
            }
            targetPlayer = sender
        } else {
            val firstArgAsInt = args[0].toIntOrNull()
            if (firstArgAsInt != null) {
                if (sender !is Player) {
                    sender.sendMessage(plugin.getMessage("console-error"))
                    return true
                }
                targetPlayer = sender
                seconds = firstArgAsInt
            } else {
                if (!sender.hasPermission("azifly.admin")) {
                    sender.sendMessage(plugin.getMessage("no-permission"))
                    return true
                }

                targetPlayer = Bukkit.getPlayer(args[0])
                if (targetPlayer == null) {
                    sender.sendMessage(plugin.getMessage("player-not-found"))
                    return true
                }
                if (args.size >= 2) {
                    args[1].toIntOrNull()?.let { seconds = it }
                }
            }
        }

        if (plugin.flightSessions.containsKey(targetPlayer.uniqueId)) {
            plugin.stopFlight(targetPlayer, true)
            return true
        } else if (targetPlayer.allowFlight) {
            targetPlayer.allowFlight = false
            targetPlayer.isFlying = false
            sender.sendMessage(plugin.getMessage("fly-disabled"))
            return true
        }

        val disabledWorlds = plugin.config.getStringList("disabled-worlds")
        if (disabledWorlds.contains(targetPlayer.world.name) && !targetPlayer.hasPermission("azifly.admin")) {
            sender.sendMessage(plugin.getMessage("world-disabled"))
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
            var isTutorialIncomplete = false
            if (cost > 0) {
                try {
                    val taskId = plugin.config.getString("tutorial-task-id", "fly_intro") ?: "fly_intro"
                    val isCompleted = DBConnector.isCompleted(targetPlayer.uniqueId, taskId)
                    isTutorialIncomplete = !isCompleted
                } catch (e: Exception) {
                    plugin.logger.warning("LifeTutorialAssistとの通信に失敗しました: ${e.message}")
                }
            }

            Bukkit.getScheduler().runTask(plugin, Runnable {
                var finalCost = cost

                if (isTutorialIncomplete) {
                    finalCost = 0.0
                    targetPlayer.sendMessage(plugin.getMessage("tutorial-free"))
                }
                if (finalCost > 0) {
                    val balance = plugin.economy?.getBalance(sender as Player) ?: 0.0
                    if (balance < finalCost) {
                        val msg = plugin.getMessage("insufficient-funds").replace("{amount}", finalCost.toInt().toString())
                        sender.sendMessage(msg)
                        return@Runnable
                    }
                    plugin.economy?.withdrawPlayer(sender as Player, finalCost)
                    sender.sendMessage(plugin.getMessage("fly-paid").replace("{amount}", finalCost.toInt().toString()))
                }

                targetPlayer.allowFlight = true
                targetPlayer.isFlying = true

                val m = seconds / 60
                val s = seconds % 60
                val timeStr = buildString {
                    if (m > 0) append("${m}分")
                    if (s > 0 || m == 0) append("${s}秒")
                }

                if (isAdmin || !isSelf) {
                    targetPlayer.sendMessage(plugin.getMessage("admin-bypass"))
                    if (sender != targetPlayer) {
                        sender.sendMessage(plugin.getMessage("fly-enabled-by-admin").replace("{name}", targetPlayer.name))
                    }
                } else {
                    val msg = plugin.getMessage("fly-enabled")
                        .replace("{time}秒", timeStr)
                        .replace("{time}", timeStr)
                    targetPlayer.sendMessage(msg)
                }

                if (isAdmin || !isSelf) return@Runnable
                val taskId = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                    if (targetPlayer.isOnline) {
                        plugin.stopFlight(targetPlayer, false)
                    }
                }, seconds.toLong() * 20L).taskId

                val session = AziFly.FlightSession(taskId, System.currentTimeMillis(), finalCost, seconds)
                plugin.flightSessions[targetPlayer.uniqueId] = session
            })
        })
        return true
    }
}