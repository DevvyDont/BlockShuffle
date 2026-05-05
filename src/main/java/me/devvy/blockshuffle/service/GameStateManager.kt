package me.devvy.blockshuffle.service

import me.devvy.blockshuffle.config.GameConfig

/**
 * Manages game state and timer logic.
 */
class GameStateManager {

    enum class GameState {
        PREPARE,
        PAUSED,
        INGAME
    }

    private var state = GameState.PREPARE
    private var paused = false
    private var timer: Int = GameConfig.PREPARE_TIME_LIMIT * GameConfig.TASK_FREQUENCY
    private var roundNumber: Int = -1

    fun getState(): GameState = state

    fun getTimer(): Int = timer

    fun getRoundNumber(): Int = roundNumber

    fun isPaused(): Boolean = paused

    /**
     * Sets pause state and adjusts game state accordingly.
     */
    fun setPaused(shouldPause: Boolean) {
        paused = shouldPause
        if (shouldPause) {
            state = GameState.PAUSED
        } else {
            state = if (roundNumber >= 0) GameState.INGAME else GameState.PREPARE
        }
    }

    /**
     * Transitions to in-game state.
     */
    fun startGame() {
        state = GameState.INGAME
        paused = false
        timer = GameConfig.GAME_TIME_LIMIT * GameConfig.TASK_FREQUENCY
        roundNumber = 0
    }

    /**
     * Starts a new round.
     */
    fun nextRound() {
        roundNumber++
        timer = GameConfig.GAME_TIME_LIMIT * GameConfig.TASK_FREQUENCY
    }

    /**
     * Decrements timer and returns true if time is up.
     */
    fun tickTimer(): Boolean {
        if (timer <= 0) return true
        timer--
        return false
    }

    /**
     * Resets timer to game duration.
     */
    fun resetTimer() {
        timer = GameConfig.GAME_TIME_LIMIT * GameConfig.TASK_FREQUENCY
    }

    /**
     * Handles special timer state for round completion.
     * Returns true if game should continue, false if should end.
     */
    fun handleTimerEndOfRound(): Boolean {
        if (timer == GameConfig.GAME_TIME_LIMIT * GameConfig.TASK_FREQUENCY * -7) {
            resetTimer()
            return false
        }
        return true
    }

    /**
     * Checks if prepare phase just finished.
     */
    fun isPreparePhaseEnding(): Boolean {
        return state == GameState.PREPARE && timer == 0
    }

    /**
     * Checks if it's time to assign new blocks (beginning of round).
     */
    fun isTimeToAssignBlocks(): Boolean {
        return state == GameState.INGAME && timer == GameConfig.GAME_TIME_LIMIT * GameConfig.TASK_FREQUENCY
    }

    /**
     * Checks if the game time has run out for current round.
     */
    fun isRoundTimeUp(): Boolean {
        return timer == 0
    }
}

