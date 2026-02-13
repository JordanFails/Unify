package me.jordanfails.unify.scoreboard

import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.utils.CC
import org.bukkit.entity.Player

/**
 * Wrapper for sending scoreboard objective/score packets directly to players.
 * This prevents the scoreboard from appearing in /scoreboard command output.
 */
class ScoreboardPacket(
    val objectiveName: String,
    val title: String,
    val lines: Map<String, Int> // Entry text -> Score value
) {
    
    fun send(player: Player) {
        val nms = UnifyCore.instance.nms ?: return

        // 1. First, create the objective
        // Pass raw title - NMS handlers handle MiniMessage/legacy conversion themselves
        nms.sendScoreboardObjective(player, objectiveName, title, 0) // 0 = CREATE
        
        // 2. Then display it in the sidebar
        nms.sendScoreboardDisplaySlot(player, objectiveName, 1) // 1 = SIDEBAR
        
        // 3. Finally, send all the score entries
        for ((entry, score) in lines) {
            val translatedEntry = CC.translate(entry)
            nms.sendScoreboardScore(player, objectiveName, translatedEntry, score, 0) // 0 = CHANGE
        }
    }
    
    fun remove(player: Player) {
        val nms = UnifyCore.instance.nms ?: return
        
        // Remove objective
        nms.sendScoreboardObjective(player, objectiveName, "", 1) // 1 = REMOVE
    }
    
    companion object {
        fun createFromInfo(info: ScoreboardInfo): ScoreboardPacket {
            val lines = mutableMapOf<String, Int>()
            val usedTranslatedEntries = mutableSetOf<String>()
            val displayLines = info.lines.take(15)
            var score = displayLines.size

            for (line in displayLines) {
                // Scoreboard entry keys must be non-empty; blank lines are represented as invisible reset codes.
                var entry = line.ifBlank { "&r" }
                var translatedEntry = CC.translate(entry)

                // Ensure uniqueness after translation (raw duplicates can still collide once colorized).
                while (usedTranslatedEntries.contains(translatedEntry)) {
                    entry += "&r"
                    translatedEntry = CC.translate(entry)
                }

                lines[entry] = score--
                usedTranslatedEntries += translatedEntry
            }

            return ScoreboardPacket("sidebar", info.title, lines)
        }
    }
}
