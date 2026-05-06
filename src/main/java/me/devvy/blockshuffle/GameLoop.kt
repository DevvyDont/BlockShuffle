package me.devvy.blockshuffle

import me.devvy.blockshuffle.BlockShuffle.Companion.getInstance
import me.devvy.blockshuffle.gamemode.GameMode
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.scheduler.BukkitRunnable

/**
 * Main game loop that delegates all game logic to the selected game mode.
 * Acts as a thin wrapper around the GameMode abstraction, handling scheduling and events.
 *
 * This design allows different game modes (Classic, Blitz, etc.) to implement their own rules
 * without modifying the core GameLoop.
 */
class GameLoop(private val gameMode: GameMode) : BukkitRunnable(), Listener {

    init {
        gameMode.initialize()
    }

    fun isPaused(): Boolean {
        return gameMode.isPaused()
    }

    fun setPaused(paused: Boolean) {
        gameMode.setPaused(paused)
    }

    /**
     * Stops the game loop and cleans up the game mode.
     */
    fun stop() {
        setPaused(true)
        this.cancel()
        gameMode.cleanup()

        // Schedule plugin stop after a delay
        object : BukkitRunnable() {
            override fun run() {
                getInstance().stop()
            }
        }.runTaskLater(getInstance(), (20 * 5).toLong())
    }

    /**
     * Main game loop tick - delegates to the game mode.
     */
    override fun run() {
        gameMode.tick()

        // Check if the game mode has ended and stop the game loop if so
        if (gameMode.isGameOver()) {
            stop()
        }
    }

    /**
     * Handles player movement - delegates to the game mode.
     */
    @EventHandler
    fun onPlayerMoveEvent(event: PlayerMoveEvent) {
        gameMode.onPlayerMove(event.player, event.to)
    }

    /**
     * Handles player join - delegates to the game mode.
     */
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        gameMode.onPlayerJoin(event)
    }

    /**
     * Handles player damage - delegates to the game mode.
     */
    @EventHandler
    fun onEntityDamage(event: EntityDamageEvent) {
        if (event.entity !is org.bukkit.entity.Player) return
        gameMode.onPlayerDamage(event.entity as org.bukkit.entity.Player, event.damage)
    }

    /**
     * Handles player death - delegates to the game mode.
     */
    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        gameMode.onPlayerDeath(event.entity)
    }

}
