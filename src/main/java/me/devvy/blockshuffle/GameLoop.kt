package me.devvy.blockshuffle

import me.devvy.blockshuffle.BlockShuffle.Companion.getInstance
import me.devvy.blockshuffle.config.GameConfig
import me.devvy.blockshuffle.service.GameMessenger
import me.devvy.blockshuffle.service.GameStateManager
import me.devvy.blockshuffle.service.GameStateManager.GameState
import me.devvy.blockshuffle.service.RoundManager
import me.devvy.blockshuffle.service.WorldManager
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.scheduler.BukkitRunnable

/**
 * Main game loop that orchestrates all game logic.
 * Delegates to service classes for specific concerns.
 */
class GameLoop(private val blockManager: me.devvy.blockshuffle.service.BlockManager) : BukkitRunnable(), Listener {

    private val stateManager = GameStateManager()
    private val roundManager = RoundManager(blockManager)
    private val messenger = GameMessenger()
    private val worldManager = WorldManager()

    init {
        roundManager.initializeAllPlayers()
    }

    fun isPaused(): Boolean {
        return stateManager.isPaused()
    }

    fun setPaused(paused: Boolean) {
        stateManager.setPaused(paused)
    }

    // All state management delegated to services; methods removed
    // scoreTracker, failedPlayers, assignedMaterialMap accessed via services

    private fun doBlockCheck(player: Player) {
        val assignedMaterial = roundManager.getAssignedBlock(player) ?: return
        val blockBelow = player.location.block.getRelative(BlockFace.DOWN).type

        if (blockBelow == assignedMaterial) {
            messenger.playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.0f)
            messenger.broadcastBlockFound(player)
            messenger.setPlayerSafe(player)
            messenger.showBlockFoundTitle(player)
            roundManager.increaseScore(player)
            roundManager.clearAssignedBlock(player)
        }
    }

    private fun displayTime() {
        val state = stateManager.getState().toString()
        val timerTicks = stateManager.getTimer()

        for (player in Bukkit.getOnlinePlayers()) {
            val assignedMat = roundManager.getAssignedBlock(player)
            messenger.sendActionBar(player, state, timerTicks, assignedMat)

            // Play timer sounds
            val simpleSecondsLeft = (timerTicks.toDouble() / GameConfig.TASK_FREQUENCY).toInt()
            if ((simpleSecondsLeft == 30 || simpleSecondsLeft == 60) && timerTicks % GameConfig.TASK_FREQUENCY == 0) {
                messenger.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.5f)
            } else if (simpleSecondsLeft <= 3 && timerTicks % GameConfig.TASK_FREQUENCY == 0) {
                val pitch = 1.8f - simpleSecondsLeft * 0.15f
                messenger.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, pitch)
            } else if (simpleSecondsLeft <= 5 && timerTicks % GameConfig.TASK_FREQUENCY == 0) {
                messenger.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f)
            } else if (simpleSecondsLeft <= 10 && timerTicks % GameConfig.TASK_FREQUENCY == 0) {
                messenger.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.75f)
            }
        }
    }

    private fun assignBlockToPlayer(player: Player) {
        val material = roundManager.assignRandomBlock(player) ?: return
        messenger.broadcastBlockAssignment(player, material)
        messenger.setPlayerInGame(player, material)
    }

    fun stop() {
        setPaused(true)
        this.cancel()
        worldManager.resetAllPlayersAfterGame(messenger)

        object : BukkitRunnable() {
            override fun run() {
                getInstance().stop()
            }
        }.runTaskLater(getInstance(), (20 * 5).toLong())
    }

    override fun run() {
        if (stateManager.getTimer() >= 0) displayTime()

        // If the game is paused, just show the clock
        if (stateManager.isPaused()) return

        // Handle special timer states
        if (!stateManager.handleTimerEndOfRound()) {
            stateManager.resetTimer()
            return
        }

        // Handle prepare phase
        if (stateManager.getState() == GameState.PREPARE) {
            if (stateManager.isPreparePhaseEnding()) {
                stateManager.startGame()
            } else {
                stateManager.tickTimer()
            }
            return
        }

        // Handle start of round (assign blocks)
        if (stateManager.isTimeToAssignBlocks()) {
            handleRoundStart()
        }

        // Handle end of round (eliminate failed players)
        if (stateManager.isRoundTimeUp()) {
            handleRoundEnd()
            stateManager.resetTimer()
            return
        }

        stateManager.tickTimer()

        // Check if all active players have completed their blocks
        if (stateManager.getTimer() > 0 && roundManager.areAllPlayersCompleted()) {
            stateManager.nextRound()
        }
    }

    /**
     * Handles the start of a new round.
     */
    private fun handleRoundStart() {
        val playersStillIn = roundManager.getPlayersStillInGame()

        // Check for game end conditions
        if (playersStillIn.isEmpty()) {
            handleGameOverDraw()
            return
        }

        if (playersStillIn.size == 1) {
        if (playersStillIn.size == 1 && Bukkit.getOnlinePlayers().size != 1) {
            handleGameOverWinner(playersStillIn[0])
            return
        }

        for (player in Bukkit.getOnlinePlayers()) {
            if (playersStillIn.contains(player)) {
                assignBlockToPlayer(player)
            } else {
                roundManager.clearAssignedBlock(player)
            }

            messenger.playSound(player, Sound.ENTITY_VILLAGER_CELEBRATE, 1f, 1f)
            messenger.showRoundTitle(player, stateManager.getRoundNumber() + 1, playersStillIn.size)
        }
    }

    /**
     * Handles elimination of failed players at end of round.
     */
    private fun handleRoundEnd() {
        val failedUUIDs = roundManager.getPlayersStillInGame()
            .filter { roundManager.hasAssignedBlock(it) }
            .map { it.uniqueId }

        for (uuid in failedUUIDs) {
            val player = Bukkit.getPlayer(uuid)
            val assignedMaterial = roundManager.getAssignedBlock(player!!) ?: continue

            messenger.broadcastBlockFailed(player, assignedMaterial)
            worldManager.eliminatePlayer(player, assignedMaterial, messenger)
            roundManager.markPlayerFailed(player)
        }

        roundManager.resetRound()
    }

    /**
     * Handles game over (draw).
     */
    private fun handleGameOverDraw() {
        for (player in Bukkit.getOnlinePlayers()) {
            messenger.showGameOverDrawTitle(player)
            messenger.playSound(player, Sound.ENTITY_ENDER_DRAGON_DEATH, 0.4f, 1f)
        }
        stop()
        messenger.broadcastLeaderboard(roundManager.getAllScores())
    }

    /**
     * Handles game over (winner).
     */
    private fun handleGameOverWinner(winner: Player) {
        for (player in Bukkit.getOnlinePlayers()) {
            messenger.showGameOverWinnerTitle(player, winner.name)
            messenger.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
        }
        stop()
        messenger.broadcastLeaderboard(roundManager.getAllScores())
    }

    @EventHandler
    fun onPlayerMoveEvent(event: PlayerMoveEvent) {
        if (stateManager.isPaused()) return

        if (!roundManager.hasAssignedBlock(event.player)) return

        if (event.from.block != event.to.block) doBlockCheck(event.player)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (roundManager.hasPlayerFailed(event.player)) return

        if (roundManager.hasAssignedBlock(event.player)) return

        if (stateManager.getState() != GameState.PREPARE) {
            assignBlockToPlayer(event.player)
            roundManager.initializePlayer(event.player)
        }
    }

}

