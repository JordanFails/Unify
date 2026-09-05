package me.jordanfails.unify.npc.event

import me.jordanfails.unify.npc.UnifyNPC
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Base for every NPC event.
 *
 * Each concrete event declares its own [HandlerList] rather than sharing one on this class —
 * Bukkit keys registered listeners by the list instance, so a shared list would deliver every
 * NPC event to every NPC listener regardless of the type it declared.
 */
abstract class NPCEvent(val npc: UnifyNPC) : Event()

/** Fired after an NPC's body has been created and its traits have run `onSpawn`. */
class NPCSpawnEvent(npc: UnifyNPC) : NPCEvent(npc) {
    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}

/** Fired before an NPC's body is removed. Traits have not yet run `onDespawn`. */
class NPCDespawnEvent(npc: UnifyNPC, val reason: Reason) : NPCEvent(npc) {
    enum class Reason {
        /** Explicit despawn — `/npc despawn`, deletion, or plugin disable. */
        REQUESTED,

        /** The body is being rebuilt in place (entity type change, world unload/reload). */
        RESPAWN,

        /** The body vanished on its own and the registry noticed. Should be rare once chunks are pinned. */
        LOST,
    }

    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}

/**
 * Fired when a player right-clicks an NPC.
 *
 * Cancelling stops Unify's own handling (the command trait, registered [me.jordanfails.unify.npc.NPCAction]s);
 * the underlying Bukkit interact event is always cancelled regardless, so vanilla never sees the click.
 */
class NPCRightClickEvent(npc: UnifyNPC, val clicker: Player) : NPCEvent(npc), Cancellable {
    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}

/** Fired when a player left-clicks (attacks) an NPC. Damage is always cancelled. */
class NPCLeftClickEvent(npc: UnifyNPC, val clicker: Player) : NPCEvent(npc), Cancellable {
    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
