package me.jordanfails.unify.nms.v1_9_R2

import me.jordanfails.unify.nms.impl.NMSScoreboardTeam
import net.minecraft.server.v1_9_R2.PacketPlayOutScoreboardTeam
import net.minecraft.server.v1_9_R2.Scoreboard
import net.minecraft.server.v1_9_R2.ScoreboardTeam
import org.bukkit.craftbukkit.v1_9_R2.entity.CraftPlayer
import org.bukkit.entity.Player

/**
 * Fully working raw-NMS 1_8_R3 ScoreboardTeam packet builder.
 * This directly uses the old NMS API—no reflection, no missing enums.
 */
@Suppress("unused")
class ScoreboardTeamHandler_v1_9_R2 : NMSScoreboardTeam {

    private var packet: PacketPlayOutScoreboardTeam? = null

    override fun createTeam(
        teamName: String,
        prefix: String,
        suffix: String,
        players: Collection<String>
    ) {
        val scoreboard = Scoreboard()
        val team = ScoreboardTeam(scoreboard, teamName)

        // Set prefixes and suffixes (raw strings in 1.8)
        team.prefix = prefix
        team.suffix = suffix

        team.setAllowFriendlyFire(true)
        team.setCanSeeFriendlyInvisibles(true)

        // Add all player names
        team.playerNameSet.addAll(players)

        packet = PacketPlayOutScoreboardTeam(team, 0) // 0 = create
    }

    override fun updateTeam(teamName: String, prefix: String, suffix: String) {
        val scoreboard = Scoreboard()
        val team = ScoreboardTeam(scoreboard, teamName)
        team.prefix = prefix
        team.suffix = suffix

        packet = PacketPlayOutScoreboardTeam(team, 2) // 2 = update
    }

    override fun removeTeam(teamName: String) {
        val scoreboard = Scoreboard()
        val team = ScoreboardTeam(scoreboard, teamName)
        packet = PacketPlayOutScoreboardTeam(team, 1) // 1 = remove
    }

    override fun send(player: Player) {
        val craftPlayer = player as CraftPlayer
        packet?.let { craftPlayer.handle.playerConnection.sendPacket(it) }
    }

    // Optional extras for dynamic add/remove players
    fun addPlayers(teamName: String, players: Collection<String>) {
        val scoreboard = Scoreboard()
        val team = ScoreboardTeam(scoreboard, teamName)
        team.playerNameSet.addAll(players)
        packet = PacketPlayOutScoreboardTeam(team, 3) // 3 = add players
    }

    fun removePlayers(teamName: String, players: Collection<String>) {
        val scoreboard = Scoreboard()
        val team = ScoreboardTeam(scoreboard, teamName)
        team.playerNameSet.addAll(players)
        packet = PacketPlayOutScoreboardTeam(team, 4) // 4 = remove players
    }
}