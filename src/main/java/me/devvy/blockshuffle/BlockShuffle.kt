package me.devvy.blockshuffle

import me.devvy.blockshuffle.command.CommandHandler
import me.devvy.blockshuffle.config.GameConfig
import me.devvy.blockshuffle.service.WorldManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * Main plugin class for Block Shuffle.
 * Manages plugin lifecycle, game initialization, and command routing.
 */
class BlockShuffle : JavaPlugin(), Listener {

    private var gameTask: GameLoop? = null
    @JvmField
    val validMaterials: MutableList<Material> = ArrayList()

    override fun onEnable() {
        // Register command handler
        getCommand("blockshuffle")?.setExecutor(CommandHandler(this))

        // Register events
        server.pluginManager.registerEvents(this, this)

        // Load valid materials from resource file
        loadValidMaterials()
    }

    /**
     * Loads the list of valid blocks from blocks.txt resource file.
     */
    private fun loadValidMaterials() {
        try {
            val inputStream = javaClass.getResourceAsStream("/blocks.txt")
            checkNotNull(inputStream) { "Could not find blocks.txt in resources. Check compilation settings" }

            inputStream.bufferedReader().use { br ->
                br.forEachLine { line ->
                    try {
                        validMaterials.add(Material.valueOf(line.trim()))
                    } catch (e: IllegalArgumentException) {
                        logger.warning("Unknown material: $line")
                    }
                }
            }

            logger.info("Loaded ${validMaterials.size} valid materials")
        } catch (e: IOException) {
            logger.severe("Failed to load blocks.txt: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Starts a new game of Block Shuffle.
     */
    fun start() {
        stop() // Ensure any existing game is stopped first

        val messenger = me.devvy.blockshuffle.service.GameMessenger()
        val worldManager = WorldManager()
        worldManager.initializeAllPlayersForGame(messenger)

        gameTask = GameLoop()
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

    companion object {
        @JvmStatic
        fun getInstance(): BlockShuffle {
            return getPlugin(BlockShuffle::class.java)
        }
    }
}
