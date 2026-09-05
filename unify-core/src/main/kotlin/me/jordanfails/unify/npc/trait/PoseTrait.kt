package me.jordanfails.unify.npc.trait

import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.nms.NMSHandlerFactory
import me.jordanfails.unify.npc.NPCPose
import me.jordanfails.unify.npc.NPCRegistry
import org.bukkit.configuration.ConfigurationSection

/**
 * Holds the body in a pose — sitting, sneaking, sleeping, swimming.
 *
 * Support varies by server version (1.8 has no swim pose at all). An unsupported pose is refused
 * rather than silently ignored, so `/npc pose` can tell the user instead of leaving them staring
 * at an NPC that did not move.
 */
class PoseTrait : Trait("pose") {

    var pose: NPCPose = NPCPose.STANDING
        private set

    /** Returns false when this server version cannot render [value]; the pose is left unchanged. */
    fun setPose(value: NPCPose): Boolean {
        val handler = NMSHandlerFactory.getHandler() ?: return false
        if (!handler.supportsNpcPose(value)) return false

        pose = value
        apply()
        NPCRegistry.save()
        return true
    }

    override fun onSpawn() = apply()

    private fun apply() {
        if (!isAttached || !npc.isSpawned()) return
        val entity = npc.entityUuid ?: return
        NMSHandlerFactory.getHandler()?.setNpcPose(entity, pose)
    }

    override fun save(section: ConfigurationSection) {
        section.set("pose", pose.name)
    }

    override fun load(section: ConfigurationSection) {
        val stored = section.getString("pose") ?: return
        val parsed = runCatching { NPCPose.valueOf(stored.uppercase()) }.getOrNull()
        if (parsed == null) {
            UnifyCore.instance.logger.warning("Unknown NPC pose '$stored'; falling back to STANDING.")
            return
        }
        pose = parsed
    }
}
