package me.jordanfails.unify.nms.impl

import org.bukkit.entity.Player

interface NMSScoreboardTeam {
    fun createTeam(teamName: String, prefix: String, suffix: String, players: Collection<String>)
    fun updateTeam(teamName: String, prefix: String, suffix: String)
    fun removeTeam(teamName: String)
    fun send(player: Player)
}