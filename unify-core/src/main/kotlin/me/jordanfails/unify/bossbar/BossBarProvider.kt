package me.jordanfails.unify.bossbar

import me.jordanfails.unify.config.UnifyConfig
import org.bukkit.entity.Player

/**
 * Supplies a player's boss-bar HUD each refresh.
 *
 * Providers are tried highest [weight] first; the first non-null [fetchBossBar] wins, exactly like
 * [me.jordanfails.unify.scoreboard.ScoreboardProvider]. Returning `null` means "no opinion" and
 * lets a lower-weight provider answer; returning [BossBarInfo.EMPTY] clears the HUD.
 */
abstract class BossBarProvider(val name: String, val weight: Int) {

    abstract fun fetchBossBar(player: Player): BossBarInfo?

    /**
     * Renders `bossbar.lines` from config. Each entry is `title|color|style|progress`, where every
     * field after the title is optional (e.g. `&aEvent starts in {time}|GREEN|SEGMENTED_10|0.5`).
     */
    class DefaultBossBarProvider : BossBarProvider("Default Provider", 0) {
        override fun fetchBossBar(player: Player): BossBarInfo? {
            val lines = UnifyConfig.BossBar.lines.get()
            if (lines.isEmpty()) return null
            return BossBarInfo(lines.mapIndexed { index, raw -> parseLine("config-$index", raw) })
        }
    }

    companion object {
        @JvmStatic
        fun createBossBar(vararg lines: BossBarLine): BossBarInfo = BossBarInfo(lines.toList())

        @JvmStatic
        fun createBossBar(lines: List<BossBarLine>): BossBarInfo = BossBarInfo(lines)

        /**
         * Parses `title|color|style|progress` into a [BossBarLine]. Unknown colors/styles fall back
         * to the [BossBarLine] defaults rather than throwing, so a typo can't break the HUD.
         */
        @JvmStatic
        fun parseLine(id: String, raw: String): BossBarLine {
            val parts = raw.split('|')
            val title = parts.getOrNull(0)?.trim().orEmpty()
            val color = parts.getOrNull(1)?.let { BossBarColor.fromString(it) } ?: BossBarColor.PURPLE
            val style = parts.getOrNull(2)?.let { BossBarStyle.fromString(it) } ?: BossBarStyle.SOLID
            val progress = parts.getOrNull(3)?.trim()?.toDoubleOrNull() ?: 1.0
            return BossBarLine(
                id = id,
                title = title,
                progress = progress.coerceIn(0.0, 1.0),
                color = color,
                style = style,
            )
        }
    }
}
