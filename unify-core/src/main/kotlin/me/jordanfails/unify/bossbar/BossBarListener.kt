package me.jordanfails.unify.bossbar

import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.npc.NPCRegistry
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

object BossBarListener : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        if (NPCRegistry.isNpc(event.player)) return
        Bukkit.getScheduler().runTaskLater(UnifyCore.instance, Runnable {
            val player = event.player
            if (!player.isOnline || NPCRegistry.isNpc(player)) return@Runnable
            BossBarHandler.initiatePlayer(player)
            BossBarManager.handleScopeChange(player)
        }, 10L)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        BossBarHandler.removePlayer(event.player)
        BossBarManager.handleQuit(event.player)
    }

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        if (NPCRegistry.isNpc(event.player)) return
        BossBarManager.handleScopeChange(event.player)
    }
}
