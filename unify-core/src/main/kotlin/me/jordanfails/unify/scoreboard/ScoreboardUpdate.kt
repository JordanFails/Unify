package me.jordanfails.unify.scoreboard

import org.bukkit.entity.Player

data class ScoreboardUpdate(val player: Player) {
    
    override fun equals(other: Any?): Boolean {
        return other is ScoreboardUpdate && other.player.uniqueId == this.player.uniqueId
    }

    override fun hashCode(): Int {
        return player.uniqueId.hashCode()
    }
}
