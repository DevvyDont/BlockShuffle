package me.devvy.blockshuffle.service.gamemode

import me.devvy.blockshuffle.config.GameConfig
import net.minecraft.util.Tuple
import org.bukkit.Sound
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

    // Keeps track of +/- to display in timers when adding/subtracing time.
    // First element of tuple is the delta, the second element is expiry time (when to stop showing it/reset counter)
    private val playerTimerDeltas = mutableMapOf<UUID, Int>()

    /**
     * Initializes a player's timer with the starting time.
     */
    fun initializePlayer(player: Player) {
        playerTimers[player.uniqueId] = startingTimeSeconds * GameConfig.TASK_FREQUENCY  // Convert to ticks (10 ticks = 1 second)
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
        return getTimeRemainingTicks(player) / GameConfig.TASK_FREQUENCY
    }

    /**
     * Decrements player timer by one tick. Returns true if time expired.
     */
    fun tickTimer(player: Player): Boolean {
        val uuid = player.uniqueId

        if (!playerTimers.containsKey(uuid))
            return false

        // If they have some time delta, give it to their time
        var currentTime = playerTimers[uuid]!!
        val delta = getCurrentTimeDelta(player)
        if (delta != null) {
            if (delta > 0) {
                currentTime += GameConfig.TASK_FREQUENCY
                playerTimerDeltas[uuid] = delta - 1
            }
            else if (delta < 0) {
                currentTime -= GameConfig.TASK_FREQUENCY
                playerTimerDeltas[uuid] = delta + 1
            }
            player.playSound(player.location, Sound.ENTITY_WARDEN_HEARTBEAT, .5f, if (delta > 0 ) 2f else 1.2f)
        }

        // Remove the delta entry if it's depleted
        if (delta == 0)
            playerTimerDeltas.remove(uuid)

        if (currentTime <= 1) {
            playerTimers[uuid] = 0
            playerTimerDeltas.remove(uuid)
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
        if (!playerTimers.containsKey(uuid))
            return

        val newDelta = if (playerTimerDeltas.containsKey(uuid))
            playerTimerDeltas[uuid]!! + timeSeconds
        else
            timeSeconds

        playerTimerDeltas[uuid] = newDelta
    }

    /**
     * Subtracts time from a player's timer (e.g., from damage).
     */
    fun subtractTime(player: Player, timeSeconds: Int) {
        val uuid = player.uniqueId
        if (!playerTimers.containsKey(uuid))
            return

        val newDelta = if (playerTimerDeltas.containsKey(uuid))
            playerTimerDeltas[uuid]!! - timeSeconds
        else
            -timeSeconds

        playerTimerDeltas[uuid] = newDelta
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
        playerTimerDeltas.remove(player.uniqueId)
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
        playerTimerDeltas.clear()
    }

    /**
     * Gets the time bonus added per block find.
     */
    fun getTimePerBlock(): Int {
        return timeAddedPerBlockSeconds
    }

    /**
     * Gets the current time delta for the player. Used for display purposes only.
     * Returns null if no current delta.
     */
    fun getCurrentTimeDelta(player: Player): Int? {
        val uuid = player.uniqueId
        val deltas = playerTimerDeltas[uuid] ?: return null
        return deltas
    }
}

