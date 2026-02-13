package net.azisaba.azifly.commands

import net.azisaba.azifly.AziFly
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

        if (cost > 0) {
            val balance = plugin.economy?.getBalance(sender as Player) ?: 0.0
            if (balance < cost) {
                val msg = plugin.getMessage("insufficient-funds").replace("{amount}", cost.toInt().toString())
                sender.sendMessage(msg)
                return true
            }
            plugin.economy?.withdrawPlayer(sender as Player, cost)
            val payMsg = plugin.getMessage("fly-paid").replace("{amount}", cost.toInt().toString())
            sender.sendMessage(payMsg)
        }

        targetPlayer.allowFlight = true
        targetPlayer.isFlying = true

        if (isAdmin || !isSelf) {
            targetPlayer.sendMessage(plugin.getMessage("admin-bypass"))
            if (sender != targetPlayer) {
                sender.sendMessage(plugin.getMessage("fly-enabled-admin").replace("{name}", targetPlayer.name))
            }
        } else {
            val msg = plugin.getMessage("fly-enabled").replace("{time}", seconds.toString())
            targetPlayer.sendMessage(msg)
        }

        if (isAdmin || !isSelf) return true
        val taskId = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (targetPlayer.isOnline) {
                plugin.stopFlight(targetPlayer, false)
            }
        }, seconds.toLong() * 20L).taskId

        val session = AziFly.FlightSession(taskId, System.currentTimeMillis(), cost, seconds)
        plugin.flightSessions[targetPlayer.uniqueId] = session

        return true
    }
}