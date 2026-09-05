package me.jordanfails.unify.npc.trait

import me.jordanfails.unify.nms.NMSHandlerFactory
import me.jordanfails.unify.npc.NPCRegistry
import me.jordanfails.unify.npc.NPCSkin
import me.jordanfails.unify.npc.SkinResolver
import org.bukkit.configuration.ConfigurationSection

/**
 * Gives a player-type NPC its appearance.
 *
 * Only meaningful on [org.bukkit.entity.EntityType.PLAYER] bodies — the trait attaches harmlessly
 * to any NPC but the NMS layer ignores it for other types.
 *
 * The skin is stored resolved (texture + signature) so a restart does not need the network to put
 * NPCs back on screen, but its origin is stored too, so [refresh] can pick up a player changing
 * their skin without the NPC having to be recreated.
 */
class SkinTrait : Trait("skin") {

    /** The currently applied skin, or null for the default appearance. */
    var skin: NPCSkin? = null
        private set

    /**
     * Resolves and applies a skin. [callback] receives false when resolution failed — an unknown
     * player name, a Mojang outage, or a lookup already in flight for that name.
     *
     * Resolution is asynchronous for NAME sources, so this returns long before the skin lands.
     */
    fun setSkin(sourceType: NPCSkin.SourceType, source: String, callback: (Boolean) -> Unit = {}) {
        SkinResolver.resolve(sourceType, source) { resolved ->
            if (resolved == null) {
                callback(false)
                return@resolve
            }
            skin = resolved
            apply()
            NPCRegistry.save()
            callback(true)
        }
    }

    /** Drops the skin, returning the NPC to the default appearance. */
    fun clearSkin() {
        skin = null
        apply()
        NPCRegistry.save()
    }

    /**
     * Re-resolves the stored source and reapplies it.
     *
     * Only does anything for NAME skins; URL and BASE64 sources are fixed by definition.
     */
    fun refresh(callback: (Boolean) -> Unit = {}) {
        val current = skin
        if (current == null || current.sourceType != NPCSkin.SourceType.NAME) {
            callback(false)
            return
        }
        setSkin(current.sourceType, current.source, callback)
    }

    override fun onSpawn() {
        // The spawn spec already carried the skin into the new body's profile, so there is
        // nothing to push here. Re-applying would send a pointless respawn burst to every viewer.
    }

    /**
     * Pushes the current skin onto the live body.
     *
     * A player body's skin lives on its GameProfile, which is fixed once the entity exists, so the
     * NMS layer swaps the profile and re-sends the entity to current viewers. When a module cannot
     * do that we fall back to rebuilding the whole NPC — correct either way, just more disruptive.
     */
    private fun apply() {
        if (!isAttached || !npc.isSpawned()) return
        val entity = npc.entityUuid ?: return
        val applied = NMSHandlerFactory.getHandler()?.setNpcSkin(entity, skin) ?: false
        if (!applied) npc.spawn()
    }

    override fun save(section: ConfigurationSection) {
        val current = skin ?: return
        section.set("source-type", current.sourceType.name)
        section.set("source", current.source)
        section.set("value", current.value)
        section.set("signature", current.signature)
    }

    override fun load(section: ConfigurationSection) {
        val value = section.getString("value") ?: return
        val sourceType = section.getString("source-type")
            ?.let { runCatching { NPCSkin.SourceType.valueOf(it.uppercase()) }.getOrNull() }
            ?: NPCSkin.SourceType.BASE64

        skin = NPCSkin(
            value = value,
            signature = section.getString("signature"),
            sourceType = sourceType,
            source = section.getString("source") ?: value,
        )
    }
}
