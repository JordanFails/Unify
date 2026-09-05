package me.jordanfails.unify.npc

import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.npc.event.NPCDespawnEvent
import me.jordanfails.unify.npc.trait.ProtectedTrait
import me.jordanfails.unify.npc.trait.Trait
import me.jordanfails.unify.npc.trait.TraitRegistry
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns every NPC: creation, lookup, persistence, and the events that drive them.
 *
 * Deliberately much smaller than the NPCManager it replaces. Because NPC bodies now live in pinned
 * chunks as ordinary world entities, the registry no longer has to hunt for bodies that vanished:
 * the per-move NPC scan, the chunk-load rebuild, and the pre-login interception are all gone, and
 * with them the reason those existed.
 */
object NPCRegistry : Listener {

    /**
     * Bukkit metadata key set on every NPC body. Other plugins should skip profile and economy
     * work for entities carrying it.
     */
    const val NPC_METADATA_KEY = "UNIFY_NPC_ID"

    /**
     * Ecosystem convention (Citizens et al.), set alongside [NPC_METADATA_KEY] so third-party
     * plugins that only check for `"NPC"` also ignore our bodies.
     */
    const val LEGACY_NPC_METADATA_KEY = "NPC"

    private val npcs = ConcurrentHashMap<String, UnifyNPC>()

    /** Live body UUID to owning NPC. Rebuilt on every spawn; empty entries mean despawned NPCs. */
    private val entityLookup = ConcurrentHashMap<UUID, UnifyNPC>()

    private lateinit var plugin: UnifyCore
    private lateinit var dataFile: File

    /**
     * Set by any mutation that needs persisting; flushed on a timer instead of immediately.
     *
     * Trait setters call [save] freely, and several of them fire on a tick loop. Writing the whole
     * file synchronously from each one would put a main-thread file write in the hot path.
     */
    @Volatile
    private var dirty = false

