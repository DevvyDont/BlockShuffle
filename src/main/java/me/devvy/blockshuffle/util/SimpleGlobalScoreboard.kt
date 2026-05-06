package me.devvy.blockshuffle.util

import io.papermc.paper.scoreboard.numbers.NumberFormat
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scoreboard.*
import java.util.*


class SimpleGlobalScoreboard(private val scoreboard: Scoreboard, title: Component?) {
    var display: Objective = scoreboard.getObjective("boss") ?: scoreboard.registerNewObjective("boss", Criteria.DUMMY, title)
    private val players: MutableList<Player> = ArrayList()

    init {
        display.setAutoUpdateDisplay(true)
        display.displaySlot = DisplaySlot.SIDEBAR
        display.numberFormat(NumberFormat.blank())
    }

    fun display(player: Player) {
        player.scoreboard = scoreboard
        display.displaySlot = DisplaySlot.SIDEBAR

        if (!players.contains(player)) players.add(player)
    }

    fun hide(player: Player) {
        player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        players.remove(player)
    }

    fun cleanup() {
        for (player in players.stream().toList())
            hide(player)
        players.clear()
        display.displaySlot = null
        setLines()
    }

    /**
     * The scoreboard will display from 15,14,13, etc to 1.
     *
     * @param realIndex
     * @return
     */
    private fun convertRealIndexToScoreIndex(realIndex: Int): Int {
        return 15 - realIndex
    }

    private fun getLine(index: Int): Team {
        val id = '§'.toString() + Integer.toHexString(index)
        var team: Team? = scoreboard.getTeam(id)
        if (team == null) {
            team = scoreboard.registerNewTeam(id)
            team.addEntry(id)
        }
        return team
    }

    fun setLines(vararg lines: Component) {
        setLines(Arrays.stream(lines).toList())
    }

    fun setLines(lines: MutableList<Component>) {
        for (i in 0..15) {
            val line: Team = getLine(i)
            val score: Score = display.getScore(line.name)

            if (i >= lines.size) {
                score.resetScore()
                continue
            }

            score.score = convertRealIndexToScoreIndex(i)
            line.prefix(lines[i])
        }
    }

    fun showing(player: Player?): Boolean {
        return players.contains(player)
    }
}