package me.devvy.blockshuffle.service.gamemode

import org.bukkit.entity.Player
import java.util.*

/**
 * Manages individual countdown timers for each player in Blitz mode.
 * Each player has their own time remaining, independent of others.
 */
class PlayerTimerManager(
    private val startingTimeSeconds: Int,
    private val timeAddedPerBlockSeconds: Int
) {

    private val playerTimers: MutableMap<UUID, Int> = HashMap()

    /**
     * Initializes a player's timer with the starting time.
     */
    fun initializePlayer(player: Player) {
        playerTimers[player.uniqueId] = startingTimeSeconds * 10  // Convert to ticks (10 ticks = 1 second)
    }

    /**
     * Gets the remaining time for a player in ticks (10 ticks = 1 second).
     */
    fun getTimeRemainingTicks(player: Player): Int {
        return playerTimers[player.uniqueId] ?: 0
    }

    /**
     * Gets the remaining time for a player in seconds.
     */
    fun getTimeRemainingSeconds(player: Player): Int {
        return getTimeRemainingTicks(player) / 10
    }

    /**
     * Decrements player timer by one tick. Returns true if time expired.
     */
    fun tickTimer(player: Player): Boolean {
        val uuid = player.uniqueId
        if (!playerTimers.containsKey(uuid)) return false

        val currentTime = playerTimers[uuid]!!
        if (currentTime <= 1) {
            playerTimers[uuid] = 0
            return true
        }

        playerTimers[uuid] = currentTime - 1
        return false
    }

    /**
     * Adds time to a player's timer (e.g., after finding a block).
     */
    fun addTime(player: Player, timeSeconds: Int) {
        val uuid = player.uniqueId
        if (!playerTimers.containsKey(uuid)) return

        playerTimers[uuid] = playerTimers[uuid]!! + (timeSeconds * 10)
    }

    /**
     * Subtracts time from a player's timer (e.g., from damage).
     */
    fun subtractTime(player: Player, timeSeconds: Int) {
        val uuid = player.uniqueId
        if (!playerTimers.containsKey(uuid)) return

        val newTime = playerTimers[uuid]!! - (timeSeconds * 10)
        playerTimers[uuid] = maxOf(0, newTime)
    }

    /**
     * Checks if a player's time has expired.
     */
    fun isTimeExpired(player: Player): Boolean {
        return getTimeRemainingTicks(player) <= 0
    }

    /**
     * Removes a player from the timer system (e.g., when eliminated).
     */
    fun removePlayer(player: Player) {
        playerTimers.remove(player.uniqueId)
    }

    /**
     * Gets all players currently being tracked.
     */
    fun getTrackedPlayers(): Set<UUID> {
        return playerTimers.keys
    }

    /**
     * Clears all timers.
     */
    fun clear() {
        playerTimers.clear()
    }

    /**
     * Gets the time bonus added per block find.
     */
    fun getTimePerBlock(): Int {
        return timeAddedPerBlockSeconds
    }
}

