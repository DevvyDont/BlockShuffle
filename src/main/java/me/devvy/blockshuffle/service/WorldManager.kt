package me.devvy.blockshuffle.service

import me.devvy.blockshuffle.config.GameConfig
import org.bukkit.Bukkit
import org.bukkit.Difficulty
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player

/**
 * Manages world setup and player initialization.
 */
class WorldManager {

    /**
     * Initializes a player for game start (clears inventory, sets game mode, etc).
     */
    fun initializePlayerForGame(player: Player, messenger: GameMessenger) {
        player.gameMode = GameMode.SURVIVAL
        player.health = 20.0
        player.saturation = 20f
        player.foodLevel = 20
        player.setRespawnLocation(null, true)
        player.inventory.clear()

        messenger.showRoundTitle(player, 0, Bukkit.getOnlinePlayers().size)
        messenger.playSound(player, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.8f)
    }

    /**
     * Initializes all online players for game start.
     */
    fun initializeAllPlayersForGame(messenger: GameMessenger) {
        // Set world properties
        val world = Bukkit.getWorld(GameConfig.DEFAULT_WORLD)
        if (world != null) {
            world.time = 0
            world.difficulty = Difficulty.NORMAL
        }

        // Initialize each player
        for (player in Bukkit.getOnlinePlayers()) {
            initializePlayerForGame(player, messenger)
        }
    }

    /**
     * Resets all players to creative mode after game ends.
     */
    fun resetAllPlayersAfterGame(messenger: GameMessenger) {
        for (player in Bukkit.getOnlinePlayers()) {
            player.gameMode = GameMode.CREATIVE
            messenger.resetPlayerListName(player)
        }
    }

    /**
     * Eliminates a player from the game.
     */
    fun eliminatePlayer(player: Player, assignedMaterial: Material, messenger: GameMessenger) {
        player.damage(0.0) // Trigger damage visual
        messenger.playSound(player, Sound.ENTITY_BAT_DEATH, 0.75f, 0.5f)
        player.gameMode = GameMode.SPECTATOR

        // Drop all items
        for (item in player.inventory.contents) {
            if (item != null) {
                player.world.dropItemNaturally(player.eyeLocation.subtract(0.0, 0.25, 0.0), item)
            }
        }
        player.inventory.clear()
        messenger.setPlayerFailed(player)
    }

    /**
     * Finds a random safe spawn location.
     * Returns null if no valid spawn found within search limit.
     */
    fun findRandomSpawn(): Location? {
        val world = Bukkit.getWorld(GameConfig.DEFAULT_WORLD) ?: return null

        repeat(100) { // Try up to 100 times
            val randX = (Math.random() * GameConfig.SPAWN_SEARCH_RANGE - GameConfig.SPAWN_SEARCH_RANGE / 2).toInt()
            val randZ = (Math.random() * GameConfig.SPAWN_SEARCH_RANGE - GameConfig.SPAWN_SEARCH_RANGE / 2).toInt()
            val randY = world.getHighestBlockYAt(randX, randZ)
            val spawn = Location(world, randX.toDouble(), randY.toDouble(), randZ.toDouble())

            if (spawn.block.type != Material.LAVA && spawn.block.type != Material.WATER) {
                return spawn
            }
        }

        return null
    }

    /**
     * Teleports all players to a spawn location.
     */
    fun teleportAllPlayersToSpawn(spawn: Location) {
        for (player in Bukkit.getOnlinePlayers()) {
            player.teleport(spawn.clone().add(0.0, 2.0, 0.0))
        }
    }
}

