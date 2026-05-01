package me.devvy.blockshuffle.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration

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
}

