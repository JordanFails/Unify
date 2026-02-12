package me.jordanfails.unify.scoreboard

import me.jordanfails.unify.UnifyCore
import org.bukkit.entity.Player
import java.util.Locale

abstract class ScoreboardProvider(val name: String, val weight: Int) {

    abstract fun fetchScoreboard(player: Player): ScoreboardInfo?

    class DefaultScoreboardProvider : ScoreboardProvider("Default Provider", 0) {
        override fun fetchScoreboard(player: Player): ScoreboardInfo {
//            val phase = ((System.currentTimeMillis() % 4000L).toDouble() / 4000.0) * 2.0 - 1.0
//            val title = "<rainbow:${String.format(Locale.US, "%.2f", phase)}><bold>Unify</bold></rainbow>"
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
