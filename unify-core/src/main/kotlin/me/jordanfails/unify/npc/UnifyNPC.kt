package me.jordanfails.unify.npc

import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.nms.NMSHandlerFactory
import me.jordanfails.unify.npc.event.NPCDespawnEvent
import me.jordanfails.unify.npc.event.NPCLeftClickEvent
import me.jordanfails.unify.npc.event.NPCRightClickEvent
import me.jordanfails.unify.npc.event.NPCSpawnEvent
import me.jordanfails.unify.npc.trait.NameTrait
import me.jordanfails.unify.npc.trait.PoseTrait
import me.jordanfails.unify.npc.trait.SkinTrait
import me.jordanfails.unify.npc.trait.Trait
import me.jordanfails.unify.npc.trait.TraitRegistry
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * A single NPC: an identity, a place in the world, and a bag of [Trait]s that give it behaviour.
 *
 * The NPC outlives its body. [uuid] and [id] are stable for the NPC's whole persisted life, while
 * [entityUuid] points at whichever entity currently represents it and changes on every respawn.
 * Anything that needs to remember an NPC across a restart must key on [id], never on [entityUuid].
 */
class UnifyNPC internal constructor(
    val id: String,
    val uuid: UUID,
    entityType: EntityType,
    location: Location,
) {

    /**
     * The entity type the body is built from. Changing it rebuilds the body, since no server
     * version can convert an entity in place.
     */
    var entityType: EntityType = entityType
        private set

    /** Where the body sits. Cloned on read and write so callers cannot mutate it behind our back. */
    var location: Location = location.clone()
        private set
        get() = field.clone()

    private val traits = ConcurrentHashMap<Class<out Trait>, Trait>()
    private val clickCooldowns = ConcurrentHashMap<UUID, Long>()

    /** UUID of the live body entity, or null while despawned. */
    @Volatile
    var entityUuid: UUID? = null
        private set

    /** Suppresses repeat spawn-failure warnings; reset as soon as a spawn succeeds. */
    private var spawnFailureLogged = false

    /** Runtime-only click handler, not persisted. Set by other plugins via [NPCRegistry.registerAction]. */
    @Volatile
    internal var runtimeAction: NPCAction? = null

    // ── Lifecycle ───────────────────────────────────────────────────────────

    /** True when a body exists. Since chunks are pinned, this only goes false via [despawn]. */
    fun isSpawned(): Boolean = entityUuid?.let { Bukkit.getEntity(it) != null } ?: false

    /**
     * Creates the body, pinning its chunk first so the entity cannot be discarded later.
     *
     * Despawns any existing body, so this doubles as the rebuild path for entity-type and skin
     * changes. Returns false when the NMS module could not build the requested type — the NPC
     * stays registered and despawned rather than being dropped, so a failure is recoverable
     * without losing the NPC's configuration.
     */
    fun spawn(): Boolean {
        despawn(NPCDespawnEvent.Reason.RESPAWN)

        val world = location.world
        if (world == null) {
            UnifyCore.instance.logger.warning("NPC '$id' has no loaded world; leaving it despawned.")
            return false
        }

        ChunkPin.pin(location)

        val handler = NMSHandlerFactory.getHandler()
        if (handler == null) {
            UnifyCore.instance.logger.warning("No NMS handler for this server version; NPC '$id' cannot spawn.")
            return false
        }

        val spawned = handler.spawnNpcEntity(buildSpawnSpec())
        if (spawned == null) {
            // Callers retry on a timer (AscendCore's leaderboard re-checks every second), so this
            // warns once per NPC and stays quiet until something changes. Naming the handler makes
            // an unsupported entity type on a given version diagnosable from one line.
            if (!spawnFailureLogged) {
                spawnFailureLogged = true
                UnifyCore.instance.logger.warning(
                    "Failed to spawn NPC '$id' as ${entityType.name} at " +
                        "${world.name} ${location.blockX}, ${location.blockY}, ${location.blockZ} " +
                        "(handler: ${handler.javaClass.simpleName}, " +
                        "supported: ${handler.supportsNpcEntityType(entityType)}). " +
                        "Further failures for this NPC will not be logged."
                )
            }
            return false
        }

        spawnFailureLogged = false
        entityUuid = spawned
        NPCRegistry.bindEntity(spawned, this)

        traits.values.forEach { trait ->
            runCatching { trait.onSpawn() }
                .onFailure { logTraitFailure(trait, "onSpawn", it) }
        }

        Bukkit.getPluginManager().callEvent(NPCSpawnEvent(this))
        return true
    }

    /** Removes the body and releases its chunk pin. No-op when already despawned. */
    fun despawn(reason: NPCDespawnEvent.Reason = NPCDespawnEvent.Reason.REQUESTED) {
        val current = entityUuid ?: return

        Bukkit.getPluginManager().callEvent(NPCDespawnEvent(this, reason))

        traits.values.forEach { trait ->
            runCatching { trait.onDespawn() }
                .onFailure { logTraitFailure(trait, "onDespawn", it) }
        }

        NMSHandlerFactory.getHandler()?.despawnNpcEntity(current)
        NPCRegistry.unbindEntity(current)
        entityUuid = null
        clickCooldowns.clear()

        // Keep the pin across a respawn: releasing and re-pinning would let the chunk unload in
        // between, which is exactly the window the old implementation kept losing bodies in.
        if (reason != NPCDespawnEvent.Reason.RESPAWN) {
            ChunkPin.unpin(location)
        }
    }

    /** Moves the NPC, transferring the chunk pin to the destination. */
    fun teleport(destination: Location) {
        val previous = location
        location = destination.clone()

        if (isSpawned()) {
            ChunkPin.pin(destination)
            val moved = entityUuid?.let { NMSHandlerFactory.getHandler()?.teleportNpcEntity(it, destination) } ?: false
            // A cross-world move cannot be a teleport on most versions — rebuild instead.
            if (!moved || previous.world != destination.world) {
                spawn()
            }
            if (previous.world != destination.world ||
                previous.blockX shr 4 != destination.blockX shr 4 ||
                previous.blockZ shr 4 != destination.blockZ shr 4
            ) {
                ChunkPin.unpin(previous)
            }
        }
    }

    /** Changes the body's entity type, rebuilding it if spawned. */
    fun setEntityType(type: EntityType) {
        if (type == entityType) return
        entityType = type
        if (isSpawned()) spawn()
    }

    // ── Traits ──────────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    fun <T : Trait> getTrait(type: Class<T>): T? = traits[type] as? T

    fun hasTrait(type: Class<out Trait>): Boolean = traits.containsKey(type)

    /** All attached traits, in no particular order. */
    fun traits(): Collection<Trait> = traits.values.toList()

    /**
     * Returns the attached trait of [type], creating and attaching it if absent.
     *
     * The type must be registered with [TraitRegistry]; an unregistered type throws, because a
     * trait we cannot name is a trait we cannot persist, and silently dropping it at save time
     * would be much harder to diagnose than failing here.
     */
    fun <T : Trait> getOrAddTrait(type: Class<T>): T {
        getTrait(type)?.let { return it }
        val created = TraitRegistry.create(type)
            ?: throw IllegalArgumentException("Trait ${type.name} is not registered with TraitRegistry")
        attach(created)
        return created
    }

    /** Attaches [trait], replacing any existing trait of the same type. */
    fun addTrait(trait: Trait) {
        removeTrait(trait.javaClass)
        attach(trait)
    }

    fun removeTrait(type: Class<out Trait>) {
        val removed = traits.remove(type) ?: return
        if (isSpawned()) {
            runCatching { removed.onDespawn() }.onFailure { logTraitFailure(removed, "onDespawn", it) }
        }
        runCatching { removed.onRemove() }.onFailure { logTraitFailure(removed, "onRemove", it) }
    }

    private fun attach(trait: Trait) {
        trait.npc = this
        traits[trait.javaClass] = trait
        runCatching { trait.onAttach() }.onFailure { logTraitFailure(trait, "onAttach", it) }
        // A trait added to an already-spawned NPC still needs its spawn hook, or it would sit
        // inert until the next restart.
        if (isSpawned()) {
            runCatching { trait.onSpawn() }.onFailure { logTraitFailure(trait, "onSpawn", it) }
        }
    }

    /** Ticks every trait that asked to be ticked. Driven by [NPCRegistry]'s single shared task. */
    internal fun tick() {
        for (trait in traits.values) {
            if (!trait.isTicking) continue
            runCatching { trait.onTick() }.onFailure { logTraitFailure(trait, "onTick", it) }
        }
    }

    // ── Interaction ─────────────────────────────────────────────────────────

    internal fun handleRightClick(player: Player) {
        if (!allowClick(player.uniqueId)) return

        val event = NPCRightClickEvent(this, player)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return

        traits.values.forEach { trait ->
            runCatching { trait.onRightClick(player) }
                .onFailure { logTraitFailure(trait, "onRightClick", it) }
        }

        runtimeAction?.let { action ->
            runCatching { action.execute(player, this) }
                .onFailure { UnifyCore.instance.logger.warning("NPC '$id' runtime action failed: ${it.message}") }
        }
    }

    internal fun handleLeftClick(player: Player) {
        if (!allowClick(player.uniqueId)) return

        val event = NPCLeftClickEvent(this, player)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return

        traits.values.forEach { trait ->
            runCatching { trait.onLeftClick(player) }
                .onFailure { logTraitFailure(trait, "onLeftClick", it) }
        }
    }

    internal fun isBody(entity: Entity): Boolean = entity.uniqueId == entityUuid

    /**
     * Tells traits that [player]'s view of this NPC may have changed.
     *
     * The body itself needs no help — the server's entity tracker spawns and despawns it for each
     * player automatically now that it is a normal world entity. This exists for traits that keep
     * their own per-viewer state, such as [me.jordanfails.unify.npc.trait.HologramTrait].
     */
    internal fun updateViewer(player: Player, canSee: Boolean) {
        traits.values.forEach { trait ->
            runCatching { trait.onViewerUpdate(player, canSee) }
                .onFailure { logTraitFailure(trait, "onViewerUpdate", it) }
        }
    }

    /**
     * Debounces clicks per player. A single right-click can surface as both
     * PlayerInteractEntityEvent and PlayerInteractAtEntityEvent, so without this every click
     * would fire the NPC's action twice.
     */
    private fun allowClick(playerUuid: UUID): Boolean {
        val now = System.currentTimeMillis()
        if (now < (clickCooldowns[playerUuid] ?: 0L)) return false
        clickCooldowns[playerUuid] = now + CLICK_COOLDOWN_MS
        return true
    }

    // ── Internals ───────────────────────────────────────────────────────────

    /**
     * Collects the trait-owned state that has to be present at construction time.
     *
     * The skin in particular cannot be applied afterwards on a player body — it lives on the
     * GameProfile, which is fixed once the entity exists. Name and pose could be pushed by their
     * traits post-spawn, but including them here avoids a visible tick of default appearance.
     */
    private fun buildSpawnSpec(): NPCSpawnSpec {
        val nameTrait = getTrait(NameTrait::class.java)
        return NPCSpawnSpec(
            npcId = id,
            entityType = entityType,
            location = location,
            skin = getTrait(SkinTrait::class.java)?.skin,
            name = nameTrait?.displayName,
            nameVisible = nameTrait?.visible ?: false,
            pose = getTrait(PoseTrait::class.java)?.pose ?: NPCPose.STANDING,
        )
    }

    private fun logTraitFailure(trait: Trait, hook: String, error: Throwable) {
        UnifyCore.instance.logger.warning(
            "Trait '${trait.name}' on NPC '$id' failed during $hook: ${error.message}"
        )
    }

    companion object {
        private const val CLICK_COOLDOWN_MS = 200L
    }
}
