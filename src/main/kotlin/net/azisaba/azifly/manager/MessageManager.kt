package net.azisaba.azifly.manager

import net.azisaba.azifly.AziFly
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File

object MessageManager {
    private lateinit var plugin: AziFly
    private val loadedLangs = mutableMapOf<String, FileConfiguration>()

    private var prefix: String = ""
    private var defaultLang: String = "ja"

    fun init(plugin: AziFly) {
        this.plugin = plugin
        loadedLangs.clear()

        plugin.saveDefaultConfig()
        plugin.reloadConfig()

        prefix = plugin.config.getString("prefix") ?: "&7[&dAziFly7]&r"
        defaultLang = plugin.config.getString("default_language")?.lowercase() ?: "ja"

        val msgFolder = File(plugin.dataFolder, "messages")
        if (!msgFolder.exists()) {
            msgFolder.mkdirs()
        }

        val defaultFiles = listOf("lang_ja.yml", "lang_en.yml")
        for (fileName in defaultFiles) {
            val file = File(msgFolder, fileName)
            if (!file.exists()) {
                try {
                    plugin.saveResource("messages/$fileName", false)
                } catch (e: Exception) {
                }
            }
        }

        val files = msgFolder.listFiles()?.filter { it.extension == "yml" } ?: emptyList()
        if (files.isEmpty()) {
            plugin.logger.warning("No language files found in /messages/ folder!")
        }

        files.forEach { file ->
            val langCode = file.nameWithoutExtension.removePrefix("lang_").lowercase()
            val config = YamlConfiguration.loadConfiguration(file)
            loadedLangs[langCode] = config
            plugin.logger.info("Loaded language: $langCode (${file.name})")
        }
        plugin.logger.info("MessageManager initialized. Default language: $defaultLang, Total loaded: ${loadedLangs.size}")
    }

    fun sendMessage(sender: CommandSender, key: String, vararg placeholders: Pair<String, String>) {
        val rawMessage = getMessage(sender, key)
        var finalMessage = "$prefix $rawMessage"

        for ((pKey, pValue) in placeholders) {
            finalMessage = finalMessage.replace("{$pKey}", pValue)
        }

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', finalMessage))
    }

    public fun getMessage(sender: CommandSender, key: String): String {
        val userLocale = if (sender is Player) {
            sender.locale.lowercase().split("_").firstOrNull() ?: defaultLang
        } else {
            defaultLang
        }

        val config = loadedLangs[userLocale] ?: loadedLangs[defaultLang] ?: loadedLangs.values.firstOrNull()
        val message = config?.getString(key)

        return if (message == null) {
            plugin.logger.warning("Missing message key: '$key' for language: '$userLocale'")
            "&cMessage not found: $key"
        } else {
            message
        }
    }
}

fun CommandSender.sendLangMessage(key: String, vararg placeholders: Pair<String, String>) {
    MessageManager.sendMessage(this, key, *placeholders)
}