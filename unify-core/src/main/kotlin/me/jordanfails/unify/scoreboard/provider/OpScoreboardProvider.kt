package me.jordanfails.unify.scoreboard.provider

import me.jordanfails.unify.scoreboard.ScoreboardInfo
import me.jordanfails.unify.scoreboard.ScoreboardProvider
import org.bukkit.entity.Player

class OpScoreboardProvider : ScoreboardProvider("OP Scoreboard Provider", 50) {
    
    override fun fetchScoreboard(player: Player): ScoreboardInfo? {
        if (!player.isOp) {
            return null
        }

        return createScoreboard("&c&lOP MODE", listOf(
            "",
            "&fPlayer: &e${player.name}",
            "&fOP: &aYes",
            "&fWorld: &e${player.world.name}",
            ""
        ))
    }
}
