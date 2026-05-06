package me.devvy.blockshuffle.gamemode

import me.devvy.blockshuffle.config.GameConfig
import me.devvy.blockshuffle.service.BlockManager
import me.devvy.blockshuffle.service.GameMessenger
import me.devvy.blockshuffle.service.GameStateManager
import me.devvy.blockshuffle.service.GameStateManager.GameState
import me.devvy.blockshuffle.service.RoundManager
import me.devvy.blockshuffle.service.WorldManager
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent

/**
 * Classic Block Shuffle mode: Global timer, synchronized rounds.
 * All players must find their assigned block within the round time limit.
 * If they fail, they are eliminated.
 */
class ClassicMode(
    private val blockManager: BlockManager
) : GameMode {

    private val stateManager = GameStateManager()
    private val roundManager = RoundManager(blockManager)
    private val messenger = GameMessenger(blockManager)
    private val worldManager = WorldManager()
    private var isGameOver = false

    override fun initialize() {
        roundManager.initializeAllPlayers()
    }

    override fun tick() {
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

    override fun onPlayerMove(player: Player, location: Location) {
        if (stateManager.isPaused()) return

        if (!roundManager.hasAssignedBlock(player)) return

        doBlockCheck(player, location)
    }

    override fun onPlayerJoin(event: PlayerJoinEvent) {
        if (roundManager.hasPlayerFailed(event.player)) return

        if (roundManager.hasAssignedBlock(event.player)) return

        if (stateManager.getState() != GameState.PREPARE) {
            assignBlockToPlayer(event.player)
            roundManager.initializePlayer(event.player)
        }
    }

    override fun onPlayerDamage(player: Player, damage: Double) {
        // Classic mode doesn't track health
    }

    override fun onPlayerDeath(player: Player) {
        // Classic mode doesn't track deaths (only failures)
    }

    override fun cleanup() {
        setPaused(true)
        worldManager.resetAllPlayersAfterGame(messenger)
        isGameOver = true
    }

    override fun isPaused(): Boolean {
        return stateManager.isPaused()
    }

    override fun setPaused(paused: Boolean) {
        stateManager.setPaused(paused)
    }

    override fun isGameOver(): Boolean {
        return isGameOver
    }

    /**
     * Checks if player is standing on their assigned block.
     */
    private fun doBlockCheck(player: Player, location: Location) {
        val assignedMaterial = roundManager.getAssignedBlock(player) ?: return
        val blockBelow = location.block.getRelative(BlockFace.DOWN).type

        if (blockBelow == assignedMaterial) {
            messenger.playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.0f)
            messenger.broadcastBlockFound(player)
            messenger.setPlayerSafe(player)
            messenger.showBlockFoundTitle(player)
            roundManager.increaseScore(player)
            roundManager.clearAssignedBlock(player)
        }
    }

    /**
     * Displays timer and assigned block to all players.
     */
    private fun displayTime() {
        val state = stateManager.getState().toString()
        val timerTicks = stateManager.getTimer()

        for (player in Bukkit.getOnlinePlayers()) {
            val assignedMat = roundManager.getAssignedBlock(player)
            messenger.sendSimpleActionBar(player, state, timerTicks, assignedMat)
            // Play timer sounds
            messenger.playTimerWarningSfx(player, timerTicks)
        }
    }

    /**
     * Assigns a random block to a player (round-aware with difficulty progression).
     */
    private fun assignBlockToPlayer(player: Player) {
        val material = roundManager.assignRandomBlock(player, stateManager.getRoundNumber()) ?: return
        messenger.broadcastBlockAssignment(player, material)
        messenger.setPlayerInGame(player, material)
    }

    /**
     * Handles the start of a new round (assign blocks to all active players).
     */
    private fun handleRoundStart() {
        val playersStillIn = roundManager.getPlayersStillInGame()

        // Check for game end conditions
        if (playersStillIn.isEmpty()) {
            handleGameOverDraw()
            return
        }

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
     * Handles game over with no winners (all players eliminated or draw).
     */
    private fun handleGameOverDraw() {
        for (player in Bukkit.getOnlinePlayers()) {
            messenger.showGameOverDrawTitle(player)
            messenger.playSound(player, Sound.ENTITY_ENDER_DRAGON_DEATH, 0.4f, 1f)
        }
        messenger.broadcastLeaderboard(roundManager.getAllScores())
        cleanup()
    }

    /**
     * Handles game over with a winner.
     */
    private fun handleGameOverWinner(winner: Player) {
        for (player in Bukkit.getOnlinePlayers()) {
            messenger.showGameOverWinnerTitle(player, winner.name)
            messenger.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
        }
        messenger.broadcastLeaderboard(roundManager.getAllScores())
        cleanup()
    }

    /**
     * Gets the round manager (for external access if needed).
     */
    fun getRoundManager(): RoundManager {
        return roundManager
    }

    /**
     * Gets the state manager (for external access if needed).
     */
    fun getStateManager(): GameStateManager {
        return stateManager
    }
}

