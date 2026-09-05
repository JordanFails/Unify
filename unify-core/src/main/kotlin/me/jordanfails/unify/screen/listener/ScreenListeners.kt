package me.jordanfails.unify.screen.listener

import me.jordanfails.unify.screen.Screens
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

object ScreenListeners : Listener {

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        Screens.forget(event.player)
    }
}
