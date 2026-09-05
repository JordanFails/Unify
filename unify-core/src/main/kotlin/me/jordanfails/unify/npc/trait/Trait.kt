package me.jordanfails.unify.npc.trait

import me.jordanfails.unify.npc.UnifyNPC
import org.bukkit.configuration.ConfigurationSection

/**
 * A pluggable, persisted unit of NPC behaviour.
 *
 * Traits are how an NPC gains capabilities without [UnifyNPC] growing a field per feature. Each
 * trait owns its own state, its own persistence, and its own reaction to the NPC's lifecycle.
 * Third-party code can define traits too — register a factory with [TraitRegistry] and they load
 * from `npcs.yml` exactly like the built-in ones.
 *
 * Lifecycle order for a trait attached to a spawned NPC:
 * `onAttach` → `onSpawn` → (`onTick`)* → `onDespawn` → `onRemove`
 *
 * A trait attached while the NPC is already spawned receives `onAttach` then `onSpawn` immediately,
 * so implementations never need to check whether they missed the spawn.
 */
abstract class Trait(val name: String) {

    /**
     * The NPC this trait belongs to. Assigned before [onAttach] and valid for the trait's whole
     * life; reading it earlier (in an `init` block or constructor) will throw.
     */
    lateinit var npc: UnifyNPC
        internal set

    /** True once [npc] is assigned — for defensive checks in async callbacks that may outlive the trait. */
    val isAttached: Boolean
        get() = this::npc.isInitialized

    /** Called once when the trait is added to an NPC, before any spawn. */
    open fun onAttach() {}

    /** Called every time the NPC's body is (re)created. Push initial state to the body here. */
    open fun onSpawn() {}

    /** Called before the NPC's body goes away. Release per-body state here, not per-NPC state. */
    open fun onDespawn() {}

    /** Called when the trait is removed from the NPC, or the NPC is deleted. */
    open fun onRemove() {}

    /**
     * Called when a player right-clicks the NPC, after [me.jordanfails.unify.npc.event.NPCRightClickEvent]
     * has been fired and not cancelled. Clicks are already debounced per player.
     */
    open fun onRightClick(player: org.bukkit.entity.Player) {}

    /** Called when a player left-clicks the NPC. Damage is cancelled before this runs. */
    open fun onLeftClick(player: org.bukkit.entity.Player) {}

    /**
     * Called when a player's ability to see this NPC may have changed — they joined, quit, or
     * moved between worlds. [canSee] is false when the player can no longer see the NPC.
     *
     * Only traits that maintain their own per-viewer state (holograms, per-player packets) need
     * this; the NPC body itself is handled by the server's entity tracker.
     */
    open fun onViewerUpdate(player: org.bukkit.entity.Player, canSee: Boolean) {}

    /**
     * Called on the main thread while the NPC is spawned, at the registry's tick rate.
     *
     * Only invoked when [isTicking] is true, so traits that are dormant most of the time (skins,
     * equipment) cost nothing. Keep this cheap — it runs per NPC, per tick.
     */
    open fun onTick() {}

    /**
     * Whether [onTick] should be called. Checked each tick, so a trait can go dormant at runtime
     * (e.g. look-close with no players in range) by flipping this to false.
     */
    open val isTicking: Boolean
        get() = false

    /** Write this trait's state into its own section of `npcs.yml`. */
    open fun save(section: ConfigurationSection) {}

    /** Restore state written by [save]. Called before [onAttach]. */
    open fun load(section: ConfigurationSection) {}
}
