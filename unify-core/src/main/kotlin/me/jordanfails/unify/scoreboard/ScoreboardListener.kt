package me.jordanfails.unify.scoreboard

import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.npc.NPCRegistry
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

object ScoreboardListener : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        if (NPCRegistry.isNpc(event.player)) return
        Bukkit.getScheduler().runTaskLater(UnifyCore.instance, Runnable {
            val player = event.player
            if (player.isOnline && !NPCRegistry.isNpc(player)) {
                ScoreboardHandler.initiatePlayer(player)
            }
        }, 10L)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        if (NPCRegistry.isNpc(event.player)) return
        ScoreboardHandler.removePlayer(event.player)
    }
}
