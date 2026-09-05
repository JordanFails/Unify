package me.jordanfails.unify.bossbar

/**
 * A player's full boss-bar HUD: a stack of [BossBarLine]s, each of which may nest children.
 */
data class BossBarInfo(
    val lines: List<BossBarLine> = emptyList(),
) {
    fun flatten(): List<Pair<Int, BossBarLine>> = lines.flatMap { it.flatten() }

    companion object {
        @JvmField
        val EMPTY = BossBarInfo()

        @JvmStatic
        fun of(vararg lines: BossBarLine): BossBarInfo = BossBarInfo(lines.toList())

        @JvmStatic
        fun of(lines: List<BossBarLine>): BossBarInfo = BossBarInfo(lines)
    }
}
