package me.devvy.blockshuffle.config

/**
 * Centralized game configuration.
 * Currently hardcoded, but can be extended to load from YAML config files.
 */
object GameConfig {
    // Time limits (in seconds)
    const val GAME_TIME_LIMIT: Int = 300
    const val PREPARE_TIME_LIMIT: Int = 120
    const val ROUND_INTERMISSION_TIME_LIMIT: Int = 10

    // Game loop frequency (ticks per second the task runs)
    const val TASK_FREQUENCY: Int = 10

    // Tick duration for scheduler
    const val TASK_TICK_PERIOD: Long = (20 / TASK_FREQUENCY).toLong()

    // Title duration settings (in milliseconds)
    const val TITLE_FADE_IN_MS: Long = 500
    const val TITLE_STAY_MS: Long = 5000
    const val TITLE_FADE_OUT_MS: Long = 500

    // World setup
    const val DEFAULT_WORLD: String = "world"
    const val SPAWN_SEARCH_RANGE: Int = 100000

    // Blitz mode settings
    const val BLITZ_STARTING_TIME_SECONDS: Int = 300
    const val BLITZ_TIME_PER_BLOCK_SECONDS: Int = 30
    const val BLITZ_TIME_BONUS_PER_DIFFICULTY = 30
    const val BLITZ_DAMAGE_TO_TIME_RATIO: Double = 1.0
}
