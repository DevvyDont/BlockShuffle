package me.devvy.blockshuffle.config

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * Data class representing a block's configuration.
 */
data class BlockConfig(
    val material: Material,
    val difficulty: Int,
    val enabled: Boolean
)

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
        config.createSection("blocks")
        config.set("version", "2.0")

        config.save(configFile)
        plugin.logger.info("Created default config.yml")
    }

    /**
     * Loads the config.yml file into memory.
     */
    fun load() {
        config = YamlConfiguration.loadConfiguration(configFile)
        migrateOldConfigIfNeeded()
    }

    /**
     * Migrates old list-based config to new map-based structure.
     */
    private fun migrateOldConfigIfNeeded() {
        if (config.contains("blocks.enabled") || config.contains("blocks.disabled")) {
            plugin.logger.info("Migrating old config format to new structure...")

            val enabled = getEnabledBlocks()
            val disabled = getDisabledBlocks()

            // Clear old sections
            config.set("blocks.enabled", null)
            config.set("blocks.disabled", null)

            // Set default difficulty of 1 for migrated blocks
            for (material in enabled) {
                setBlockConfig(material, BlockConfig(material, 1, true))
            }
            for (material in disabled) {
                setBlockConfig(material, BlockConfig(material, 1, false))
            }

            save()
            plugin.logger.info("Migration complete. All blocks set to difficulty 1.")
        }
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
     * Gets all block configurations from config.
     */
    fun getAllBlockConfigs(): Map<Material, BlockConfig> {
        val blocksSection = config.getConfigurationSection("blocks") ?: return emptyMap()
        val result = mutableMapOf<Material, BlockConfig>()

        for (key in blocksSection.getKeys(false)) {
            try {
                val material = Material.valueOf(key)
                val difficulty = blocksSection.getInt("$key.difficulty", 1)
                val enabled = blocksSection.getBoolean("$key.enabled", true)
                result[material] = BlockConfig(material, difficulty, enabled)
            } catch (e: IllegalArgumentException) {
                plugin.logger.warning("Unknown material in config: $key")
            }
        }

        return result
    }

    /**
     * Gets the list of enabled blocks from config.
     */
    fun getEnabledBlocks(): List<Material> {
        return getAllBlockConfigs().filter { it.value.enabled }.keys.toList()
    }

    /**
     * Gets the list of disabled blocks from config.
     */
    fun getDisabledBlocks(): List<Material> {
        return getAllBlockConfigs().filter { !it.value.enabled }.keys.toList()
    }

    /**
     * Gets the difficulty of a specific block.
     */
    fun getBlockDifficulty(material: Material): Int {
        return getAllBlockConfigs()[material]?.difficulty ?: 1
    }

    /**
     * Sets the configuration for a specific block.
     */
    fun setBlockConfig(material: Material, blockConfig: BlockConfig) {
        config.set("blocks.${material.name}.difficulty", blockConfig.difficulty)
        config.set("blocks.${material.name}.enabled", blockConfig.enabled)
        save()
    }

    /**
     * Sets the difficulty for a specific block.
     */
    fun setBlockDifficulty(material: Material, difficulty: Int) {
        val currentConfig = getAllBlockConfigs()[material] ?: BlockConfig(material, 1, true)
        setBlockConfig(material, currentConfig.copy(difficulty = difficulty))
    }

    /**
     * Toggles a block's enabled status.
     * Returns true if the block is now enabled, false if disabled.
     */
    fun toggleBlock(material: Material): Boolean {
        val currentConfig = getAllBlockConfigs()[material] ?: BlockConfig(material, 1, true)
        val newEnabled = !currentConfig.enabled
        setBlockConfig(material, currentConfig.copy(enabled = newEnabled))
        return newEnabled
    }

    /**
     * Initializes blocks from a map of material to difficulty, setting all as enabled.
     */
    fun initializeBlocks(blockDifficulties: Map<Material, Int>) {
        for ((material, difficulty) in blockDifficulties) {
            setBlockConfig(material, BlockConfig(material, difficulty, true))
        }
    }

    // Deprecated methods for backward compatibility
    /**
     * @deprecated Use getEnabledBlocks() instead.
     */
    fun setEnabledBlocks(blocks: List<Material>) {
        // No-op, kept for compatibility
    }

    /**
     * @deprecated Use getDisabledBlocks() instead.
     */
    fun setDisabledBlocks(blocks: List<Material>) {
        // No-op, kept for compatibility
    }
}
