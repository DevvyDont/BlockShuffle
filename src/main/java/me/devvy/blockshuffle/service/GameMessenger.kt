package me.devvy.blockshuffle.service

import me.devvy.blockshuffle.BlockShuffle
import me.devvy.blockshuffle.config.GameConfig
import me.devvy.blockshuffle.util.TextUtils
import me.devvy.blockshuffle.util.TextUtils.append
import me.devvy.blockshuffle.util.TextUtils.capitalizeFully
import me.devvy.blockshuffle.util.TextUtils.darkGray
import me.devvy.blockshuffle.util.TextUtils.darkRed
import me.devvy.blockshuffle.util.TextUtils.gray
import me.devvy.blockshuffle.util.TextUtils.playerName
import me.devvy.blockshuffle.util.TextUtils.yellow
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Centralized messaging and UI system for the game.
 * Handles all broadcasts, titles, action bars, and sounds.
 */
class GameMessenger {

    private val blockManager = BlockShuffle.getInstance().blockManager

    fun playTimerWarningSfx(player: Player, ticksLeft: Int) {
        val secondsLeft = (ticksLeft.toDouble() / GameConfig.TASK_FREQUENCY).toInt()
        if ((secondsLeft == 30 || secondsLeft == 60) && ticksLeft % GameConfig.TASK_FREQUENCY == 0) {
            playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.5f)
        } else if (secondsLeft <= 3 && ticksLeft % GameConfig.TASK_FREQUENCY == 0) {
            val pitch = 1.8f - secondsLeft * 0.15f
            playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, pitch)
        } else if (secondsLeft <= 5 && ticksLeft % GameConfig.TASK_FREQUENCY == 0) {
            playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f)
        } else if (secondsLeft <= 10 && ticksLeft % GameConfig.TASK_FREQUENCY == 0) {
            playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.75f)
        }
    }

    /**
     * Sends an action bar update to a player based on game state.
     */
    fun sendSimpleActionBar(
        player: Player,
        gameState: String,
        timeLeft: Int,
        assignedMaterial: Material? = null
    ) {
        val timeDisplay = formatTime(timeLeft)

        val actionBar: Component = when (gameState) {
            "PAUSED" -> append(darkRed("PAUSED"), darkGray(" | "), timeDisplay)
            "PREPARE" -> append(gray("Assigning blocks in: "), timeDisplay)
            "INGAME" -> {
                if (assignedMaterial != null) {
                    val name = capitalizeFully(
                        assignedMaterial.name.lowercase(Locale.getDefault()).replace("_", " ")
                    )
                    val matPart = append(gray("Stand on: "), TextUtils.blockWithDifficulty(name, blockManager.getBlockDifficulty(
                        assignedMaterial
                    )), gray(" | "))
                    append(matPart, timeDisplay)
                } else {
                    timeDisplay
                }
            }
            else -> timeDisplay
        }

        player.sendActionBar(actionBar)
    }

    /**
     * Sends an action bar update to a player based on game state. Contains more information than usual.
     */
    fun sendDetailedActionBar(
        player: Player,
        gameState: String,
        timeLeft: Int,
        score: Int,
        assignedMaterial: Material? = null,
        timeModifier: Int? = null
    ) {
        val timeDisplay = formatTime(timeLeft, timeModifier)
        val actionBar: Component = when (gameState) {
            "PAUSED" -> append(darkRed("PAUSED"), darkGray(" | "), timeDisplay)
            "PREPARE" -> append(gray("Assigning blocks in: "), timeDisplay)
            "INGAME" -> {
                if (assignedMaterial != null) {
                    val name = capitalizeFully(
                        assignedMaterial.name.lowercase(Locale.getDefault()).replace("_", " ")
                    )
                    val matPart = append(gray("Stand on: "), TextUtils.blockWithDifficulty(name, blockManager.getBlockDifficulty(
                        assignedMaterial
                    )), gray(" | "))
                    val score = append(gray(" | "), Component.text("\uD83C\uDFC6$score", NamedTextColor.YELLOW))
                    append(matPart, timeDisplay, score)
                } else {
                    timeDisplay
                }
            }
            else -> timeDisplay
        }

        player.sendActionBar(actionBar)
    }

    /**
     * Broadcasts a message to all players.
     */
    fun broadcastMessage(component: Component) {
        Bukkit.broadcast(component)
    }

    /**
     * Broadcasts block assignment message.
     */
    fun broadcastBlockAssignment(player: Player, material: Material) {
        var name = material.name.lowercase(Locale.getDefault()).replace("_", " ")
        name = TextUtils.articlize(name)

        val message = append(
            playerName(player.name),
            yellow(" must find and stand on "),
            TextUtils.blockWithDifficulty(capitalizeFully(name), blockManager.getBlockDifficulty(material)),
            yellow(".")
        )

        broadcastMessage(message)
    }

    /**
     * Broadcasts when a player successfully finds their block.
     */
    fun broadcastBlockFound(player: Player) {
        val message = append(
            playerName(player.name),
            Component.text(" found their block!", NamedTextColor.GREEN, TextDecoration.BOLD)
        )
        broadcastMessage(message)
    }

    /**
     * Broadcasts when a player fails to find their block.
     */
    fun broadcastBlockFailed(player: Player, material: Material) {
        var name = material.name.lowercase(Locale.getDefault()).replace("_", " ")
        name = TextUtils.articlize(name)

        val message = Component.text(
            player.name + " failed to find ${capitalizeFully(name)}!",
            NamedTextColor.RED,
            TextDecoration.BOLD
        )
        broadcastMessage(message)
    }

    /**
     * Broadcasts when a player is offline and fails.
     */
    fun broadcastOfflinePlayerFailed(playerName: String?) {
        val message = Component.text(
            "$playerName was offline and failed to find their block!",
            NamedTextColor.GRAY,
            TextDecoration.BOLD
        )
        broadcastMessage(message)
    }

    /**
     * Shows a title for successful block placement.
     */
    fun showBlockFoundTitle(player: Player) {
        player.showTitle(
            Title.title(
                Component.text("Safe!", NamedTextColor.GREEN),
                Component.text("+1 point!!!", NamedTextColor.GRAY),
                Title.Times.times(
                    Duration.ofMillis(GameConfig.TITLE_FADE_IN_MS),
                    Duration.ofMillis(GameConfig.TITLE_STAY_MS),
                    Duration.ofMillis(GameConfig.TITLE_FADE_OUT_MS)
                )
            )
        )
    }

    /**
     * Shows round start title.
     */
    fun showRoundTitle(player: Player, roundNumber: Int, playersRemaining: Int) {

        // Normal round display
        if (roundNumber > 0) {
            player.showTitle(
                Title.title(
                    Component.text("Round $roundNumber", NamedTextColor.GOLD),
                    Component.text("$playersRemaining players remain...", NamedTextColor.RED),
                    Title.Times.times(
                        Duration.ofMillis(GameConfig.TITLE_FADE_IN_MS),
                        Duration.ofMillis(GameConfig.TITLE_STAY_MS),
                        Duration.ofMillis(GameConfig.TITLE_FADE_OUT_MS)
                    )
                )
            )
            return
        }

        // First round display (initial one)
        player.showTitle(Title.title(
            Component.text("Block Shuffle!", NamedTextColor.LIGHT_PURPLE),
            Component.text("Stand on your block to save yourself!", NamedTextColor.GRAY),
        ))

    }

    /**
     * Shows game over title (draw).
     */
    fun showGameOverDrawTitle(player: Player) {
        player.showTitle(
            Title.title(
                Component.text("GAME OVER!", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("It was a draw!!!", NamedTextColor.GRAY),
                Title.Times.times(
                    Duration.ofMillis(GameConfig.TITLE_FADE_IN_MS),
                    Duration.ofMillis(GameConfig.TITLE_STAY_MS),
                    Duration.ofMillis(GameConfig.TITLE_FADE_OUT_MS)
                )
            )
        )
    }

    /**
     * Shows game over title (winner).
     */
    fun showGameOverWinnerTitle(player: Player, winner: String) {
        player.showTitle(
            Title.title(
                Component.text("GAME OVER!", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("$winner won!", NamedTextColor.AQUA),
                Title.Times.times(
                    Duration.ofMillis(GameConfig.TITLE_FADE_IN_MS),
                    Duration.ofMillis(GameConfig.TITLE_STAY_MS),
                    Duration.ofMillis(GameConfig.TITLE_FADE_OUT_MS)
                )
            )
        )
    }

    /**
     * Updates player list name to indicate safe status.
     */
    fun setPlayerSafe(player: Player) {
        player.playerListName(Component.text("SAFE! ", NamedTextColor.GREEN).append(player.name()))
    }

    /**
     * Updates player list name to indicate failed status.
     */
    fun setPlayerFailed(player: Player) {
        player.playerListName(Component.text("FAILED ", NamedTextColor.RED).append(player.name()))
    }

    /**
     * Updates player list name to indicate in-game status.
     */
    fun setPlayerInGame(player: Player, assignedMaterial: Material) {
        val name = assignedMaterial.name.uppercase(Locale.getDefault()).replace("_", " ")
        val blockComp = TextUtils.blockWithDifficulty(name, blockManager.getBlockDifficulty(assignedMaterial))
        player.playerListName(blockComp.append(Component.space()).append(player.name().color(NamedTextColor.AQUA)))
    }

    /**
     * Resets player list name to normal.
     */
    fun resetPlayerListName(player: Player) {
        player.playerListName(Component.text(player.name, NamedTextColor.AQUA))
    }

    /**
     * Plays a sound effect at the player's location.
     */
    fun playSound(player: Player, sound: Sound, volume: Float, pitch: Float) {
        player.world.playSound(player.location, sound, volume, pitch)
    }

    /**
     * Broadcasts leaderboard/stats at end of game.
     */
    fun broadcastLeaderboard(scoreMap: Map<UUID, Int>) {
        broadcastMessage(Component.empty())
        broadcastMessage(
            Component.text("               Leaderboard", NamedTextColor.YELLOW, TextDecoration.BOLD)
        )
        broadcastMessage(Component.text("----------------------------------------", NamedTextColor.GREEN))
        broadcastMessage(Component.empty())

        val orderedPlayers = scoreMap.keys.toMutableList()
        orderedPlayers.shuffle()
        orderedPlayers.sortWith(compareBy<UUID> { -scoreMap[it]!! })

        for ((index, uuid) in orderedPlayers.withIndex()) {
            val placement = Component.text(
                "${index + 1}${getPlacementSuffix(index)}",
                getPlacementColor(index),
                TextDecoration.BOLD
            )
            val offlinePlayer = Bukkit.getOfflinePlayer(uuid)
            val playerNameComponent = if (offlinePlayer.name == null) {
                Component.text("Unknown Player")
            } else {
                Component.text(offlinePlayer.name!!, NamedTextColor.AQUA)
            }
            val score = Component.text(
                "${scoreMap[uuid]} blocks",
                NamedTextColor.LIGHT_PURPLE
            )
            val line = append(
                placement,
                Component.text(": ", NamedTextColor.GRAY),
                playerNameComponent,
                Component.text(" - ", NamedTextColor.GRAY),
                score
            )
            broadcastMessage(line)
            broadcastMessage(Component.empty())
        }

        broadcastMessage(Component.text("----------------------------------------", NamedTextColor.GREEN))
    }

    /**
     * Formats time remaining for display.
     */
    fun formatTime(timerTicks: Int, timeModifier: Int? = null): Component {


        val rawSeconds = timerTicks.toDouble() / GameConfig.TASK_FREQUENCY
        val millis = (rawSeconds * 1000).toLong()

        val hr = TimeUnit.MILLISECONDS.toHours(millis)
        val min = TimeUnit.MILLISECONDS.toMinutes(millis - TimeUnit.HOURS.toMillis(hr))
        val sec = TimeUnit.MILLISECONDS.toSeconds(
            millis - TimeUnit.HOURS.toMillis(hr) - TimeUnit.MINUTES.toMillis(min)
        )
        val ms = TimeUnit.MILLISECONDS.toMillis(
            millis - TimeUnit.HOURS.toMillis(hr) - TimeUnit.MINUTES.toMillis(min) - TimeUnit.SECONDS.toMillis(sec)
        )

        val timer =  when {
            min >= 5 -> Component.text(String.format("%02d:%02d", min, sec), NamedTextColor.AQUA)
            min > 0 -> Component.text(String.format("%02d:%02d", min, sec), NamedTextColor.GREEN)
            sec >= 45 -> Component.text(String.format("%02d.%01ds", sec, ms / 100), NamedTextColor.YELLOW)
            sec >= 30 -> Component.text(String.format("%02d.%01ds", sec, ms / 100), NamedTextColor.GOLD)
            sec >= 10 -> Component.text(String.format("%02d.%01ds", sec, ms / 100), NamedTextColor.RED)
            else -> Component.text(
                String.format("%02d.%01ds", sec, ms / 100),
                if (ms / 100 > 4) NamedTextColor.DARK_RED else NamedTextColor.DARK_GRAY,
                TextDecoration.BOLD
            )
        }

        var delta = Component.empty()
        if (timeModifier != null && timeModifier != 0) {
            val op = if (timeModifier > 0) "+" else ""
            val color = if (timeModifier > 0) NamedTextColor.GREEN else NamedTextColor.RED
            delta = Component.text(" ($op${timeModifier}s)", color)
        }
        return append(timer, delta)
    }

    /**
     * Gets placement-specific color (gold, silver, bronze).
     */
    private fun getPlacementColor(index: Int): TextColor {
        return when (index) {
            0 -> TextColor.color(255, 255, 0) // Gold
            1 -> TextColor.color(230, 230, 230) // Silver
            2 -> TextColor.color(200, 126, 80) // Bronze
            else -> NamedTextColor.GRAY
        }
    }

    /**
     * Gets ordinal suffix (st, nd, rd, th).
     */
    private fun getPlacementSuffix(index: Int): String {
        val place = ((index + 1) % 10).toString()
        return when (place[0]) {
            '0', '4', '5', '6', '7', '8', '9' -> "th"
            '1' -> "st"
            '2' -> "nd"
            '3' -> "rd"
            else -> "th"
        }
    }
}

