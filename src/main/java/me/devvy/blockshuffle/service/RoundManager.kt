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
     * Difficulty ranges from 1-10, with round 30 being "endgame" (mostly 9-10).
     */
    private fun calculateDifficultyWeights(roundNumber: Int): Map<Int, Int> {
        val weights = mutableMapOf<Int, Int>()

        when {
            roundNumber <= 1 -> {
                // Round 1: 100% difficulty 1
                weights[1] = 100
            }
            roundNumber == 2 -> {
                // Round 2: 85% diff 1, 15% diff 2
                weights[1] = 85
                weights[2] = 15
            }
            roundNumber == 3 -> {
                // Round 3: 70% diff 1, 25% diff 2, 5% diff 3
                weights[1] = 70
                weights[2] = 25
                weights[3] = 5
            }
            roundNumber == 4 -> {
                // Round 4: 55% diff 1, 30% diff 2, 15% diff 3
                weights[1] = 55
                weights[2] = 30
                weights[3] = 15
            }
            roundNumber == 5 -> {
                // Round 5: 40% diff 1, 30% diff 2, 20% diff 3, 10% diff 4
                weights[1] = 40
                weights[2] = 30
                weights[3] = 20
                weights[4] = 10
            }
            roundNumber <= 10 -> {
                // Rounds 6-10: Gradual introduction of difficulties 5-7
                val progress = (roundNumber - 5).toDouble() / 5.0 // 0.0 to 1.0
                weights[1] = (40 * (1 - progress)).toInt()
                weights[2] = (25 * (1 - progress * 0.7)).toInt()
                weights[3] = (20 + (progress * 10)).toInt()
                weights[4] = (10 + (progress * 15)).toInt()
                weights[5] = (progress * 20).toInt()
                weights[6] = (progress * 10).toInt()
                weights[7] = (progress * 5).toInt()
            }
            roundNumber <= 15 -> {
                // Rounds 11-15: Shift toward mid-high difficulties (4-8)
                val progress = (roundNumber - 10).toDouble() / 5.0 // 0.0 to 1.0
                weights[1] = 0
                weights[2] = (10 * (1 - progress)).toInt()
                weights[3] = (25 - (progress * 5)).toInt()
                weights[4] = (25 + (progress * 5)).toInt()
                weights[5] = (20 + (progress * 10)).toInt()
                weights[6] = (10 + (progress * 15)).toInt()
                weights[7] = (5 + (progress * 20)).toInt()
                weights[8] = (progress * 15).toInt()
            }
            roundNumber <= 20 -> {
                // Rounds 16-20: Heavy on 6-9, introduce difficulty 10
                val progress = (roundNumber - 15).toDouble() / 5.0 // 0.0 to 1.0
                weights[2] = (5 * (1 - progress)).toInt()
                weights[3] = (10 * (1 - progress)).toInt()
                weights[4] = (20 - (progress * 5)).toInt()
                weights[5] = (25 - (progress * 5)).toInt()
                weights[6] = (20 + (progress * 5)).toInt()
                weights[7] = (15 + (progress * 10)).toInt()
                weights[8] = (10 + (progress * 15)).toInt()
                weights[9] = (progress * 20).toInt()
                weights[10] = (progress * 10).toInt()
            }
            roundNumber <= 25 -> {
                // Rounds 21-25: Dominated by 7-10, difficulty 10 rising
                val progress = (roundNumber - 20).toDouble() / 5.0 // 0.0 to 1.0
                weights[3] = (5 * (1 - progress)).toInt()
                weights[4] = (10 * (1 - progress)).toInt()
                weights[5] = (15 * (1 - progress)).toInt()
                weights[6] = (20 - (progress * 5)).toInt()
                weights[7] = (20 + (progress * 5)).toInt()
                weights[8] = (15 + (progress * 10)).toInt()
                weights[9] = (10 + (progress * 15)).toInt()
                weights[10] = (5 + (progress * 30)).toInt()
            }
            roundNumber <= 30 -> {
                // Rounds 26-30: Approaching endgame, difficulty 10 dominant
                val progress = (roundNumber - 25).toDouble() / 5.0 // 0.0 to 1.0
                weights[4] = (5 * (1 - progress)).toInt()
                weights[5] = (10 * (1 - progress)).toInt()
                weights[6] = (10 * (1 - progress)).toInt()
                weights[7] = (15 - (progress * 5)).toInt()
                weights[8] = (20 - (progress * 5)).toInt()
                weights[9] = (15 + (progress * 10)).toInt()
                weights[10] = (25 + (progress * 35)).toInt()
            }
            else -> {
                // Round 31+: Endgame - mostly difficulty 10 with some 9s
                weights[7] = 5
                weights[8] = 10
                weights[9] = 15
                weights[10] = 70
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
