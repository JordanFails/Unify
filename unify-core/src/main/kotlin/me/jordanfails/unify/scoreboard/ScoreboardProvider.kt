package me.jordanfails.unify.scoreboard

import me.jordanfails.unify.UnifyCore
import org.bukkit.entity.Player
import java.util.Locale

/**
 * Supplies display data for each player on every refresh; the highest-weight answer wins.
 *
 * Threading: Unify calls this on the main thread, so it may use the Bukkit API. If your plugin calls a
 * handler's reloadPlayer from another thread, it runs on that thread instead.
 * A provider that throws is skipped and logged once. Providers are removed when their plugin disables.
 */
abstract class ScoreboardProvider(val name: String, val weight: Int) {

    abstract fun fetchScoreboard(player: Player): ScoreboardInfo?

    class DefaultScoreboardProvider : ScoreboardProvider("Default Provider", 0) {
        override fun fetchScoreboard(player: Player): ScoreboardInfo {
            return createScoreboard(UnifyCore.instance.config.getString("scoreboard.title", "&d&lHUB")!!, UnifyCore.instance.config.getStringList("scoreboard.lines"))
        }
    }

    companion object {
        @JvmStatic
        fun createScoreboard(title: String, lines: List<String>): ScoreboardInfo {
            return ScoreboardInfo(title, lines)
        }
    }
}
