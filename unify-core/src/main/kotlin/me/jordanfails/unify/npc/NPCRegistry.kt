package me.jordanfails.unify.npc

import me.jordanfails.unify.module.UnifyModule
import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.npc.event.NPCDespawnEvent
import me.jordanfails.unify.npc.trait.ProtectedTrait
import me.jordanfails.unify.npc.trait.Trait
import me.jordanfails.unify.npc.trait.TraitRegistry
import org.bukkit.Bukkit
import org.bukkit.Location
import me.jordanfails.unify.plugin.owningPlugin
import org.bukkit.plugin.Plugin
import org.bukkit.configuration.ConfigurationSection
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
import org.bukkit.event.world.WorldLoadEvent
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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

    // Set while npcs.yml can't be read; writing then would replace the operator's data with what loaded.
    private var readOnly = false

    // Raw records for NPCs that couldn't load (usually their world isn't loaded yet), keyed by id.
    private val unloaded = LinkedHashMap<String, Map<String, Any?>>()

    internal fun enable(plugin: UnifyCore) {
        this.plugin = plugin
        this.dataFile = File(plugin.dataFolder, "npcs.yml")

        // Traits must be resolvable before any NPC is read off disk.
        TraitRegistry.registerBuiltins()

        Bukkit.getPluginManager().registerEvents(this, plugin)
        load()

        Bukkit.getScheduler().runTaskTimer(plugin, Runnable { tick() }, TICK_INTERVAL, TICK_INTERVAL)
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable { flush() }, FLUSH_INTERVAL, FLUSH_INTERVAL)
    }

    internal fun disable() {
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
        UnifyModule.NPCS.require()
        // A dot would split the id into nested YAML keys and the NPC wouldn't load back.
        require('.' !in id) { "NPC ids can't contain '.': $id" }
        val key = id.lowercase()
        if (npcs.containsKey(key) || unloaded.containsKey(key)) return null

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
        if (!UnifyModule.NPCS.allowsRegistration(action)) return false
        val npc = get(id) ?: return false
        npc.runtimeAction = action
        return true
    }

    fun unregisterAction(id: String) {
        get(id)?.runtimeAction = null
    }

    // Only click actions are dropped. Traits stay attached so Unify's shutdown save still writes their data.
    internal fun forgetPlugin(plugin: Plugin) {
        for (npc in npcs.values) {
            if (npc.runtimeAction?.let(::owningPlugin) === plugin) npc.runtimeAction = null
        }
    }

    /**
     * Attaches saved [name] traits that were waiting for their plugin to register the type. The saved
     * data is only dropped once the trait loaded and attached, so a failing trait can't erase it.
     */
    internal fun adoptPendingTraits(name: String) {
        val key = name.lowercase()
        for (npc in npcs.values) {
            val data = npc.pendingTraits[key] ?: continue
            try {
                val trait = TraitRegistry.create(name) ?: return
                trait.load(YamlConfiguration().createSection("trait", data))
                npc.addTrait(trait)
                npc.pendingTraits.remove(key)
            } catch (error: Exception) {
                plugin.logger.warning("Trait '$name' on NPC '${npc.id}' failed to load; keeping its saved data: ${error.message}")
            }
        }
    }

    // Nested sections become nested maps so createSection(path, map) can write them back verbatim.
    private fun ConfigurationSection.toMap(): Map<String, Any?> =
        getValues(false).mapValues { (_, value) -> if (value is ConfigurationSection) value.toMap() else value }

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

    /**
     * Writes `npcs.yml` if anything changed since the last write. Goes through a temp file so a failed
     * write never truncates the real one, and stays dirty on failure so the next flush retries.
     */
    fun flush() {
        if (!dirty || readOnly) return

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
                npc.pendingTraits.forEach { (traitName, data) -> config.createSection("$path.traits.$traitName", data) }
            }
            // NPCs whose world isn't loaded are written back exactly as they were read.
            unloaded.forEach { (id, data) -> config.createSection("npcs.$id", data) }

            val temp = File(dataFile.parentFile, "${dataFile.name}.tmp")
            config.save(temp)
            Files.move(temp.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            dirty = false
        } catch (e: Exception) {
            plugin.logger.warning("Failed to save NPCs: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Reloads every NPC from disk. Pending in-game edits are saved first, and if the file can't be read
     * (a YAML typo, or the old pre-trait format) the NPCs in memory are kept and nothing is written
     * until it's fixed, so a mistake in the file can never wipe it.
     */
    fun load() {
        flush()
        val config = YamlConfiguration()
        if (dataFile.exists()) {
            try {
                config.load(dataFile)
            } catch (e: Exception) {
                readOnly = true
                plugin.logger.severe("npcs.yml couldn't be read, so NPCs were not reloaded and won't be saved until it's fixed: ${e.message}")
                return
            }
        }
        if (!config.contains("version") && config.contains("npcs")) {
            readOnly = true
            plugin.logger.warning(
                "npcs.yml is in the old pre-trait format and will not be loaded or overwritten. " +
                    "Move it aside and recreate your NPCs, or delete it to silence this."
            )
            return
        }
        readOnly = false

        // Click handlers are runtime-only; keep them across a reload instead of silently dropping them.
        val actions = npcs.mapValues { it.value.runtimeAction }
        npcs.values.forEach { it.despawn(NPCDespawnEvent.Reason.REQUESTED) }
        npcs.clear()
        entityLookup.clear()
        unloaded.clear()

        val section = config.getConfigurationSection("npcs")
        for (id in section?.getKeys(false).orEmpty()) {
            val loaded = runCatching { loadNpc(config, id) }
                .onFailure { plugin.logger.warning("Failed to load NPC '$id': ${it.message}") }
                .getOrDefault(false)
            // Kept verbatim so the next save doesn't drop an NPC just because it couldn't load right now.
            if (!loaded) section?.getConfigurationSection(id)?.let { unloaded[id] = it.toMap() }
        }
        actions.forEach { (id, action) -> npcs[id]?.runtimeAction = action }
        plugin.logger.info("Loaded ${npcs.size} NPCs" + if (unloaded.isEmpty()) "" else " (${unloaded.size} waiting for their world)")
    }

    // Worlds loaded after Unify (Multiverse and friends) bring their NPCs with them.
    @EventHandler
    fun onWorldLoad(event: WorldLoadEvent) {
        for ((id, data) in unloaded.filterValues { (it["location"] as? Map<*, *>)?.get("world") == event.world.name }) {
            val config = YamlConfiguration().apply { createSection("npcs.$id", data) }
            if (runCatching { loadNpc(config, id) }.getOrDefault(false)) unloaded.remove(id)
        }
    }

    /** Returns false when the NPC's world isn't loaded yet. */
    private fun loadNpc(config: YamlConfiguration, id: String): Boolean {
        val path = "npcs.$id"

        val worldName = config.getString("$path.location.world") ?: return false
        val world = Bukkit.getWorld(worldName) ?: return false

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
                val traitSection = traitsSection.getConfigurationSection(traitName) ?: continue
                val trait = TraitRegistry.create(traitName)
                if (trait == null) {
                    // Often a plugin that registers this trait just hasn't enabled yet.
                    npc.pendingTraits[traitName.lowercase()] = traitSection.toMap()
                    continue
                }
                runCatching { trait.load(traitSection) }
                    .onFailure { plugin.logger.warning("Trait '$traitName' on NPC '$id' failed to load: ${it.message}") }
                npc.addTrait(trait)
            }
        }

        npcs[id.lowercase()] = npc
        npc.spawn()
        return true
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
