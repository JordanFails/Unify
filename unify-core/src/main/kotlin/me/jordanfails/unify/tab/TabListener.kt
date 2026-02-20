package me.jordanfails.unify.tab

import me.jordanfails.unify.UnifyCore
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

object TabListener : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        Bukkit.getScheduler().runTaskLater(UnifyCore.instance, Runnable {
            if (event.player.isOnline) {
                TabHandler.initiatePlayer(event.player)
            }
        }, 10L)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        TabHandler.removePlayer(event.player)
    }
}
