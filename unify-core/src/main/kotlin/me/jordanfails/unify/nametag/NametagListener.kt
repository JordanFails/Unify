package me.jordanfails.unify.nametag

import me.jordanfails.unify.UnifyCore
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.metadata.MetadataValue
import org.bukkit.event.player.PlayerJoinEvent

object NametagListener : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        event.player.setMetadata("Nametag-Applied", FixedMetadataValue(UnifyCore.instance, true) as MetadataValue)
        NametagHandler.initiatePlayer(event.player)
        NametagHandler.reloadPlayer(event.player)
        NametagHandler.reloadOthersFor(event.player)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        event.player.removeMetadata("Nametag-Applied", UnifyCore.instance)
        NametagHandler.teamMap.remove(event.player.uniqueId)
    }

}