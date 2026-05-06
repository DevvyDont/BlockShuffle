package me.devvy.blockshuffle.service.gamemode

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.plugin.java.JavaPlugin
import kotlin.math.ceil

/**
 * Listens to damage events and converts them to time loss in Blitz mode.
 * Health is the only representation of time remaining - taking damage loses time.
 */
class PlayerHealthManager(
    private val plugin: JavaPlugin,
    private val timerManager: PlayerTimerManager,
    private val damageToTimeRatio: Double = 1.0  // Default: 1 damage = 1 second loss
) : Listener {

    fun setPlayerHealth(player: Player, fullHealthTime: Int) {

        val maxHp = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
        val secsLeft = timerManager.getTimeRemainingSeconds(player)
        var newHp = secsLeft.toDouble() / fullHealthTime.toDouble() * maxHp
        val overflow = (newHp - maxHp).coerceAtLeast(0.0)

        // Don't actually kill the player at this point
        if (newHp.toInt() == 0)
            newHp = .5

        // Don't go over their max
        if (newHp > maxHp)
            newHp = maxHp

        if (player.health.toInt() != newHp.toInt())
            player.health = newHp

        // Give them absorption if they are flying over the cap
        player.getAttribute(Attribute.MAX_ABSORPTION)?.baseValue = overflow
        player.absorptionAmount = overflow
    }

    /**
     * Handles damage by converting it to time loss.
     * Damage is measured in half-hearts; 1 damage = 0.5 hearts.
     * We convert this to seconds lost.
     */
    @EventHandler
    fun onEntityDamage(event: EntityDamageEvent) {
        if (event.entity !is Player)
            return
        val player = event.entity as Player

        // Only apply to players currently in a Blitz game
        if (!timerManager.getTrackedPlayers().contains(player.uniqueId))
            return
        val timeLostSeconds = ceil(event.damage * damageToTimeRatio).toInt()
        event.damage = 0.01
        if (player.noDamageTicks > 10)
            return

        timerManager.subtractTime(player, timeLostSeconds)
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

    @EventHandler
    fun onRegenerate(event: EntityRegainHealthEvent) {

        if (event.entity !is Player)
            return
        val player = event.entity as Player
        // Only apply to players currently in a Blitz game
        if (!timerManager.getTrackedPlayers().contains(player.uniqueId))
            return

        // Cancels all health regen if tracked
        event.isCancelled = true
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
        HandlerList.unregisterAll(this)
    }
}

