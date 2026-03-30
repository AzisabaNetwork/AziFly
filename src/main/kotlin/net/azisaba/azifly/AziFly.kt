package net.azisaba.azifly

import net.azisaba.azifly.commands.FlyCommand
import net.azisaba.azifly.hook.EconomyHandler
import net.azisaba.azifly.listener.WorldListener
import net.azisaba.azifly.manager.MessageManager
import net.azisaba.azifly.manager.sendLangMessage
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import kotlin.math.floor

class AziFly : JavaPlugin() {

    val flightSessions = HashMap<UUID, FlightSession>()

    override fun onEnable() {
        MessageManager.init(this)
        EconomyHandler.init(this)
        saveDefaultConfig()
        val flyCommand = FlyCommand(this)
        getCommand("azifly")?.setExecutor(flyCommand)
        getCommand("azifly")?.tabCompleter = flyCommand
        server.pluginManager.registerEvents(WorldListener(this), this)
        logger.info("AziFly has been enabled.")
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
        logger.info("AziFly has been disabled.")
    }


    fun stopFlight(player: Player, refund: Boolean) {
        val session = flightSessions.remove(player.uniqueId)

        player.allowFlight = false
        player.isFlying = false
        player.sendLangMessage("fly-disabled")

        if (session != null) {
            if (session.taskId != -1) {
                server.scheduler.cancelTask(session.taskId)
            }
            if (refund && session.paidAmount > 0) {
                val elapsedMillis = System.currentTimeMillis() - session.startTime
                val remainingSeconds = session.durationSeconds - (elapsedMillis / 1000.0)

                if (remainingSeconds > 0) {
                    val refundAmount = session.paidAmount * (remainingSeconds / session.durationSeconds)
                    val finalRefund = floor(refundAmount)

                    if (finalRefund > 0) {
                        EconomyHandler.deposit(player, finalRefund)
                        player.sendLangMessage("fly-refunded", "amount" to finalRefund.toInt().toString())
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