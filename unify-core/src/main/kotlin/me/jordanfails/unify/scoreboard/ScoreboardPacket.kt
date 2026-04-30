package me.jordanfails.unify.scoreboard

import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.utils.CC
import org.bukkit.entity.Player

/**
 * Wrapper for sending scoreboard objective/score packets directly to players.
 * This prevents the scoreboard from appearing in /scoreboard command output.
 *
 * Rows use **stable fake score entries** (color codes) plus **team prefix/suffix** for visible text,
 * so countdown and other changing lines update in place instead of stacking or requiring REMOVE packets.
 */
class ScoreboardPacket(
    val objectiveName: String,
    val title: String,
    /** Display lines (with &-colors); paired with descending score values for ordering. */
    private val rows: List<Pair<String, Int>>,
) {

    fun send(player: Player) {
        val nms = UnifyCore.instance.nms ?: return

        nms.sendScoreboardObjective(player, objectiveName, title, 0) // 0 = CREATE
        nms.sendScoreboardDisplaySlot(player, objectiveName, 1) // 1 = SIDEBAR

        val prefixLimit = nms.getTeamPrefixLimit()
        val suffixLimit = nms.getTeamPrefixLimit()

        for ((rowIndex, pair) in rows.withIndex()) {
            val (displayRaw, scoreVal) = pair
            val stableEntry = stableSidebarEntry(rowIndex)
            val translatedLine = CC.translate(displayRaw)
            val (prefix, suffix) = splitSidebarPrefixSuffix(translatedLine, prefixLimit, suffixLimit)

            nms.sendScoreboardScore(player, objectiveName, stableEntry, scoreVal, 0) // CHANGE
            nms.sendScoreboardSidebarTeamLine(
                player,
                sidebarTeamName(objectiveName, rowIndex),
                stableEntry,
                prefix,
                suffix,
            )
        }
    }

    fun remove(player: Player) {
        val nms = UnifyCore.instance.nms ?: return
        nms.sendScoreboardObjective(player, objectiveName, "", 1) // 1 = REMOVE
    }

    companion object {

        private const val SIDEBAR_OBJECTIVE = "sidebar"

        private fun buildSidebarRows(info: ScoreboardInfo): List<Pair<String, Int>> {
            val displayLines = info.lines.take(15)
            var score = displayLines.size
            val rows = mutableListOf<Pair<String, Int>>()
            for (line in displayLines) {
                val raw = line.ifBlank { "&r" }
                rows += raw to score--
            }
            return rows
        }

        /** Unique fake player name per sidebar slot (≤16 chars); must stay stable across ticks. */
        internal fun stableSidebarEntry(index: Int): String {
            require(index in 0 until 15) { "sidebar row index out of range: $index" }
            val codes = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e')
            return "\u00A7${codes[index]}\u00A7r"
        }

        internal fun sidebarTeamName(objectiveName: String, rowIndex: Int): String {
            val base = "${objectiveName.take(6)}_$rowIndex"
            return if (base.length <= 16) base else base.take(16)
        }

        internal fun splitSidebarPrefixSuffix(
            translatedLine: String,
            prefixLimit: Int,
            suffixLimit: Int,
        ): Pair<String, String> {
            if (translatedLine.length <= prefixLimit) {
                return translatedLine to ""
            }
            val prefix = translatedLine.take(prefixLimit)
            val suffix = translatedLine.drop(prefixLimit).take(suffixLimit.coerceAtLeast(0))
            return prefix to suffix
        }

        fun createFromInfo(info: ScoreboardInfo): ScoreboardPacket {
            return ScoreboardPacket(SIDEBAR_OBJECTIVE, info.title, buildSidebarRows(info))
        }
    }
}
