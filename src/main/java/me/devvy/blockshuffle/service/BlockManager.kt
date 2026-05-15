package me.devvy.blockshuffle.service

import me.devvy.blockshuffle.BlockShuffle
import me.devvy.blockshuffle.config.BlockConfig
import me.devvy.blockshuffle.config.calculateDifficultyWeights
import me.devvy.blockshuffle.util.BlockValidator
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import java.io.IOException

/**
 * Centralized service for managing valid game blocks.
 * Coordinates between ConfigManager, BlockValidator, and resource files.
 * Provides methods to load, validate, toggle, and access blocks for gameplay.
 */
class BlockManager(
    private val plugin: BlockShuffle,
) {

    private var allValidBlocks: List<Material> = emptyList()

    init {
        initialize()
    }

    /**
     * Initializes the block manager by loading from config or resources.
     */
    private fun initialize() {
        val blockConfigs = plugin.configManager.getAllBlockConfigs()

        // If config is empty, load from blocks.txt resource file (first run)
        if (blockConfigs.isEmpty()) {
            plugin.logger.info("No blocks found in config, loading from blocks.txt...")
            val blockDifficulties = loadBlocksFromResource()

            // Initialize with enabled blocks from blocks.txt
            plugin.configManager.initializeBlocks(blockDifficulties)

            // Add all other valid game blocks as disabled with default difficulty 1
            val enabledMaterials = blockDifficulties.keys
            val allPossibleBlocks = getAllValidGameBlocks()
            for (material in allPossibleBlocks) {
                if (!enabledMaterials.contains(material)) {
                    plugin.configManager.setBlockConfig(
                        material,
                        me.devvy.blockshuffle.config.BlockConfig(material, 1, false)
                    )
                }
            }

            allValidBlocks = blockDifficulties.keys.toList()
        } else {
            allValidBlocks = blockConfigs.filter { it.value.enabled }.keys.toList()
        }

        plugin.logger.info("Loaded ${allValidBlocks.size} valid blocks")
    }

    /**
     * Loads blocks from the bundled blocks.txt resource file.
     * Returns a map of Material to difficulty.
     */
    private fun loadBlocksFromResource(): Map<Material, Int> {
        val blocks = mutableMapOf<Material, Int>()
        try {
            val inputStream = plugin.javaClass.getResourceAsStream("/blocks.txt")
                ?: return blocks

            inputStream.bufferedReader().use { br ->
                br.forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        val parts = trimmed.split(":")
                        if (parts.size == 2) {
                            try {
                                val material = Material.valueOf(parts[0])
                                val difficulty = parts[1].toInt()
                                blocks[material] = difficulty
                            } catch (e: IllegalArgumentException) {
                                plugin.logger.warning("Unknown material in blocks.txt: ${parts[0]}")
                            } catch (e: NumberFormatException) {
                                plugin.logger.warning("Invalid difficulty in blocks.txt: $trimmed")
                            }
                        } else {
                            plugin.logger.warning("Invalid format in blocks.txt: $trimmed (expected MATERIAL:DIFFICULTY)")
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
     * Gets all currently disabled blocks.
     */
    fun getDisabledBlocks(): List<Material> {
        val allConfigs = plugin.configManager.getAllBlockConfigs()
        return allConfigs.filter { !it.value.enabled }.keys.toList()
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
     * Gets all block configurations.
     */
    fun getAllBlockConfigs(): Map<Material, BlockConfig> {
        return plugin.configManager.getAllBlockConfigs()
    }

    /**
     * Gets the difficulty of a specific block.
     */
    fun getBlockDifficulty(material: Material): Int {
        return plugin.configManager.getBlockDifficulty(material)
    }

    /**
     * Gets blocks filtered by difficulty level.
     */
    fun getBlocksByDifficulty(difficulty: Int): List<Material> {
        return plugin.configManager.getAllBlockConfigs()
            .filter { it.value.difficulty == difficulty && it.value.enabled }
            .keys.toList()
    }

    /**
     * Gets blocks within a difficulty range (inclusive).
     */
    fun getBlocksByDifficultyRange(minDifficulty: Int, maxDifficulty: Int): List<Material> {
        return plugin.configManager.getAllBlockConfigs()
            .filter { it.value.difficulty in minDifficulty..maxDifficulty && it.value.enabled }
            .keys.toList()
    }

    /**
     * Refreshes enabled blocks from config (call after external config changes).
     */
    fun refreshEnabledBlocks() {
        allValidBlocks = plugin.configManager.getEnabledBlocks()
        plugin.logger.info("Refreshed enabled blocks: ${allValidBlocks.size} blocks active")
    }

    /**
     * Toggles a block's enabled status and saves to config.
     */
    fun toggleBlock(material: Material): Boolean {
        val isNowEnabled = plugin.configManager.toggleBlock(material)
        refreshEnabledBlocks()
        return isNowEnabled
    }

    /**
     * Sets the difficulty for a block.
     */
    fun setBlockDifficulty(material: Material, difficulty: Int) {
        plugin.configManager.setBlockDifficulty(material, difficulty)
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
     * Gets a random block from a specific difficulty level.
     */
    fun getRandomBlockByDifficulty(difficulty: Int): Material {
        val blocks = getBlocksByDifficulty(difficulty)
        return if (blocks.isNotEmpty()) {
            blocks[(Math.random() * blocks.size).toInt()]
        } else {
            // Fallback to any enabled block
            getRandomBlock()
        }
    }

    /**
     * Gets a random block appropriate for the current round with progressive difficulty.
     */
    fun getRandomBlockForRound(roundNumber: Int): Material {
        val difficultyWeights = calculateDifficultyWeights(roundNumber)
        val availableBlocks = mutableListOf<Material>()

        // Build weighted list of blocks
        for ((difficulty, weight) in difficultyWeights) {
            val blocks = getBlocksByDifficulty(difficulty)
            // Add each block 'weight' times to create weighted distribution
            repeat(weight) {
                availableBlocks.addAll(blocks)
            }
        }

        return if (availableBlocks.isNotEmpty()) {
            availableBlocks.random()
        } else {
            // Fallback to any enabled block if no blocks match the criteria
            getRandomBlock()
        }
    }

    /**
     * Reloads all block data from config file.
     * Useful after manual config edits.
     */
    fun reload() {
        plugin.configManager.load()
        refreshEnabledBlocks()
    }
}
