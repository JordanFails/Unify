package me.jordanfails.unify.npc.trait

import me.jordanfails.unify.nms.NMSHandlerFactory
import me.jordanfails.unify.npc.NPCRegistry
import me.jordanfails.unify.utils.CC
import org.bukkit.configuration.ConfigurationSection

/**
 * Renders text on the body's own nameplate.
 *
 * This is the cheap single-line option. It uses the entity's real custom name, so it costs no
 * extra entities and follows the body automatically — but it is limited to one line and sits at a
 * fixed height. Use [HologramTrait] when you need several lines or control over placement.
 *
 * On player-type NPCs the nameplate is driven through a scoreboard team rather than a custom name,
 * since vanilla always renders a player's profile name above its head; the NMS layer handles that
 * difference so this trait does not have to.
 */
class NameTrait : Trait("name") {

    /** Raw text, with `&` colour codes. Null means no nameplate. */
    var displayName: String? = null
        private set

    /** Whether the nameplate renders. Kept separate from [displayName] so text survives hiding it. */
    var visible: Boolean = true
        private set

    /** [displayName] with colour codes translated, ready for the NMS layer. */
    val formattedName: String?
        get() = displayName?.let { CC.translate(it) }

    fun setDisplayName(name: String?) {
        displayName = name?.trim()?.takeIf { it.isNotEmpty() }
        apply()
        NPCRegistry.save()
    }

    fun setVisible(value: Boolean) {
        visible = value
        apply()
        NPCRegistry.save()
    }

    override fun onSpawn() = apply()

    private fun apply() {
        if (!isAttached || !npc.isSpawned()) return
        val entity = npc.entityUuid ?: return
        NMSHandlerFactory.getHandler()?.setNpcName(entity, formattedName, visible && displayName != null)
    }

    override fun save(section: ConfigurationSection) {
        section.set("display-name", displayName)
        section.set("visible", visible)
    }

    override fun load(section: ConfigurationSection) {
        displayName = section.getString("display-name")
        visible = if (section.contains("visible")) section.getBoolean("visible") else true
    }
}
