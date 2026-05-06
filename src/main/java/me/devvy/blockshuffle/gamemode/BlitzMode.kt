package me.devvy.blockshuffle.gamemode

import me.devvy.blockshuffle.BlockShuffle
import me.devvy.blockshuffle.config.GameConfig
import me.devvy.blockshuffle.service.BlockManager
import me.devvy.blockshuffle.service.GameMessenger
import me.devvy.blockshuffle.service.WorldManager
import me.devvy.blockshuffle.service.gamemode.PlayerHealthManager
import me.devvy.blockshuffle.service.gamemode.PlayerTimerManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

/**
 * Blitz Mode: Fast-paced individual timers with damage-based time loss.
 * Each player has their own countdown timer. Finding blocks adds time, damage removes time.
 * Taking lethal damage or running out of time results in elimination.
 */
class BlitzMode(
    private val plugin: JavaPlugin,
    private val blockManager: BlockManager
) : GameMode, Listener {

    private val timerManager = PlayerTimerManager(
        GameConfig.BLITZ_STARTING_TIME_SECONDS,
        GameConfig.BLITZ_TIME_PER_BLOCK_SECONDS
    )
    private val healthManager = PlayerHealthManager(
        plugin,
        timerManager,
        GameConfig.BLITZ_DAMAGE_TO_TIME_RATIO
    )
    private val messenger = GameMessenger(blockManager)
    private val worldManager = WorldManager()

    private val assignedBlocks: MutableMap<UUID, Pair<Material, Long>> = HashMap()  // UUID -> (Material, assignTime)
    private val eliminatedPlayers: MutableSet<UUID> = HashSet()
    private val playerScores: MutableMap<UUID, Int> = HashMap()

    private var isPaused = false
    private var gameStarted = false
    private var isGameOver = false

    override fun initialize() {
        // Register damage/death listener
        plugin.server.pluginManager.registerEvents(this, plugin)
        healthManager.register()

        for (player in Bukkit.getOnlinePlayers()) {
            timerManager.initializePlayer(player)
            playerScores[player.uniqueId] = 0
            // Assign initial block to each player
            assignBlockToPlayer(player)
        }

        gameStarted = true
    }

    override fun tick() {
        if (!gameStarted) return

        displayPlayerTimers()

        if (isPaused) return

        // Tick all player timers, eliminate those who expire
        for (uuid in timerManager.getTrackedPlayers().toList()) {
            val player = Bukkit.getPlayer(uuid) ?: continue
            healthManager.setPlayerHealth(player, GameConfig.BLITZ_STARTING_TIME_SECONDS)
            if (timerManager.tickTimer(player)) {
                eliminatePlayer(player, "TIME_EXPIRED")
            }
        }

        // Check win condition: only one player left
        val activePlayers = Bukkit.getOnlinePlayers().filter { !eliminatedPlayers.contains(it.uniqueId) }
        if (activePlayers.isEmpty()) {
            handleGameOverDraw()
        } else if (activePlayers.size == 1) {
            handleGameOverWinner(activePlayers[0])
        }
    }

    override fun onPlayerMove(player: Player, location: Location) {
        if (isPaused || !gameStarted) return
        if (eliminatedPlayers.contains(player.uniqueId)) return

        val uuid = player.uniqueId
        val assignedData = assignedBlocks[uuid] ?: return
        val (assignedBlock, _) = assignedData

        val blockBelow = location.block.getRelative(BlockFace.DOWN).type

        if (blockBelow == assignedBlock) {
            // Block found!
            messenger.playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.0f)
            messenger.broadcastBlockFound(player)
            messenger.showBlockFoundTitle(player)

            // Add time bonus
            val bonus = timerManager.getTimePerBlock() + blockManager.getBlockDifficulty(assignedBlock) * GameConfig.BLITZ_TIME_BONUS_PER_DIFFICULTY
            timerManager.addTime(player, bonus)

            // Increase score
            playerScores[uuid] = (playerScores[uuid] ?: 0) + 1

            // Create visual/audio feedback
            messenger.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f)

            // Broadcast time bonus
            for (other in Bukkit.getOnlinePlayers()) {
                other.sendMessage(
                    Component.text()
                        .append(Component.text(player.name, NamedTextColor.AQUA))
                        .append(Component.text(" found a block! +$bonus seconds", NamedTextColor.GREEN))
                        .build()
                )
            }

            // Assign new block immediately
            assignBlockToPlayer(player)
        }
    }

    override fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!gameStarted) return

        val player = event.player

        // Skip if already in game
        if (assignedBlocks.containsKey(player.uniqueId)) return

        // Initialize timer and score
        timerManager.initializePlayer(player)
        playerScores[player.uniqueId] = 0

        // Assign a block
        assignBlockToPlayer(player)
    }

    override fun onPlayerDamage(player: Player, damage: Double) {
        // Damage handling is delegated to PlayerHealthManager
    }

    override fun onPlayerDeath(player: Player) {
        // Death handling is delegated to PlayerHealthManager
        eliminatePlayer(player, "DEATH")
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        onPlayerMove(event.player, event.to)
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        if (eliminatedPlayers.contains(event.entity.uniqueId)) return
        // Will be handled by eliminatePlayer
    }

    override fun cleanup() {
        isPaused = true
        gameStarted = false
        isGameOver = true
        HandlerList.unregisterAll(this)
        healthManager.unregister()
        worldManager.resetAllPlayersAfterGame(messenger)
        timerManager.clear()
    }

    override fun isPaused(): Boolean {
        return isPaused
    }

    override fun setPaused(paused: Boolean) {
        isPaused = paused
    }

    override fun isGameOver(): Boolean {
        return isGameOver
    }

    /**
     * Displays each player's remaining time in the action bar.
     */
    private fun displayPlayerTimers() {
        val state = if (isPaused) "PAUSED" else "INGAME"
        for (player in Bukkit.getOnlinePlayers()) {
            if (eliminatedPlayers.contains(player.uniqueId))
                continue

            val timeRemaining = timerManager.getTimeRemainingTicks(player)
            val blockMaterial = assignedBlocks[player.uniqueId]?.first

            val score = playerScores[player.uniqueId] ?: 0
            messenger.sendDetailedActionBar(player, state, timeRemaining, score, blockMaterial, timeModifier = timerManager.getCurrentTimeDelta(player))
            // Play warning sounds
            messenger.playTimerWarningSfx(player, timerManager.getTimeRemainingTicks(player))
        }
    }

    /**
     * Assigns a random block to a player (with difficulty progression based on time).
     */
    private fun assignBlockToPlayer(player: Player) {
        // Use round-based difficulty progression (estimate round based on score)
        val round = (playerScores[player.uniqueId] ?: 0) / 2  // Every 2 blocks found increases difficulty
        val material = blockManager.getRandomBlockForRound(round)

        assignedBlocks[player.uniqueId] = Pair(material, System.currentTimeMillis())

        messenger.broadcastBlockAssignment(player, material)
        messenger.setPlayerInGame(player, material)
    }

    /**
     * Eliminates a player from the game.
     */
    private fun eliminatePlayer(player: Player, reason: String) {
        if (eliminatedPlayers.contains(player.uniqueId)) return

        eliminatedPlayers.add(player.uniqueId)
        val blockMaterial = assignedBlocks.remove(player.uniqueId)?.first ?: Material.DIRT
        timerManager.removePlayer(player)

        val reasonText = when (reason) {
            "TIME_EXPIRED" -> "Time expired"
            "DEATH" -> "Died"
            "TIME_OUT_FROM_DAMAGE" -> "Took too much damage"
            else -> reason
        }

        for (other in Bukkit.getOnlinePlayers()) {
            other.sendMessage(
                Component.text()
                    .append(Component.text(player.name, NamedTextColor.RED))
                    .append(Component.text(" eliminated! ($reasonText)", NamedTextColor.GRAY))
                    .build()
            )
        }

        messenger.playSound(player, Sound.ENTITY_GENERIC_DEATH, 1f, 1f)
        worldManager.eliminatePlayer(player, blockMaterial, messenger)
    }

    /**
     * Handles game over (draw).
     */
    private fun handleGameOverDraw() {
        for (player in Bukkit.getOnlinePlayers()) {
            messenger.showGameOverDrawTitle(player)
            messenger.playSound(player, Sound.ENTITY_ENDER_DRAGON_DEATH, 0.4f, 1f)
        }
        messenger.broadcastLeaderboard(playerScores)
        cleanup()
    }

    /**
     * Handles game over (winner).
     */
    private fun handleGameOverWinner(winner: Player) {
        for (player in Bukkit.getOnlinePlayers()) {
            messenger.showGameOverWinnerTitle(player, winner.name)
            messenger.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
        }
        messenger.broadcastLeaderboard(playerScores)
        cleanup()
    }
}
