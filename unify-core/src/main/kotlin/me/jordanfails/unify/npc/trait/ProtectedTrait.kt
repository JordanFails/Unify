package me.jordanfails.unify.npc.trait

import me.jordanfails.unify.npc.NPCRegistry
import org.bukkit.configuration.ConfigurationSection

/**
 * Whether the NPC can be harmed or pushed around.
 *
 * Protection is on by default and is what the damage listener consults. Turning it off is for the
 * unusual case of an NPC that is meant to be killable — a duel dummy, an arena target — and means
 * the NPC's body can genuinely die, which despawns it until something spawns it again.
 */
class ProtectedTrait : Trait("protected") {

    var isProtected: Boolean = true
        private set

    fun setProtected(value: Boolean) {
        isProtected = value
        NPCRegistry.save()
    }

    override fun save(section: ConfigurationSection) {
        section.set("protected", isProtected)
    }

    override fun load(section: ConfigurationSection) {
        isProtected = if (section.contains("protected")) section.getBoolean("protected") else true
    }
}
