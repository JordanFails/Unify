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

        // Drop trailing slots when line count shrinks (e.g. game switch); otherwise old rows stay visible.
        for (rowIndex in rows.size until MAX_SIDEBAR_ROWS) {
            val stableEntry = stableSidebarEntry(rowIndex)
            nms.sendScoreboardScore(player, objectiveName, stableEntry, 0, 1) // REMOVE
        }
    }

    fun remove(player: Player) {
        val nms = UnifyCore.instance.nms ?: return
        nms.sendScoreboardObjective(player, objectiveName, "", 1) // 1 = REMOVE
    }

    companion object {

        private const val SIDEBAR_OBJECTIVE = "sidebar"

        /** Must match [stableSidebarEntry] valid indices (sidebar allows 15 lines). */
        private const val MAX_SIDEBAR_ROWS = 15

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

        /**
         * Splits a legacy §-colored line into team prefix/suffix without cutting between `§` and its
         * format character (which would leak naked letters/digits next to the fake score entry).
         */
        internal fun splitSidebarPrefixSuffix(
            translatedLine: String,
            prefixLimit: Int,
            suffixLimit: Int,
        ): Pair<String, String> {
            val units = legacyColorUnits(translatedLine)
            val totalChars = units.sumOf { it.length }
            if (totalChars <= prefixLimit) {
                return translatedLine to ""
            }
            val prefixUnits = mutableListOf<String>()
            var prefixLen = 0
            for (u in units) {
                val ul = u.length
                if (prefixLen + ul <= prefixLimit) {
                    prefixUnits.add(u)
                    prefixLen += ul
                } else {
                    break
                }
            }
            val rest = units.drop(prefixUnits.size)
            val suffixUnits = mutableListOf<String>()
            var suffixLen = 0
            val cap = suffixLimit.coerceAtLeast(0)
            for (u in rest) {
                val ul = u.length
                if (suffixLen + ul <= cap) {
                    suffixUnits.add(u)
                    suffixLen += ul
                } else {
                    break
                }
            }
            return prefixUnits.joinToString("") to suffixUnits.joinToString("")
        }

        /** Each element is one visible char or one legacy pair `§` + modifier (`§e`, `§r`, …). */
        internal fun legacyColorUnits(line: String): List<String> {
            val out = ArrayList<String>()
            var i = 0
            while (i < line.length) {
                val c = line[i]
                if (c == '\u00A7' && i + 1 < line.length) {
                    out.add(line.substring(i, i + 2))
                    i += 2
                } else {
                    out.add(c.toString())
                    i += 1
                }
            }
            return out
        }

        fun createFromInfo(info: ScoreboardInfo): ScoreboardPacket {
            return ScoreboardPacket(SIDEBAR_OBJECTIVE, info.title, buildSidebarRows(info))
        }

        /** Removes Unify's packet sidebar ([SIDEBAR_OBJECTIVE]) from the client when no provider applies. */
        fun removePacketSidebar(player: Player) {
            val nms = UnifyCore.instance.nms ?: return
            nms.sendScoreboardObjective(player, SIDEBAR_OBJECTIVE, "", 1) // REMOVE
        }
    }
}
