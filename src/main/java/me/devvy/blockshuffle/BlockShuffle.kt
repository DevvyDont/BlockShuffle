package me.devvy.blockshuffle

import me.devvy.blockshuffle.command.CommandHandler
import me.devvy.blockshuffle.config.ConfigManager
import me.devvy.blockshuffle.config.GameConfig
import me.devvy.blockshuffle.gamemode.BlitzMode
import me.devvy.blockshuffle.gamemode.ClassicMode
import me.devvy.blockshuffle.service.BlockManager
import me.devvy.blockshuffle.service.GameMessenger
import me.devvy.blockshuffle.service.WorldManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

/**
 * Main plugin class for Block Shuffle.
 * Manages plugin lifecycle, game initialization, and command routing.
 */
class BlockShuffle : JavaPlugin(), Listener {

    private var gameTask: GameLoop? = null
    private lateinit var configManager: ConfigManager
    private lateinit var blockManager: BlockManager

    override fun onEnable() {
        // Initialize managers
        configManager = ConfigManager(this)
        blockManager = BlockManager(this, configManager)

        // Register command handler
        getCommand("blockshuffle")?.setExecutor(CommandHandler(this, blockManager))

        // Register events
        server.pluginManager.registerEvents(this, this)
    }

    /**
     * Starts a new game using Classic mode (default).
     * In the future, this can be extended to support other game modes.
     */
    fun start() {
        stop() // Ensure any existing game is stopped first

        val messenger = GameMessenger(blockManager)
        val worldManager = WorldManager()
        worldManager.initializeAllPlayersForGame(messenger)

        // Create and start ClassicMode
        val gameMode = BlitzMode(this, blockManager)
        gameTask = GameLoop(gameMode)
        gameTask!!.runTaskTimer(this, 0, GameConfig.TASK_TICK_PERIOD)
        server.pluginManager.registerEvents(gameTask!!, this)
    }

    /**
     * Stops the current game and cleans up.
     */
    fun stop() {
        if (gameTask == null) return

        HandlerList.unregisterAll(gameTask!!)
        gameTask!!.stop()
        gameTask = null

        for (player in Bukkit.getOnlinePlayers()) {
            player.gameMode = GameMode.CREATIVE
            player.playerListName(Component.text(player.name, NamedTextColor.AQUA))
        }
    }

    /**
     * Checks if a game is currently running.
     */
    fun isGameRunning(): Boolean {
        return gameTask != null
    }

    /**
     * Gets the current game task (if running).
     */
    fun getGameTask(): GameLoop? {
        return gameTask
    }

    /**
     * Gets the block manager.
     */
    fun getBlockManager(): BlockManager {
        return blockManager
    }

    companion object {
        @JvmStatic
        fun getInstance(): BlockShuffle {
            return getPlugin(BlockShuffle::class.java)
        }
    }
}
