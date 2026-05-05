package me.devvy.blockshuffle.config

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * Manages plugin configuration (config.yml) including block persistence.
 * Handles loading, saving, and YAML serialization for blocks and other settings.
 */
class ConfigManager(private val plugin: JavaPlugin) {

    private val configFile: File = File(plugin.dataFolder, "config.yml")
    private lateinit var config: YamlConfiguration

    init {
        ensureConfigExists()
        load()
    }

    /**
     * Ensures the config.yml file and data folder exist.
     */
    private fun ensureConfigExists() {
        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
        }

        if (!configFile.exists()) {
            createDefaultConfig()
        }
    }

    /**
     * Creates a default config.yml if it doesn't exist.
     */
    private fun createDefaultConfig() {
        configFile.createNewFile()
        val config = YamlConfiguration()

        // Initialize with default empty blocks section
        config.set("blocks.enabled", emptyList<String>())
        config.set("blocks.disabled", emptyList<String>())
        config.set("version", "1.0")

        config.save(configFile)
        plugin.logger.info("Created default config.yml")
    }

    /**
     * Loads the config.yml file into memory.
     */
    fun load() {
        config = YamlConfiguration.loadConfiguration(configFile)
    }

    /**
     * Saves the current config to config.yml.
     */
    fun save() {
        try {
            config.save(configFile)
        } catch (e: Exception) {
            plugin.logger.severe("Failed to save config.yml: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Gets the list of enabled blocks from config.
     */
    fun getEnabledBlocks(): List<Material> {
        val enabledNames = config.getStringList("blocks.enabled")
        return enabledNames.mapNotNull { name ->
            try {
                Material.valueOf(name)
            } catch (e: IllegalArgumentException) {
                plugin.logger.warning("Unknown material in config: $name")
                null
            }
        }
    }

    /**
     * Gets the list of disabled blocks from config.
     */
    fun getDisabledBlocks(): List<Material> {
        val disabledNames = config.getStringList("blocks.disabled")
        return disabledNames.mapNotNull { name ->
            try {
                Material.valueOf(name)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    /**
     * Sets the list of enabled blocks and saves to config.
     */
    fun setEnabledBlocks(blocks: List<Material>) {
        val blockNames = blocks.map { it.name }
        config.set("blocks.enabled", blockNames)
        save()
        load()
    }

    /**
     * Sets the list of disabled blocks and saves to config.
     */
    fun setDisabledBlocks(blocks: List<Material>) {
        val blockNames = blocks.map { it.name }
        config.set("blocks.disabled", blockNames)
        save()
        load()
    }

    /**
     * Toggles a block's enabled status.
     * Returns true if the block is now enabled, false if disabled.
     */
    fun toggleBlock(material: Material): Boolean {
        val enabled = getEnabledBlocks().toMutableList()
        val disabled = getDisabledBlocks().toMutableList()

        return when {
            enabled.contains(material) -> {
                enabled.remove(material)
                disabled.add(material)
                setEnabledBlocks(enabled)
                setDisabledBlocks(disabled)
                false
            }
            disabled.contains(material) -> {
                disabled.remove(material)
                enabled.add(material)
                setEnabledBlocks(enabled)
                setDisabledBlocks(disabled)
                true
            }
            else -> {
                // Not tracked, so add as enabled
                enabled.add(material)
                setEnabledBlocks(enabled)
                true
            }
        }
    }

    /**
     * Initializes blocks list from a given list, saving to config.
     */
    fun initializeBlocks(blocks: List<Material>, disabled: List<Material>) {
        val enabled = blocks.toMutableList()
        val disabled = disabled.toMutableList()
        setEnabledBlocks(enabled)
        setDisabledBlocks(disabled)
    }
}

