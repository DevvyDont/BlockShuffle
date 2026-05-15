package me.devvy.blockshuffle.util

import me.devvy.blockshuffle.config.GameConfig
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minecraft.network.chat.TextColor
import org.bukkit.Material
import java.util.concurrent.TimeUnit

/**
 * Consolidated utility functions for text and component handling.
 */
object TextUtils {

    // ===== Text formatting =====

    /**
     * Capitalizes the first letter of each word in a string.
     * Example: "nether brick" -> "Nether Brick"
     */
    fun capitalizeFully(str: String): String {
        return str.split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * Converts an article + noun string to use proper grammar.
     * Example: "a nether_brick" -> "a Nether Brick"
     */
    fun articlize(str: String): String {
        val formatted = capitalizeFully(str)
        val article = if ("aeiou".contains(str.first(), ignoreCase = true)) "an" else "a"
        return "$article $formatted"
    }

    /**
     * Formats time in ticks as a simple string representation (no coloring).
     * Returns time in MM:SS.Ds format (e.g., "01:30.5s" or "00:05.0s").
     */
    fun formatTimeSimple(timerTicks: Int): String {
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

        return when {
            min >= 5 -> String.format("%02d:%02d", min, sec)
            min > 0 -> String.format("%02d:%02d", min, sec)
            else -> String.format("%02d.%01ds", sec, ms / 100)
        }
    }

    // ===== Component helpers =====

    /**
     * Appends multiple components together.
     */
    fun append(vararg components: Component): Component {
        var comp = Component.empty()
        for (c in components) comp = comp.append(c)
        return comp
    }

    /**
     * Creates a player name component (Aqua color).
     */
    fun playerName(playerName: String): Component =
        Component.text(playerName, NamedTextColor.AQUA)

    /**
     * Creates a block name component (Red bold).
     */
    fun blockName(blockName: String): Component =
        Component.text(blockName, NamedTextColor.RED, TextDecoration.BOLD)

    /**
     * Creates a block name component (Red bold).
     */
    fun blockWithDifficulty(blockName: String, stars: Int): Component =
        Component.text(blockName, NamedTextColor.RED, TextDecoration.BOLD).append(difficultyStars(stars))

    /**
     * Creates a gray text component.
     */
    fun gray(text: String): Component =
        Component.text(text, NamedTextColor.GRAY)

    /**
     * Creates a yellow text component.
     */
    fun yellow(text: String): Component =
        Component.text(text, NamedTextColor.YELLOW)

    /**
     * Creates a dark red text component.
     */
    fun darkRed(text: String): Component =
        Component.text(text, NamedTextColor.DARK_RED)

    /**
     * Creates a dark gray text component.
     */
    fun darkGray(text: String): Component =
        Component.text(text, NamedTextColor.DARK_GRAY)

    fun difficultyStars(stars: Int, showAll: Boolean = false): Component {
        var str =  "⭐".repeat(stars)
        if (showAll)
            str += "✰".repeat(10 - stars)
        val color = when (stars) {
            1 -> NamedTextColor.WHITE
            2 -> NamedTextColor.GREEN
            3 -> NamedTextColor.DARK_GREEN
            4 -> NamedTextColor.YELLOW
            5 -> NamedTextColor.GOLD
            6 -> NamedTextColor.RED
            7 -> NamedTextColor.DARK_RED
            8 -> NamedTextColor.AQUA
            9 -> NamedTextColor.DARK_PURPLE
            else -> NamedTextColor.LIGHT_PURPLE
        }
        return Component.text(str, color).decoration(TextDecoration.BOLD, false)
    }
}
