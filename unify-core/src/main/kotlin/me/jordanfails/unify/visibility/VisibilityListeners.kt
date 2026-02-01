package me.jordanfails.unify.visibility

import org.apache.commons.lang.StringUtils
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChatTabCompleteEvent
import org.bukkit.event.player.PlayerJoinEvent

object VisibilityListeners : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        VisibilityHandler.update(event.player)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onTabComplete(event: PlayerChatTabCompleteEvent) {
        val token = event.lastToken
        val completions = event.tabCompletions
        completions.clear()

        for (target in Bukkit.getOnlinePlayers()) {
            if (!VisibilityHandler.treatAsOnline(target, event.player)) {
                continue
            }

            if (!StringUtils.startsWithIgnoreCase(target.name, token)) {
                continue
            }

            completions.add(target.name)
        }
    }

}