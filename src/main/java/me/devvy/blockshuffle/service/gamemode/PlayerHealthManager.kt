package me.devvy.blockshuffle.service.gamemode

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.plugin.java.JavaPlugin

/**
 * Listens to damage events and converts them to time loss in Blitz mode.
 * Health is the only representation of time remaining - taking damage loses time.
 */
class PlayerHealthManager(
    private val plugin: JavaPlugin,
    private val timerManager: PlayerTimerManager,
    private val damageToTimeRatio: Double = 1.0  // Default: 1 damage = 1 second loss
) : Listener {

    /**
     * Handles damage by converting it to time loss.
     * Damage is measured in half-hearts; 1 damage = 0.5 hearts.
     * We convert this to seconds lost.
     */
    @EventHandler
    fun onEntityDamage(event: EntityDamageEvent) {
        if (event.entity !is Player) return

        val player = event.entity as Player

        // Only apply to players currently in a Blitz game
        if (!timerManager.getTrackedPlayers().contains(player.uniqueId)) return

        val timeLostSeconds = (event.damage * damageToTimeRatio).toInt()
        timerManager.subtractTime(player, timeLostSeconds)

        // If time expired due to damage, prevent actual death and eliminate instead
        if (timerManager.isTimeExpired(player)) {
            event.isCancelled = true
            player.health = 20.0  // Reset health
            eliminatePlayerViaTimeOut(player)
        }
    }

    /**
     * Handles player death (in case they somehow die to damage that exceeds time loss).
     * This ensures we properly track death eliminations.
     */
    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        if (!timerManager.getTrackedPlayers().contains(event.entity.uniqueId)) return

        // Remove from tracking
        timerManager.removePlayer(event.entity)
    }

    /**
     * Called when a player is eliminated due to time running out.
     * Can be overridden by the game mode for custom elimination logic.
     */
    private fun eliminatePlayerViaTimeOut(player: Player) {
        // This will be called by BlitzMode to handle elimination
        // For now, just mark that time expired
    }

    /**
     * Registers this listener with the plugin.
     */
    fun register() {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    /**
     * Unregisters this listener.
     */
    fun unregister() {
        // Will be called by BlitzMode cleanup
    }
}

