package me.jordanfails.unify.utils

import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.event.Cancellable

open class BukkitEvent : Event(), Cancellable {

    private var cancelled: Boolean = false

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }

    override fun getHandlers(): HandlerList = handlerList

    // Cancellable implementation
    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancel: Boolean) {
        this.cancelled = cancel
    }

    /**
     * Helper function to call the event easily
     */
    fun call(): BukkitEvent {
        Bukkit.getPluginManager().callEvent(this)
        return this
    }
}