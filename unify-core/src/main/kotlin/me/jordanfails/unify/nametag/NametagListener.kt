package me.jordanfails.unify.nametag

import me.jordanfails.unify.UnifyCore
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerRespawnEvent

object NametagListener : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        NametagHandler.addViewer(event.player)
        NametagHandler.reloadPlayer(event.player)
        NametagHandler.reloadOthersFor(event.player)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        NametagLines.removePlayer(event.player)
        NametagHandler.removeViewer(event.player)
    }

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        refreshLines(event.player)
    }

    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        refreshLines(event.player)
    }

    private fun refreshLines(player: Player) {
        NametagLines.removePlayer(player)
        val plugin = UnifyCore.instance
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (!player.isOnline) return@Runnable
            NametagHandler.reloadPlayer(player)
            NametagHandler.reloadOthersFor(player)
        }, 2L)
    }

}
