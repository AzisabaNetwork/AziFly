package net.azisaba.azifly

import net.azisaba.azifly.commands.FlyCommand
import net.azisaba.azifly.listener.WorldListener
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import kotlin.math.floor

class AziFly : JavaPlugin() {

    var economy : Economy? = null
    val flightSessions = HashMap<UUID, FlightSession>()

    override fun onEnable() {
        saveDefaultConfig()

        if (!setupEconomy()) {
            logger.severe("Vaultが見つからなかったため，プラグインを無効化します．")
            server.pluginManager.disablePlugin(this)
            return
        }

        getCommand("azifly")?.setExecutor(FlyCommand(this))
        server.pluginManager.registerEvents(WorldListener(this), this)
        logger.info("AziFly has been enabled")
    }

    override fun onDisable() {
        val sessions = HashMap(flightSessions)
        sessions.keys.forEach { uuid ->
            val player = Bukkit.getPlayer(uuid)
            if (player != null) {
                stopFlight(player, true)
            }
        }
        flightSessions.clear()
        logger.info("AziFly has been disabled")
    }

    private fun setupEconomy(): Boolean {
        if (!server.pluginManager.isPluginEnabled("Vault")) {
            return false
        }
        val rsp = server.servicesManager.getRegistration(Economy::class.java) ?: return false
        economy = rsp.provider
        return economy != null
    }

    fun getMessage(key: String): String {
        val prefix = config.getString("prefix", "") ?: ""
        val msg = config.getString("messages.$key", "&cメッセージが見つかりません: $key") ?: ""
        return ("$prefix $msg").color()
    }

    fun stopFlight(player: Player, refund: Boolean) {
        val session = flightSessions.remove(player.uniqueId)

        player.allowFlight = false
        player.isFlying = false
        player.sendMessage(getMessage("fly-disabled"))

        if (session != null) {
            server.scheduler.cancelTask(session.taskId)
            if (refund && session.paidAmount > 0) {
                val elapsedMillis = System.currentTimeMillis() - session.startTime
                val remainingSeconds = session.durationSeconds - (elapsedMillis / 1000.0)

                if (remainingSeconds > 0) {
                    val refundAmount = session.paidAmount * (remainingSeconds / session.durationSeconds)
                    val finalRefund = floor(refundAmount)

                    if (finalRefund > 0) {
                        economy?.depositPlayer(player, finalRefund)
                        val msg = getMessage("fly-refunded").replace("{amount}", finalRefund.toInt().toString())
                        player.sendMessage(msg)
                    }
                }
            }
        }
    }

    data class FlightSession(
        val taskId: Int,
        val startTime: Long,
        val paidAmount: Double,
        val durationSeconds: Int
    )
}

fun String.color(): String {
    return org.bukkit.ChatColor.translateAlternateColorCodes('&', this)
}
