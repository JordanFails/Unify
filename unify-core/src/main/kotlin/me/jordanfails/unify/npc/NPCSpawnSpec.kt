package me.jordanfails.unify.npc

import org.bukkit.Location
import org.bukkit.entity.EntityType

/**
 * Everything an NMS module needs to build an NPC body in one call.
 *
 * Bundled into a type rather than passed as a parameter list because the list has grown once per
 * feature before, and every growth meant editing all seven version modules to keep the overrides
 * in sync. Adding a field here with a default only touches the modules that care about it.
 */
data class NPCSpawnSpec(
    /** The NPC's stable id, used for logging and for naming per-NPC scoreboard teams. */
    val npcId: String,
    val entityType: EntityType,
    val location: Location,
    /** Only meaningful for [EntityType.PLAYER] bodies; ignored otherwise. */
    val skin: NPCSkin? = null,
    /** Rendered on the body's own nameplate. Null means no nameplate. */
    val name: String? = null,
    val nameVisible: Boolean = true,
    val pose: NPCPose = NPCPose.STANDING,
)
