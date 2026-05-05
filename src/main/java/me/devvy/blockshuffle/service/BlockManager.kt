package me.devvy.blockshuffle.service

import me.devvy.blockshuffle.config.ConfigManager
import me.devvy.blockshuffle.util.BlockValidator
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.plugin.java.JavaPlugin
import java.io.IOException
import java.io.InputStreamReader

/**
 * Centralized service for managing valid game blocks.
 * Coordinates between ConfigManager, BlockValidator, and resource files.
 * Provides methods to load, validate, toggle, and access blocks for gameplay.
 */
class BlockManager(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager
) {

    private var allValidBlocks: List<Material> = emptyList()

    init {
        initialize()
    }

    /**
     * Initializes the block manager by loading from config or resources.
     */
    private fun initialize() {
        val enabledFromConfig = configManager.getEnabledBlocks()

        // If config is empty, load from blocks.txt resource file (first run)
        if (enabledFromConfig.isEmpty()) {
            plugin.logger.info("No blocks found in config, loading from blocks.txt...")
            val blocksFromFile = loadBlocksFromResource()
            val disabledFromFile = mutableListOf<Material>()
            for (block in getAllValidGameBlocks())
                if (!blocksFromFile.contains(block))
                    disabledFromFile.add(block)

            configManager.initializeBlocks(blocksFromFile, disabledFromFile)
            allValidBlocks = blocksFromFile
        } else {
            allValidBlocks = enabledFromConfig
        }

        plugin.logger.info("Loaded ${allValidBlocks.size} valid blocks")
    }

    /**
     * Loads blocks from the bundled blocks.txt resource file.
     */
    private fun loadBlocksFromResource(): List<Material> {
        val blocks = mutableListOf<Material>()
        try {
            val inputStream = plugin.javaClass.getResourceAsStream("/blocks.txt")
                ?: return blocks

            inputStream.bufferedReader().use { br ->
                br.forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        try {
                            blocks.add(Material.valueOf(trimmed))
                        } catch (e: IllegalArgumentException) {
                            plugin.logger.warning("Unknown material in blocks.txt: $trimmed")
                        }
                    }
                }
            }
        } catch (e: IOException) {
            plugin.logger.severe("Failed to load blocks.txt: ${e.message}")
            e.printStackTrace()
        }
        return blocks
    }

    /**
     * Gets all currently enabled blocks for gameplay.
     */
    fun getEnabledBlocks(): List<Material> {
        return allValidBlocks
    }

    /**
     * Gets all currently enabled blocks for gameplay.
     */
    fun getDisabledBlocks(): List<Material> {
        var ret: List<Material> = listOf()
        for (block in getAllValidGameBlocks())
            if (!isBlockEnabled(block))
                ret = ret.plus(block)
        return ret
    }

    /**
     * Refreshes enabled blocks from config (call after external config changes).
     */
    fun refreshEnabledBlocks() {
        allValidBlocks = configManager.getEnabledBlocks()
        plugin.logger.info("Refreshed enabled blocks: ${allValidBlocks.size} blocks active")
    }

    /**
     * Gets all blocks that are suitable for the game (validated).
     * This includes both enabled and disabled blocks in the UI.
     */
    fun getAllValidGameBlocks(): List<Material> {
        return Material.values()
            .filter { BlockValidator.isValidGameBlock(it) }
            .sortedBy { it.name }
    }

    /**
     * Toggles a block's enabled status and saves to config.
     */
    fun toggleBlock(material: Material): Boolean {
        val isNowEnabled = configManager.toggleBlock(material)
        refreshEnabledBlocks()
        return isNowEnabled
    }

    /**
     * Checks if a specific block is currently enabled.
     */
    fun isBlockEnabled(material: Material): Boolean {
        return allValidBlocks.contains(material)
    }

    /**
     * Gets a random enabled block for gameplay.
     */
    fun getRandomBlock(): Material {
        return if (allValidBlocks.isNotEmpty()) {
            allValidBlocks[(Math.random() * allValidBlocks.size).toInt()]
        } else {
            plugin.logger.warning("No valid blocks configured! Defaulting to dirt. An admin needs to fix this!")
            Bukkit.broadcast(Component.text("No valid blocks configured. An admin must fix this with /blockshuffle blocks",
                NamedTextColor.RED))
            return Material.DIRT
        }
    }

    /**
     * Reloads all block data from config file.
     * Useful after manual config edits.
     */
    fun reload() {
        configManager.load()
        refreshEnabledBlocks()
    }
}

