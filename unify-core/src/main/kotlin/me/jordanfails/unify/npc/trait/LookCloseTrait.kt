package me.jordanfails.unify.npc.trait

import me.jordanfails.unify.nms.NMSHandlerFactory
import me.jordanfails.unify.npc.NPCRegistry
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Turns the NPC's head to face the nearest player.
 *
 * Ticked from [NPCRegistry]'s shared task rather than a per-NPC timer, and only while [enabled],
 * so an NPC without look-close costs nothing. When no player is in [range] the head returns to the
 * NPC's configured facing once and then goes idle — it does not keep sending packets at an empty
 * room.
 */
class LookCloseTrait : Trait("lookclose") {

    var enabled: Boolean = true
        private set

    /** How far away a player can be and still be looked at, in blocks. */
    var range: Double = DEFAULT_RANGE
        private set

    /** The player currently being tracked, so we can tell "still none" from "just lost them". */
    private var target: Player? = null

    override val isTicking: Boolean
        get() = enabled

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) resetFacing()
        NPCRegistry.save()
    }

    fun setRange(value: Double) {
        range = value.coerceIn(1.0, 64.0)
        NPCRegistry.save()
    }

    override fun onTick() {
        if (!npc.isSpawned()) return

        val nearest = findNearestPlayer()
        if (nearest == null) {
            // Only reset on the transition to "nobody here", not every tick afterwards.
            if (target != null) {
                target = null
                resetFacing()
            }
            return
        }

        target = nearest
        val eye = npc.location.add(0.0, EYE_HEIGHT, 0.0)
        val to = nearest.eyeLocation

        val dx = to.x - eye.x
        val dy = to.y - eye.y
        val dz = to.z - eye.z

        val yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val pitch = Math.toDegrees(-atan2(dy, sqrt(dx * dx + dz * dz))).toFloat()

        npc.entityUuid?.let { NMSHandlerFactory.getHandler()?.setNpcLook(it, yaw, pitch) }
    }

    override fun onDespawn() {
        target = null
    }

    private fun findNearestPlayer(): Player? {
        val origin = npc.location
        val world = origin.world ?: return null
        val rangeSq = range * range

        var best: Player? = null
        var bestDistance = Double.MAX_VALUE

        // Iterating the world's players (not the whole server) keeps this proportional to the
        // players actually in this NPC's world. Player-type NPC bodies do appear here — they are
        // level entities — so they are filtered out, or NPCs would stare at each other.
        for (player in world.players) {
            if (!player.isValid || player.isDead) continue
            if (NPCRegistry.isNpc(player)) continue
            val distance = player.location.distanceSquared(origin)
            if (distance > rangeSq || distance >= bestDistance) continue
            best = player
            bestDistance = distance
        }
        return best
    }

    private fun resetFacing() {
        if (!isAttached || !npc.isSpawned()) return
        val entity = npc.entityUuid ?: return
        val origin = npc.location
        NMSHandlerFactory.getHandler()?.setNpcLook(entity, origin.yaw, origin.pitch)
    }

    override fun save(section: ConfigurationSection) {
        section.set("enabled", enabled)
        section.set("range", range)
    }

    override fun load(section: ConfigurationSection) {
        enabled = if (section.contains("enabled")) section.getBoolean("enabled") else true
        range = if (section.contains("range")) section.getDouble("range") else DEFAULT_RANGE
    }

    companion object {
        private const val DEFAULT_RANGE = 10.0

        /** Approximate player eye height; good enough that the head does not aim at players' feet. */
        private const val EYE_HEIGHT = 1.62
    }
}
