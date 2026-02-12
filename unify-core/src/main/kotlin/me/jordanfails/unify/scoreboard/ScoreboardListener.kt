package me.jordanfails.unify.scoreboard

import me.jordanfails.unify.UnifyCore
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

object ScoreboardListener : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        Bukkit.getScheduler().runTaskLater(UnifyCore.instance, Runnable {
            if (event.player.isOnline) {
                ScoreboardHandler.initiatePlayer(event.player)
            }
        }, 10L)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        ScoreboardHandler.removePlayer(event.player)
    }
}
