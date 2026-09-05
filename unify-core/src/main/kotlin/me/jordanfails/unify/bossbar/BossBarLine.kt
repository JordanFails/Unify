package me.jordanfails.unify.bossbar

import java.text.NumberFormat
import java.util.Locale

/**
 * Immutable snapshot of one HUD row (and optional nested rows) returned by a [BossBarProvider].
 *
 * [id] is stable across refreshes so the handler can update in place instead of flicker-respawning.
 * Nested [children] render under this line with an extra indent.
 */
data class BossBarLine(
    val id: String,
    val title: String,
    val progress: Double = 1.0,
    val color: BossBarColor = BossBarColor.PURPLE,
    val style: BossBarStyle = BossBarStyle.SOLID,
    val flags: Set<BossBarFlag> = emptySet(),
    val children: List<BossBarLine> = emptyList(),
) {
    fun child(
        id: String,
        title: String,
        progress: Double = 1.0,
        color: BossBarColor = this.color,
        style: BossBarStyle = this.style,
        flags: Set<BossBarFlag> = emptySet(),
        children: List<BossBarLine> = emptyList(),
    ): BossBarLine = copy(
        children = this.children + BossBarLine(id, title, progress, color, style, flags, children)
    )

    fun flatten(depth: Int = 0): List<Pair<Int, BossBarLine>> {
        val out = ArrayList<Pair<Int, BossBarLine>>(1 + children.size)
        out += depth to this
        for (child in children) {
            out += child.flatten(depth + 1)
        }
        return out
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun of(
            id: String,
            title: String,
            progress: Double = 1.0,
            color: BossBarColor = BossBarColor.PURPLE,
            style: BossBarStyle = BossBarStyle.SOLID,
        ): BossBarLine = BossBarLine(id, title, progress, color, style)

        /**
         * Rank row matching the nested leaderboard style: gold `#1`, yellow otherwise,
         * with a US-grouped score and progress relative to [maxScore].
         */
        @JvmStatic
        @JvmOverloads
        fun ranked(
            rank: Int,
            name: String,
            score: Number,
            maxScore: Number,
            color: BossBarColor = BossBarColor.GREEN,
            id: String = "rank-$rank",
        ): BossBarLine {
            val rankColor = if (rank == 1) "&6" else "&e"
            val formatted = NUMBER_FORMAT.format(score.toLong())
            val max = maxScore.toDouble().coerceAtLeast(1.0)
            return BossBarLine(
                id = id,
                title = "$rankColor#$rank &f$name &7($formatted)",
                progress = (score.toDouble() / max).coerceIn(0.0, 1.0),
                color = color,
            )
        }

        private val NUMBER_FORMAT: NumberFormat = NumberFormat.getIntegerInstance(Locale.US)
    }
}
