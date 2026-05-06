package me.devvy.blockshuffle.gamemode

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent

/**
 * Base abstraction for different game mode implementations.
 * Each game mode defines its own rules, timer logic, and win/loss conditions.
 * This allows new modes to be added without modifying core GameLoop or existing modes.
 */
interface GameMode {

    /**
     * Initializes the game mode (called when mode starts).
     */
    fun initialize()

    /**
     * Main game loop tick (called every TASK_FREQUENCY ms).
     * Implement all mode-specific logic here.
     */
    fun tick()

    /**
     * Handles a player moving in the world.
     */
    fun onPlayerMove(player: Player, location: Location)

    /**
     * Handles a player joining the game.
     */
    fun onPlayerJoin(event: PlayerJoinEvent)

    /**
     * Handles a player taking damage.
     */
    fun onPlayerDamage(player: Player, damage: Double)

    /**
     * Handles a player dying.
     */
    fun onPlayerDeath(player: Player)

    /**
     * Cleanup when game mode ends.
     */
    fun cleanup()

    /**
     * Checks if the game mode is paused.
     */
    fun isPaused(): Boolean

    /**
     * Sets pause state for the game mode.
     */
    fun setPaused(paused: Boolean)

    /**
     * Checks if the game has ended and the game loop should stop.
     * Return true when the game is over and the scheduler should be cancelled.
     */
    fun isGameOver(): Boolean
}

