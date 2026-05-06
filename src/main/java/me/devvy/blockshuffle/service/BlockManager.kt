package me.devvy.blockshuffle.service

import me.devvy.blockshuffle.config.BlockConfig
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
        val blockConfigs = configManager.getAllBlockConfigs()

        // If config is empty, load from blocks.txt resource file (first run)
        if (blockConfigs.isEmpty()) {
            plugin.logger.info("No blocks found in config, loading from blocks.txt...")
            val blockDifficulties = loadBlocksFromResource()

            // Initialize with enabled blocks from blocks.txt
            configManager.initializeBlocks(blockDifficulties)

            // Add all other valid game blocks as disabled with default difficulty 1
            val enabledMaterials = blockDifficulties.keys
            val allPossibleBlocks = getAllValidGameBlocks()
            for (material in allPossibleBlocks) {
                if (!enabledMaterials.contains(material)) {
                    configManager.setBlockConfig(
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
        val allConfigs = configManager.getAllBlockConfigs()
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
        return configManager.getAllBlockConfigs()
    }

    /**
     * Gets the difficulty of a specific block.
     */
    fun getBlockDifficulty(material: Material): Int {
        return configManager.getBlockDifficulty(material)
    }

    /**
     * Gets blocks filtered by difficulty level.
     */
    fun getBlocksByDifficulty(difficulty: Int): List<Material> {
        return configManager.getAllBlockConfigs()
            .filter { it.value.difficulty == difficulty && it.value.enabled }
            .keys.toList()
    }

    /**
     * Gets blocks within a difficulty range (inclusive).
     */
    fun getBlocksByDifficultyRange(minDifficulty: Int, maxDifficulty: Int): List<Material> {
        return configManager.getAllBlockConfigs()
            .filter { it.value.difficulty in minDifficulty..maxDifficulty && it.value.enabled }
            .keys.toList()
    }

    /**
     * Refreshes enabled blocks from config (call after external config changes).
     */
    fun refreshEnabledBlocks() {
        allValidBlocks = configManager.getEnabledBlocks()
        plugin.logger.info("Refreshed enabled blocks: ${allValidBlocks.size} blocks active")
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
     * Sets the difficulty for a block.
     */
    fun setBlockDifficulty(material: Material, difficulty: Int) {
        configManager.setBlockDifficulty(material, difficulty)
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
     * Calculates difficulty weights for a given round number.
     * Returns a map of difficulty level to weight (higher weight = more likely).
     */
    private fun calculateDifficultyWeights(roundNumber: Int): Map<Int, Int> {
        val weights = mutableMapOf<Int, Int>()

        when {
            roundNumber <= 1 -> {
                // Round 1: 100% difficulty 1
                weights[1] = 100
            }
            roundNumber == 2 -> {
                // Round 2: 80% diff 1, 20% diff 2
                weights[1] = 80
                weights[2] = 20
            }
            roundNumber == 3 -> {
                // Round 3: 60% diff 1, 35% diff 2, 5% diff 3
                weights[1] = 60
                weights[2] = 35
                weights[3] = 5
            }
            roundNumber == 4 -> {
                // Round 4: 40% diff 1, 40% diff 2, 20% diff 3
                weights[1] = 40
                weights[2] = 40
                weights[3] = 20
            }
            roundNumber == 5 -> {
                // Round 5: 20% diff 1, 40% diff 2, 35% diff 3, 5% diff 4
                weights[1] = 20
                weights[2] = 40
                weights[3] = 35
                weights[4] = 5
            }
            roundNumber <= 10 -> {
                // Rounds 6-10: Gradual shift toward higher difficulties
                val progress = (roundNumber - 5).toDouble() / 5.0 // 0.0 to 1.0
                weights[1] = (20 * (1 - progress)).toInt()
                weights[2] = (30 * (1 - progress * 0.5)).toInt()
                weights[3] = (30 + (progress * 20)).toInt()
                weights[4] = (10 + (progress * 30)).toInt()
                weights[5] = (progress * 10).toInt()
            }
            roundNumber <= 15 -> {
                // Rounds 11-15: Continue shifting
                val progress = (roundNumber - 10).toDouble() / 5.0 // 0.0 to 1.0
                weights[1] = 0
                weights[2] = (15 * (1 - progress)).toInt()
                weights[3] = (40 - (progress * 10)).toInt()
                weights[4] = (35 + (progress * 15)).toInt()
                weights[5] = (10 + (progress * 40)).toInt()
            }
            roundNumber <= 20 -> {
                // Rounds 16-20: Heavy on 4s and 5s
                val progress = (roundNumber - 15).toDouble() / 5.0 // 0.0 to 1.0
                weights[2] = (5 * (1 - progress)).toInt()
                weights[3] = (20 * (1 - progress)).toInt()
                weights[4] = (40 - (progress * 10)).toInt()
                weights[5] = (35 + (progress * 15)).toInt()
            }
            else -> {
                // Round 21+: Mostly 5s with some 4s
                weights[3] = 5
                weights[4] = 20
                weights[5] = 75
            }
        }

        // Remove zero weights
        return weights.filter { it.value > 0 }
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
