package me.jordanfails.unify.npc.trait

import java.util.concurrent.ConcurrentHashMap

/**
 * Maps trait names to their implementations so traits can be created from `npcs.yml` and from
 * commands without the NPC package hard-coding the built-in set.
 *
 * Names are the on-disk and on-command identity of a trait and are matched case-insensitively;
 * changing one breaks existing saves, so treat them as a stable API.
 */
object TraitRegistry {

    class Entry(
        val name: String,
        val type: Class<out Trait>,
        val factory: () -> Trait,
    )

    private val byName = ConcurrentHashMap<String, Entry>()
    private val byType = ConcurrentHashMap<Class<out Trait>, Entry>()

    /**
     * Registers a trait type. Re-registering the same name replaces the previous entry, which lets
     * a downstream plugin override a built-in trait without us needing an unregister dance.
     */
    fun <T : Trait> register(name: String, type: Class<T>, factory: () -> T) {
        val entry = Entry(name, type, factory)
        byName[name.lowercase()] = entry
        byType[type] = entry
    }

    /** Creates a fresh instance of the trait registered under [name], or null if none is. */
    fun create(name: String): Trait? = byName[name.lowercase()]?.factory?.invoke()

    /** Creates a fresh instance of [type]. Null if the type was never registered. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Trait> create(type: Class<T>): T? = byType[type]?.factory?.invoke() as? T

    /** The registered name for [type] — needed to decide which `npcs.yml` section a trait saves into. */
    fun nameOf(type: Class<out Trait>): String? = byType[type]?.name

    /** True when [name] resolves to a registered trait. */
    fun isRegistered(name: String): Boolean = byName.containsKey(name.lowercase())

    /** All registered trait names, for tab-completion and `/npc trait list`. */
    fun names(): Set<String> = byName.values.map { it.name }.toSortedSet()

    /**
     * Registers every trait Unify ships with. Called once from
     * [me.jordanfails.unify.npc.NPCRegistry.enable] before any NPC is loaded, since loading an
     * NPC needs its traits to already be resolvable.
     */
    internal fun registerBuiltins() {
        register("skin", SkinTrait::class.java) { SkinTrait() }
        register("name", NameTrait::class.java) { NameTrait() }
        register("hologram", HologramTrait::class.java) { HologramTrait() }
        register("command", CommandTrait::class.java) { CommandTrait() }
        register("lookclose", LookCloseTrait::class.java) { LookCloseTrait() }
        register("equipment", EquipmentTrait::class.java) { EquipmentTrait() }
        register("protected", ProtectedTrait::class.java) { ProtectedTrait() }
        register("pose", PoseTrait::class.java) { PoseTrait() }
    }
}
