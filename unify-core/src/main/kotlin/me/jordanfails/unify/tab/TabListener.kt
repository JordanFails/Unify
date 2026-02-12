package me.jordanfails.unify.tab

import me.jordanfails.unify.UnifyCore
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

object TabListener : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        Bukkit.getScheduler().runTaskLater(UnifyCore.instance, Runnable {
            if (event.player.isOnline) {
                TabHandler.sendTab(event.player)
            }
        }, 10L)
    }
}
