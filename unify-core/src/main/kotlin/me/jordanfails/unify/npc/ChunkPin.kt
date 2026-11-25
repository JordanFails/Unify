package me.jordanfails.unify.npc

import me.jordanfails.unify.UnifyCore
import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkUnloadEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps the chunks NPC bodies live in permanently loaded.
 *
 * This is what lets the NPC package stop policing its own entities. Previously a chunk unload
 * silently discarded an NPC body, and the only way to notice was to re-check every NPC against
 * every player on every [org.bukkit.event.player.PlayerMoveEvent] — O(players x NPCs) per movement
 * tick — plus a ChunkLoadEvent rebuild that raced it. Pin the chunk and the body simply never goes
 * away, so there is nothing to detect and nothing to rebuild.
 *
 * Three mechanisms, best first, because the API landed in stages:
 *  - `World#addPluginChunkTicket` (1.13.2+) — refcounted per plugin, released cleanly on disable.
 *  - `Chunk#setForceLoaded` (1.13+) — global flag, persists in level data, so we must unset it.
 *  - Cancelling [ChunkUnloadEvent] (1.8-1.12, where the event is still cancellable).
 */
object ChunkPin : Listener {

    /**
     * Which NPCs hold each chunk (`world|x|z`). Bukkit gives a plugin one ticket per chunk, not one
     * per NPC, so the ticket is only taken for the first holder and released after the last.
     */
    private val holders = ConcurrentHashMap<String, MutableSet<String>>()
    private val chunkOf = ConcurrentHashMap<String, String>()

    private var legacyListenerRegistered = false

    private val addPluginChunkTicket = runCatching {
        World::class.java.getMethod("addPluginChunkTicket", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, org.bukkit.plugin.Plugin::class.java)
    }.getOrNull()

    private val removePluginChunkTicket = runCatching {
        World::class.java.getMethod("removePluginChunkTicket", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, org.bukkit.plugin.Plugin::class.java)
    }.getOrNull()

    private val setForceLoaded = runCatching {
        Chunk::class.java.getMethod("setForceLoaded", Boolean::class.javaPrimitiveType)
    }.getOrNull()

    private fun key(world: World, x: Int, z: Int) = "${world.name}|$x|$z"

    /**
     * Holds the chunk containing [location] loaded on behalf of NPC [owner], moving the hold if that
     * NPC held a different chunk. Pinning the same chunk again is a no-op.
     */
    fun pin(owner: String, location: Location) {
        val world = location.world ?: return
        val x = location.blockX shr 4
        val z = location.blockZ shr 4
        val key = key(world, x, z)
        if (chunkOf[owner] == key) return
        unpin(owner)

        if (!world.isChunkLoaded(x, z)) {
            world.loadChunk(x, z, true)
        }
        chunkOf[owner] = key
        val first = holders.getOrPut(key) { ConcurrentHashMap.newKeySet() }.let { it.add(owner); it.size == 1 }
        if (!first) return

        // Bound to locals so Kotlin can smart-cast them; object properties are not stable for that.
        val ticket = addPluginChunkTicket
        val force = setForceLoaded

        when {
            ticket != null -> runCatching { ticket.invoke(world, x, z, UnifyCore.instance) }
            force != null -> runCatching { force.invoke(world.getChunkAt(x, z), true) }
            else -> registerLegacyListener()
        }
    }

    /** Drops [owner]'s hold. Safe to call when it holds nothing, or twice. */
    fun unpin(owner: String) {
        val key = chunkOf.remove(owner) ?: return
        val remaining = holders[key] ?: return
        remaining.remove(owner)
        if (remaining.isNotEmpty()) return
        holders.remove(key)
        release(key)
    }

    private fun release(key: String) {
        val parts = key.split('|')
        val world = org.bukkit.Bukkit.getWorld(parts[0]) ?: return
        val x = parts[1].toInt()
        val z = parts[2].toInt()

        val ticket = removePluginChunkTicket
        val force = setForceLoaded

        when {
            ticket != null -> runCatching { ticket.invoke(world, x, z, UnifyCore.instance) }

            // Unlike plugin tickets this flag is global and written into the world's level data,
            // so leaving it set would keep the chunk loaded forever, across restarts.
            force != null -> runCatching { force.invoke(world.getChunkAt(x, z), false) }
        }
    }

    /** Releases every pin. Called on plugin disable so no chunk stays forced past our lifetime. */
    fun releaseAll() {
        holders.keys.toList().forEach(::release)
        holders.clear()
        chunkOf.clear()
    }

    private fun registerLegacyListener() {
        if (legacyListenerRegistered) return
        legacyListenerRegistered = true
        org.bukkit.Bukkit.getPluginManager().registerEvents(this, UnifyCore.instance)
    }

    /**
     * 1.8-1.12 fallback. [ChunkUnloadEvent] stopped being cancellable in 1.14, but on the versions
     * that reach this listener it still is — reflection keeps this compiling against either API.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onChunkUnload(event: ChunkUnloadEvent) {
        val chunk = event.chunk
        if (!holders.containsKey(key(chunk.world, chunk.x, chunk.z))) return
        runCatching {
            event.javaClass.getMethod("setCancelled", Boolean::class.javaPrimitiveType).invoke(event, true)
        }
    }
}
