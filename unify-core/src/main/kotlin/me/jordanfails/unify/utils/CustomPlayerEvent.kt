package me.jordanfails.unify.utils

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.*

class CustomPlayerEvent : Event, Cancellable {
    var player: Player? = null
    var uuid: UUID? = null
    var eventKey: String? = null
    var eventData: Array<out Any>? = null

    private var cancelled = false

    constructor(player: Player, eventKey: String, vararg data: Any) {
        this.player = player
        this.eventKey = eventKey
        this.uuid = player.uniqueId
        this.eventData = data
    }

    constructor(uuid: UUID, eventKey: String, vararg data: Any) {
        this.uuid = uuid
        this.eventKey = eventKey
        this.eventData = data
    }

    override fun isCancelled(): Boolean {
        return cancelled
    }

    override fun setCancelled(cancelled: Boolean) {
        this.cancelled = cancelled
    }

    override fun getHandlers(): HandlerList {
        return handlerList
    }

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}