    fun enable(plugin: UnifyCore) {
        this.plugin = plugin
        this.dataFile = File(plugin.dataFolder, "npcs.yml")

        // Traits must be resolvable before any NPC is read off disk.
        TraitRegistry.registerBuiltins()

        Bukkit.getPluginManager().registerEvents(this, plugin)
        load()

        Bukkit.getScheduler().runTaskTimer(plugin, Runnable { tick() }, TICK_INTERVAL, TICK_INTERVAL)
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable { flush() }, FLUSH_INTERVAL, FLUSH_INTERVAL)
    }

    fun disable() {
        flush()
        npcs.values.forEach { it.despawn(NPCDespawnEvent.Reason.REQUESTED) }
        npcs.clear()
        entityLookup.clear()
        ChunkPin.releaseAll()
    }

    // ── Lookup ──────────────────────────────────────────────────────────────

    fun get(id: String): UnifyNPC? = npcs[id.lowercase()]

    fun exists(id: String): Boolean = npcs.containsKey(id.lowercase())

    fun getIds(): Set<String> = npcs.keys.toSet()

    fun getAll(): Map<String, UnifyNPC> = npcs.toMap()

    /** The NPC whose live body is [entity], or null if it is not one of ours. */
    fun getByEntity(entity: Entity): UnifyNPC? = entityLookup[entity.uniqueId]

    /** True when [entity] is an NPC body. */
    fun isNpc(entity: Entity): Boolean =
        entityLookup.containsKey(entity.uniqueId) ||
            entity.hasMetadata(NPC_METADATA_KEY) ||
            entity.hasMetadata(LEGACY_NPC_METADATA_KEY)

    fun isNpc(uuid: UUID): Boolean = entityLookup.containsKey(uuid)

    /**
     * Kept so existing callers still compile and so third-party plugins have a cheap guard.
     *
     * Always false in practice now: NPC bodies are world entities and never enter the server's
     * player list, which is the whole reason the old synthetic-name and pre-login detection could
     * be deleted. It stays as a safety net for anything spawning player bodies another way.
     */
    fun isNpc(player: Player): Boolean = isNpc(player as Entity)

    /**
     * Every online player. NPC bodies are no longer in this list, so this is now just
     * [Bukkit.getOnlinePlayers] — retained because callers across the plugin already use it and it
     * documents the intent at each call site.
     */
    fun realOnlinePlayers(): Collection<Player> = Bukkit.getOnlinePlayers().filterNot { isNpc(it) }

    // ── Mutation ────────────────────────────────────────────────────────────

    /**
     * Creates and spawns an NPC.
     *
     * Returns null when [id] is taken. An NPC whose body fails to spawn is still returned and
     * still registered — its configuration is intact and it can be respawned once the cause is
     * fixed, which beats silently discarding it.
     */
    fun create(id: String, location: Location, entityType: EntityType = EntityType.PLAYER): UnifyNPC? {
        val key = id.lowercase()
        if (npcs.containsKey(key)) return null

        val npc = UnifyNPC(key, UUID.randomUUID(), entityType, location)
        npcs[key] = npc
        npc.spawn()
        save()
        return npc
    }

    fun delete(id: String): Boolean {
        val npc = npcs.remove(id.lowercase()) ?: return false
        npc.despawn(NPCDespawnEvent.Reason.REQUESTED)
        npc.traits().forEach { npc.removeTrait(it.javaClass) }
        save()
        return true
    }

    /** Respawns every NPC — used by `/unify reload` after configuration changes. */
    fun respawnAll() {
        npcs.values.forEach { it.spawn() }
    }

    /** Registers a runtime-only click handler for [id]. Not persisted. */
    fun registerAction(id: String, action: NPCAction): Boolean {
        val npc = get(id) ?: return false
        npc.runtimeAction = action
        return true
    }

    fun unregisterAction(id: String) {
        get(id)?.runtimeAction = null
    }

    // ── Body binding, called by UnifyNPC ────────────────────────────────────

    internal fun bindEntity(entityUuid: UUID, npc: UnifyNPC) {
        entityLookup.entries.removeIf { it.value === npc }
        entityLookup[entityUuid] = npc
    }

    internal fun unbindEntity(entityUuid: UUID) {
        entityLookup.remove(entityUuid)
    }

    // ── Ticking ─────────────────────────────────────────────────────────────

    private fun tick() {
        for (npc in npcs.values) {
            if (npc.entityUuid == null) continue
            npc.tick()
        }
    }

    // ── Persistence ─────────────────────────────────────────────────────────

    /** Marks the registry as needing a save. The write happens on the next flush tick. */
    fun save() {
        dirty = true
    }

    /** Writes `npcs.yml` if anything changed since the last write. */
    fun flush() {
        if (!dirty) return
        dirty = false

        try {
            dataFile.parentFile?.mkdirs()

            val config = YamlConfiguration()
            config.set("version", STORAGE_VERSION)

            npcs.forEach { (id, npc) ->
                val path = "npcs.$id"
                config.set("$path.uuid", npc.uuid.toString())
                config.set("$path.entity-type", npc.entityType.name)

                val location = npc.location
                config.set("$path.location.world", location.world?.name)
                config.set("$path.location.x", location.x)
                config.set("$path.location.y", location.y)
                config.set("$path.location.z", location.z)
                config.set("$path.location.yaw", location.yaw.toDouble())
                config.set("$path.location.pitch", location.pitch.toDouble())

                npc.traits().forEach { trait ->
                    val traitName = TraitRegistry.nameOf(trait.javaClass)
                    if (traitName == null) {
                        // Unregistered traits cannot be named on disk, so they cannot be loaded
                        // back. Warn rather than write a section nothing will ever read.
                        plugin.logger.warning(
                            "Trait ${trait.javaClass.name} on NPC '$id' is not registered; not saving it."
                        )
                        return@forEach
                    }
                    val section = config.createSection("$path.traits.$traitName")
                    runCatching { trait.save(section) }
                        .onFailure { plugin.logger.warning("Trait '$traitName' on NPC '$id' failed to save: ${it.message}") }
                }
            }

            config.save(dataFile)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to save NPCs: ${e.message}")
            e.printStackTrace()
        }
    }

    /** Reloads every NPC from disk, replacing what is in memory. */
    fun load() {
        npcs.values.forEach { it.despawn(NPCDespawnEvent.Reason.REQUESTED) }
        npcs.clear()
        entityLookup.clear()

        try {
            if (!dataFile.exists()) return

            val config = YamlConfiguration.loadConfiguration(dataFile)

            // The pre-traits format has no version key and cannot be read by this code. Say so
            // once, clearly, instead of loading zero NPCs and looking like nothing happened.
            if (!config.contains("version") && config.contains("npcs")) {
                plugin.logger.warning(
                    "npcs.yml is in the old pre-trait format and will not be loaded. " +
                        "Move it aside and recreate your NPCs, or delete it to silence this."
                )
                return
            }

            val section = config.getConfigurationSection("npcs") ?: return

            for (id in section.getKeys(false)) {
                runCatching { loadNpc(config, id) }
                    .onFailure { plugin.logger.warning("Failed to load NPC '$id': ${it.message}") }
            }

            plugin.logger.info("Loaded ${npcs.size} NPCs")
        } catch (e: Exception) {
            plugin.logger.warning("Failed to load NPCs: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun loadNpc(config: YamlConfiguration, id: String) {
        val path = "npcs.$id"

        val worldName = config.getString("$path.location.world") ?: return
        val world = Bukkit.getWorld(worldName)
        if (world == null) {
            plugin.logger.warning("NPC '$id' lives in unloaded world '$worldName'; skipping it.")
            return
        }

        val location = Location(
            world,
            config.getDouble("$path.location.x"),
            config.getDouble("$path.location.y"),
            config.getDouble("$path.location.z"),
            config.getDouble("$path.location.yaw").toFloat(),
            config.getDouble("$path.location.pitch").toFloat(),
        )

        val entityType = config.getString("$path.entity-type")
            ?.let { runCatching { EntityType.valueOf(it.uppercase()) }.getOrNull() }
            ?: EntityType.PLAYER

        val uuid = config.getString("$path.uuid")
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: UUID.randomUUID()

        val npc = UnifyNPC(id.lowercase(), uuid, entityType, location)

        // Traits are loaded and attached before the first spawn so the spawn spec already carries
        // skin, name and pose — otherwise the NPC appears in its default form for a tick.
        config.getConfigurationSection("$path.traits")?.let { traitsSection ->
            for (traitName in traitsSection.getKeys(false)) {
                val trait = TraitRegistry.create(traitName)
                if (trait == null) {
                    plugin.logger.warning("NPC '$id' uses unknown trait '$traitName'; skipping it.")
                    continue
                }
                val traitSection = traitsSection.getConfigurationSection(traitName) ?: continue
                runCatching { trait.load(traitSection) }
                    .onFailure { plugin.logger.warning("Trait '$traitName' on NPC '$id' failed to load: ${it.message}") }
                npc.addTrait(trait)
            }
        }

        npcs[id.lowercase()] = npc
        npc.spawn()
    }

    // ── Events ──────────────────────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEntityEvent) {
        val npc = entityLookup[event.rightClicked.uniqueId] ?: return
        event.isCancelled = true
        npc.handleRightClick(event.player)
    }

    @EventHandler(ignoreCancelled = true)
    fun onInteractAt(event: PlayerInteractAtEntityEvent) {
        val npc = entityLookup[event.rightClicked.uniqueId] ?: return
        event.isCancelled = true
        npc.handleRightClick(event.player)
    }

    /**
     * Blocks damage to protected NPCs. Unprotected ones take damage normally, which is the point
     * of [ProtectedTrait] being toggleable.
     */
    @EventHandler(ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        val npc = entityLookup[event.entity.uniqueId] ?: return
        if (npc.getTrait(ProtectedTrait::class.java)?.isProtected != false) {
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onDamageByEntity(event: EntityDamageByEntityEvent) {
        val npc = entityLookup[event.entity.uniqueId] ?: return
        val player = event.damager as? Player ?: return

        if (npc.getTrait(ProtectedTrait::class.java)?.isProtected != false) {
            event.isCancelled = true
        }
        npc.handleLeftClick(player)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        // The entity tracker sends the bodies; only traits with their own per-viewer state need
        // telling. Delayed a few ticks so the client has finished loading its chunks first.
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (!player.isOnline) return@Runnable
            npcs.values.forEach { it.updateViewer(player, it.location.world == player.world) }
        }, VIEWER_SYNC_DELAY)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        npcs.values.forEach { it.updateViewer(event.player, false) }
    }

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        val player = event.player
        npcs.values.forEach { it.updateViewer(player, it.location.world == player.world) }
    }

    private const val STORAGE_VERSION = 2

    /** Trait tick rate. Look-close at 10 Hz is smooth enough and a fifth the cost of every tick. */
    private const val TICK_INTERVAL = 2L

    /** How often dirty state reaches disk, in ticks (15 seconds). */
    private const val FLUSH_INTERVAL = 300L

    private const val VIEWER_SYNC_DELAY = 5L
}
