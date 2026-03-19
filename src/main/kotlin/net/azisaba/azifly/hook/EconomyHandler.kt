package net.azisaba.azifly.hook

import net.azisaba.azifly.AziFly
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer

object EconomyHandler {
    var isHooked = false
        private set

    private var economy: Economy? = null

    fun init(plugin: AziFly) {
        if (plugin.server.pluginManager.isPluginEnabled("Vault")) {
            val rsp = Bukkit.getServicesManager().getRegistration(Economy::class.java)
            if (rsp != null) {
                economy = rsp.provider
                isHooked = true
                plugin.logger.info("Vault hooked successfully.")
            }
        }
    }

    fun getBalance(player: OfflinePlayer): Double {
        return economy?.getBalance(player) ?: 0.0
    }

    fun has(player: OfflinePlayer, amount: Double): Boolean {
        return economy?.has(player, amount) ?: false
    }

    fun withdraw(player: OfflinePlayer, amount: Double): Boolean {
        return economy?.withdrawPlayer(player, amount)?.transactionSuccess() ?: false
    }

    fun deposit(player: OfflinePlayer, amount: Double): Boolean {
        return economy?.depositPlayer(player, amount)?.transactionSuccess() ?: false
    }
}