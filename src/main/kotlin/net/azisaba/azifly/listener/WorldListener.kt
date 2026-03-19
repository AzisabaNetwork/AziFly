package net.azisaba.azifly.listener

import net.azisaba.azifly.AziFly
import net.azisaba.azifly.manager.sendLangMessage
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent

class WorldListener(private val plugin: AziFly) : Listener {

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        val player = event.player
        val disabledWorlds = plugin.config.getStringList("disabled-worlds")
        if (disabledWorlds.contains(player.world.name)) {
            if (player.hasPermission("azifly.admin")) return

            if (plugin.flightSessions.containsKey(player.uniqueId)) {
                plugin.stopFlight(player, true)
                player.sendLangMessage("world-disabled")
            } else if (player.allowFlight) {
                player.allowFlight = false
                player.isFlying = false
                player.sendLangMessage("world-disabled")
            }
        }
    }
}