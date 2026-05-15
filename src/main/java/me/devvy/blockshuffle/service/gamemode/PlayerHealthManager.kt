package me.devvy.blockshuffle.service.gamemode

import me.devvy.blockshuffle.BlockShuffle
import me.devvy.blockshuffle.gamemode.BlitzMode
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Sound
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
    private val mode: BlitzMode,
    private val damageToTimeRatio: Double = 1.0  // Default: 1 damage = 1 second loss
) : Listener {

    fun setPlayerHealth(player: Player, fullHealthTime: Int) {

        val maxHp = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
        val secsLeft = mode.timerManager.getTimeRemainingSeconds(player)
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
        if (!mode.timerManager.getTrackedPlayers().contains(player.uniqueId))
            return
        var timeLostSeconds = ceil(event.damage * damageToTimeRatio).toInt()
        if (event.damageSource.damageType != BlitzMode.IGNORED_MULTIPLIER_DAMAGE_SOURCE.damageType)
            timeLostSeconds = (timeLostSeconds * mode.damageMultiplier).toInt()
        event.damage = 0.01
        if (player.noDamageTicks > 10)
            return

        mode.timerManager.subtractTime(player, timeLostSeconds)

        // If another player caused this then give them the time
        if (event.damageSource.causingEntity is Player) {
            val damager = event.damageSource.causingEntity as Player
            if (damager.uniqueId != player.uniqueId) {
                mode.timerManager.addTime(damager, timeLostSeconds)
                damager.playSound(damager.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f)
            }
        }
    }

    /**
     * Handles player death (in case they somehow die to damage that exceeds time loss).
     * This ensures we properly track death eliminations.
     */
    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        if (!mode.timerManager.getTrackedPlayers().contains(event.entity.uniqueId)) return

        // Remove from tracking
        mode.timerManager.removePlayer(event.entity)
    }

    @EventHandler
    fun onRegenerate(event: EntityRegainHealthEvent) {

        if (event.entity !is Player)
            return
        val player = event.entity as Player
        // Only apply to players currently in a Blitz game
        if (!mode.timerManager.getTrackedPlayers().contains(player.uniqueId))
            return

        // Cancels all health regen if tracked
        event.isCancelled = true
    }

    /**
     * Registers this listener with the plugin.
     */
    fun register() {
        BlockShuffle.getInstance().server.pluginManager.registerEvents(this, BlockShuffle.getInstance())
    }

    /**
     * Unregisters this listener.
     */
    fun unregister() {
        // Will be called by BlitzMode cleanup
        HandlerList.unregisterAll(this)
    }
}

