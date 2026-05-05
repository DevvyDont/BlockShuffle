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
     * Assigns a random block to a player.
     */
    fun assignRandomBlock(player: Player): Material? {
        val randomBlock = blockManager.getRandomBlock() ?: return null
        assignedMaterialMap[player.uniqueId] = randomBlock
        return randomBlock
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
     * Gets a player's current score.
     */
    fun getScore(player: Player): Int {
        return scoreTracker[player.uniqueId] ?: 0
    }

    /**
     * Gets all scores as a map.
     */
    fun getAllScores(): Map<UUID, Int> {
        return scoreTracker.toMap()
    }

    /**
     * Clears all assigned blocks for the next round.
     */
    fun clearAllAssignedBlocks() {
        assignedMaterialMap.clear()
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

    /**
     * Resets entire game state (for a new game).
     */
    fun resetAll() {
        assignedMaterialMap.clear()
        failedPlayers.clear()
        scoreTracker.clear()
    }

    /**
     * Gets number of players still active.
     */
    fun getActivePlayerCount(): Int {
        return Bukkit.getOnlinePlayers().size - failedPlayers.size
    }
}

