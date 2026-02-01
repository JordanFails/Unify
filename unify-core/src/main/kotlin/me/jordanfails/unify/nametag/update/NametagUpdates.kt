package me.jordanfails.unify.nametag.update

import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.utils.Tasks
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.NameTagVisibility
import kotlin.random.Random

class NametagUpdates(
    var prefix: String = "",
    var name: String = "",
    var suffix: String = "",
    var invisible: Boolean = true,
    var priority: Int = 1
) {

    init {
        assert(prefix.length <= 16) { "Prefix is too long! (${prefix.length} > 16)" }
        assert(name.length <= 16) { "Name is too long! (${name.length} > 16)" }
        assert(suffix.length <= 16) { "Suffix is too long! (${suffix.length} > 16)" }
    }

    constructor(text: String = "", invisible: Boolean, priority: Int) : this("", "", "", invisible, priority) {
        if (text.isEmpty() && invisible) {
            name = generateName()
            return
        }

        assert(text.length in 0..48) { "Text is too long! (${text.length} > 48})" }

        if (text.length <= 16) {
            this.name = text
        } else {
            if (text.length <= 32) {
                this.prefix = text.substring(0, text.length - 16)

                if (this.prefix.endsWith(ChatColor.COLOR_CHAR)) {
                    this.prefix = "${this.prefix}${text.toCharArray()[text.length - 16]}"
                    this.name = text.substring(text.length - 15, text.length)
                } else {
                    this.name = text.substring(text.length - 16, text.length)
                }
            } else {
                this.prefix = text.substring(0, 15)
                this.name = text.substring(15, 31)
                this.suffix = text.substring(31, text.length)
            }
        }
    }

    private fun hasScoreboard(player: Player): Boolean {
        return player.scoreboard != null && player.scoreboard.getObjective(DisplaySlot.PLAYER_LIST) != null
    }

    private fun createScoreboard(player: Player) {
        val mainScoreboard = Bukkit.getScoreboardManager()?.mainScoreboard
        val scoreboard = if (mainScoreboard != null && player.scoreboard == mainScoreboard) {
            Bukkit.getScoreboardManager()?.newScoreboard ?: return
        } else {
            player.scoreboard
        }

        if (scoreboard.getObjective(DisplaySlot.PLAYER_LIST) == null) {
            try {
                val objective = scoreboard.registerNewObjective("NametagUpdates", "dummy")
                objective.displaySlot = DisplaySlot.PLAYER_LIST
            } catch (e: Exception) {
            }
        }

        if (player.scoreboard != scoreboard) {
            player.scoreboard = scoreboard
        }

        player.setMetadata("NAMETAG_UPDATES_SB", FixedMetadataValue(UnifyCore.instance, scoreboard))
    }

    fun enforceSendSync(player: Player, team: String) {
        if (Bukkit.isPrimaryThread()) {
            send(player, team)
        } else {
            Tasks.run {
                send(player, team)
            }
        }
    }

    fun send(player: Player, team: String) {
        if (!hasScoreboard(player)) {
            createScoreboard(player)
        }

        val priority = if (this.priority < 10) {
            "0${this.priority}"
        } else {
            this.priority.toString()
        }

        val teamName = getTeamName(priority + team)
        val scoreboard = player.scoreboard
        val scoreboardTeam = scoreboard.getTeam(teamName) ?: scoreboard.registerNewTeam(teamName)

        if (invisible) {
            scoreboardTeam.nameTagVisibility = NameTagVisibility.NEVER
        } else {
            scoreboardTeam.displayName = this.name
            scoreboardTeam.prefix = this.prefix
            scoreboardTeam.suffix = this.suffix
        }

        scoreboardTeam.addEntry(this.name)
    }

    fun enforceClearSync(player: Player, team: String) {
        if (Bukkit.isPrimaryThread()) {
            clear(player, team)
        } else {
            Tasks.run {
                clear(player, team)
            }
        }
    }

    fun clear(player: Player, team: String) {
        if (hasScoreboard(player)) {
            val priority = if (this.priority < 10) {
                "0${this.priority}"
            } else {
                this.priority.toString()
            }

            val teamName = getTeamName(priority + team)

            val scoreboardTeam = player.scoreboard.getTeam(teamName)
            if (scoreboardTeam != null) {
                val entries = scoreboardTeam.entries
                for (entry in entries) {
                    scoreboardTeam.removeEntry(entry)
                }

                try {
                    scoreboardTeam.unregister()
                } catch (ignored: java.lang.Exception) { // Skip, bukkit issue due to us running this method async, if we don't use a try catch system here the player gets kicked
                }
            }
        }
    }

    private fun getTeamName(team: String): String {
        return if (team.length > 16) {
            team.substring(0, 12)
        } else {
            team
        }
    }

    companion object {
        private val chars = arrayListOf('a', 'b', 'c', 'd', 'e', 'f', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9')

        private fun generateName(): String {
            val amountOfColors = Random.nextInt(1, 8)
            var name = ""
            for (i in 1..amountOfColors) {
                name = "${ChatColor.COLOR_CHAR}${chars[Random.nextInt(chars.size)]}"
            }
            return name
        }
    }

}