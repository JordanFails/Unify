package me.jordanfails.unify.npc.command

import me.jordanfails.unify.npc.NPCRegistry
import me.jordanfails.unify.npc.UnifyNPC
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks which NPC each player is editing.
 *
 * Citizens' selection model: pick an NPC once, then every subsequent command applies to it. Trait
 * commands would otherwise need to repeat the NPC id on every invocation, which gets unwieldy fast
 * once commands take their own arguments too.
 *
 * Selections are per-session and deliberately not persisted.
 */
object NPCSelection {

    private val selections = ConcurrentHashMap<UUID, String>()

    fun select(player: Player, npc: UnifyNPC) {
        selections[player.uniqueId] = npc.id
    }

    /**
     * The player's selected NPC, or null if they have none — or if the one they had was deleted
     * since, which is why this resolves through the registry every time rather than caching.
     */
    fun selected(player: Player): UnifyNPC? {
        val id = selections[player.uniqueId] ?: return null
        val npc = NPCRegistry.get(id)
        if (npc == null) selections.remove(player.uniqueId)
        return npc
    }

    fun clear(player: Player) {
        selections.remove(player.uniqueId)
    }
}
