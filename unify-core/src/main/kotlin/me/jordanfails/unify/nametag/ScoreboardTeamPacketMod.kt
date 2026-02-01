package me.jordanfails.unify.nametag

import me.jordanfails.unify.UnifyCore
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * A wrapper class for sending scoreboard team packets for nametag modifications.
 * Uses the NMS handler methods for cross-version compatibility.
 * 
 * @param name Team name (max 16 chars)
 * @param prefix Prefix to show before player name
 * @param suffix Suffix to show after player name
 * @param players List of player names to add to team
 * @param mode Packet mode: 0 = create, 1 = remove, 2 = update, 3 = add players, 4 = remove players
 */
class ScoreboardTeamPacketMod(
    val name: String,
    val prefix: String,
    val suffix: String,
    val players: List<String>,
    val mode: Int
) {
    constructor(name: String, players: List<String>, mode: Int) : this(name, "", "", players, mode)

    /**
     * Sends this packet to a specific player.
     */
    fun send(viewer: Player) {
        val nms = UnifyCore.instance.nms ?: return
        
        when (mode) {
            0, 2 -> {
                // Create or update team with prefix/suffix
                for (playerName in players) {
                    val target = Bukkit.getPlayerExact(playerName) ?: continue
                    nms.sendNametagPacket(viewer, target, name, prefix, suffix)
                }
            }
            1 -> {
                // Remove team
                for (playerName in players) {
                    val target = Bukkit.getPlayerExact(playerName) ?: continue
                    nms.sendRemoveNametagTeamPacket(viewer, target)
                }
            }
            3 -> {
                // Add players to team (same as create but for adding)
                for (playerName in players) {
                    val target = Bukkit.getPlayerExact(playerName) ?: continue
                    nms.sendNametagPacket(viewer, target, name, prefix, suffix)
                }
            }
            4 -> {
                // Remove players from team
                for (playerName in players) {
                    val target = Bukkit.getPlayerExact(playerName) ?: continue
                    nms.sendRemoveNametagTeamPacket(viewer, target)
                }
            }
        }
    }

    /**
     * Sends this packet to all online players.
     */
    fun sendToAll() {
        for (player in Bukkit.getOnlinePlayers()) {
            send(player)
        }
    }
}
