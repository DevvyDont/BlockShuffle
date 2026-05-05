package me.devvy.blockshuffle.service

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import java.util.*

/**
 * Manages all player-related game logic including scoring, eliminations, and block assignments.
 */
class RoundManager(private val blockManager: BlockManager) {

    private val assignedMaterialMap: MutableMap<UUID, Material> = HashMap()
    private val failedPlayers: MutableSet<UUID> = HashSet()
    private val scoreTracker: MutableMap<UUID, Int> = HashMap()

    fun initializePlayer(player: Player) {
        scoreTracker[player.uniqueId] = 0
    }

    fun initializeAllPlayers() {
        for (player in Bukkit.getOnlinePlayers()) {
            scoreTracker[player.uniqueId] = 0
        }
    }

    /**
     * Assigns a random block to a player based on current round difficulty.
     */
    fun assignRandomBlock(player: Player, roundNumber: Int): Material? {
        val randomBlock = getRandomBlockForRound(roundNumber) ?: return null
        assignedMaterialMap[player.uniqueId] = randomBlock
        return randomBlock
    }

    /**
     * Gets a random block appropriate for the current round's difficulty curve.
     */
    private fun getRandomBlockForRound(roundNumber: Int): Material? {
        val difficultyWeights = calculateDifficultyWeights(roundNumber)
        val availableBlocks = mutableListOf<Material>()

        // Build weighted list of blocks
        for ((difficulty, weight) in difficultyWeights) {
            val blocks = blockManager.getBlocksByDifficulty(difficulty)
            // Add each block 'weight' times to create weighted distribution
            repeat(weight) {
                availableBlocks.addAll(blocks)
            }
        }

        return if (availableBlocks.isNotEmpty()) {
            availableBlocks.random()
        } else {
            // Fallback to any enabled block if no blocks match the criteria
            blockManager.getRandomBlock()
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
     * Gets the currently assigned block for a player.
     */
    fun getAssignedBlock(player: Player): Material? {
        return assignedMaterialMap[player.uniqueId]
    }

    /**
     * Checks if a player has an assigned block.
     */
    fun hasAssignedBlock(player: Player): Boolean {
        return assignedMaterialMap.containsKey(player.uniqueId)
    }

    /**
     * Removes a player's assigned block (marks them as safe).
     */
    fun clearAssignedBlock(player: Player) {
        assignedMaterialMap.remove(player.uniqueId)
    }

    /**
     * Marks a player as failed/eliminated.
     */
    fun markPlayerFailed(player: Player) {
        failedPlayers.add(player.uniqueId)
        assignedMaterialMap.remove(player.uniqueId)
    }

    /**
     * Checks if a player has failed.
     */
    fun hasPlayerFailed(player: Player): Boolean {
        return failedPlayers.contains(player.uniqueId)
    }

    /**
     * Gets all players still in the game (not failed).
     */
    fun getPlayersStillInGame(): List<Player> {
        return Bukkit.getOnlinePlayers().filter { !hasPlayerFailed(it) }
    }

    /**
     * Increases a player's score by 1 (successful block placement).
     */
    fun increaseScore(player: Player) {
        scoreTracker[player.uniqueId] = (scoreTracker[player.uniqueId] ?: 0) + 1
    }

    /**
     * Gets all scores as a map.
     */
    fun getAllScores(): Map<UUID, Int> {
        return scoreTracker.toMap()
    }

    /**
     * Checks if all active players have completed their assignments.
     */
    fun areAllPlayersCompleted(): Boolean {
        return assignedMaterialMap.isEmpty()
    }

    /**
     * Resets the round state (clears assignments but keeps scores and failures).
     */
    fun resetRound() {
        assignedMaterialMap.clear()
    }
}
