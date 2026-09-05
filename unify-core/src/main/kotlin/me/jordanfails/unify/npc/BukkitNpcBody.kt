package me.jordanfails.unify.npc

import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.utils.CC
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.inventory.ItemStack
import org.bukkit.metadata.FixedMetadataValue
import java.util.UUID

/**
 * NPC bodies for every entity type except players, built on the plain Bukkit API.
 *
 * Player bodies need NMS — a fake GameProfile, a skin, a synthetic connection — and each server
 * version does that differently. Nothing else does. `World#spawnEntity` has accepted every
 * spawnable type since 1.8, and name, equipment, and teleport are all ordinary Bukkit calls on the
 * result, so the whole non-player path is one implementation shared by all seven version modules
 * instead of seven near-identical copies.
 *
 * Version differences that remain are absorbed by [invokeBooleanSetter]: `setAI`, `setCollidable`
 * and `setGravity` arrived at different times, and calling one that does not exist is a no-op
 * rather than a crash.
 */
object BukkitNpcBody {

    /** True for anything this can spawn — every spawnable type except players. */
    fun supports(type: EntityType): Boolean = type != EntityType.PLAYER && type.isSpawnable

    /**
     * Spawns and configures a body. Returns null when the type cannot be spawned here.
     *
     * The entity is made inert on purpose: no AI, no gravity, no collision, no despawning. An NPC
     * that wanders off, suffocates, or gets pushed into a wall is the most common way a "working"
     * NPC setup silently breaks.
     */
    fun spawn(spec: NPCSpawnSpec): UUID? {
        if (!supports(spec.entityType)) return null
        val world = spec.location.world ?: return null

        val entity = runCatching { world.spawnEntity(spec.location, spec.entityType) }
            .onFailure {
                UnifyCore.instance.logger.warning(
                    "Could not spawn NPC '${spec.npcId}' as ${spec.entityType.name}: ${it.message}"
                )
            }
            .getOrNull() ?: return null

        configure(entity, spec.npcId)
        applyName(entity, spec.name, spec.nameVisible)
        return entity.uniqueId
    }

    fun despawn(entityUuid: UUID) {
        Bukkit.getEntity(entityUuid)?.remove()
    }

    fun teleport(entityUuid: UUID, location: Location): Boolean {
        val entity = Bukkit.getEntity(entityUuid) ?: return false
        if (entity.world != location.world) return false
        return entity.teleport(location)
    }

    fun setName(entityUuid: UUID, name: String?, visible: Boolean): Boolean {
        val entity = Bukkit.getEntity(entityUuid) ?: return false
        applyName(entity, name, visible)
        return true
    }

    fun setEquipment(entityUuid: UUID, slot: NPCEquipmentSlot, item: ItemStack?): Boolean {
        val entity = Bukkit.getEntity(entityUuid) as? LivingEntity ?: return false
        val equipment = entity.equipment ?: return false

        // Setter names differ across versions (off-hand only exists from 1.9), so this goes
        // through reflection rather than the typed API Unify compiles against.
        val setter = when (slot) {
            NPCEquipmentSlot.HAND -> "setItemInHand"
            NPCEquipmentSlot.OFF_HAND -> "setItemInOffHand"
            NPCEquipmentSlot.HELMET -> "setHelmet"
            NPCEquipmentSlot.CHESTPLATE -> "setChestplate"
            NPCEquipmentSlot.LEGGINGS -> "setLeggings"
            NPCEquipmentSlot.BOOTS -> "setBoots"
        }

        val applied = runCatching {
            equipment.javaClass.getMethod(setter, ItemStack::class.java).invoke(equipment, item)
            true
        }.getOrElse { false }

        // Without this, anything the NPC is holding drops when it is removed or killed.
        if (applied) clearDropChance(equipment, slot)
        return applied
    }

    fun setLook(entityUuid: UUID, yaw: Float, pitch: Float): Boolean {
        val entity = Bukkit.getEntity(entityUuid) ?: return false
        val target = entity.location
        target.yaw = yaw
        target.pitch = pitch

        // A Bukkit teleport is the only cross-version way to rotate a non-player entity, and it
        // only moves the body, not the head. Good enough for mobs; player bodies get real
        // rotation packets from their NMS module.
        return entity.teleport(target)
    }

    /**
     * Poses on non-player bodies are limited to sneaking, and only where the API exists.
     * Everything else reports failure so the caller can tell the user rather than silently
     * leaving the NPC standing.
     */
    fun setPose(entityUuid: UUID, pose: NPCPose): Boolean {
        val entity = Bukkit.getEntity(entityUuid) ?: return false
        return when (pose) {
            NPCPose.STANDING -> invokeBooleanSetter(entity, "setSneaking", false)
            NPCPose.SNEAKING -> invokeBooleanSetter(entity, "setSneaking", true)
            else -> false
        }
    }

    /** Marks the body and strips every behaviour that would let it move, die, or despawn. */
    private fun configure(entity: Entity, npcId: String) {
        val plugin = UnifyCore.instance
        entity.setMetadata(NPCRegistry.NPC_METADATA_KEY, FixedMetadataValue(plugin, npcId))
        // Ecosystem convention — third-party plugins check this key to skip NPCs.
        entity.setMetadata(NPCRegistry.LEGACY_NPC_METADATA_KEY, FixedMetadataValue(plugin, true))

        invokeBooleanSetter(entity, "setAI", false)
        invokeBooleanSetter(entity, "setSilent", true)
        invokeBooleanSetter(entity, "setInvulnerable", true)
        invokeBooleanSetter(entity, "setCollidable", false)
        invokeBooleanSetter(entity, "setGravity", false)
        invokeBooleanSetter(entity, "setCanPickupItems", false)
        invokeBooleanSetter(entity, "setRemoveWhenFarAway", false)
        invokeBooleanSetter(entity, "setPersistent", true)

        // 1.8 has no setAI. Slowness at that amplitude pins the entity in place, which is what
        // the old NPC code did too and is the only option on that version.
        if (!hasBooleanSetter(entity, "setAI")) {
            runCatching {
                (entity as? LivingEntity)?.addPotionEffect(
                    org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW, Int.MAX_VALUE, 10, true, false)
                )
            }
        }
    }

    private fun applyName(entity: Entity, name: String?, visible: Boolean) {
        val translated = name?.let { CC.translate(it) }
        runCatching { entity.customName = translated }
        runCatching { entity.isCustomNameVisible = visible && translated != null }
    }

    private fun clearDropChance(equipment: Any, slot: NPCEquipmentSlot) {
        val setter = when (slot) {
            NPCEquipmentSlot.HAND -> "setItemInHandDropChance"
            NPCEquipmentSlot.OFF_HAND -> "setItemInOffHandDropChance"
            NPCEquipmentSlot.HELMET -> "setHelmetDropChance"
            NPCEquipmentSlot.CHESTPLATE -> "setChestplateDropChance"
            NPCEquipmentSlot.LEGGINGS -> "setLeggingsDropChance"
            NPCEquipmentSlot.BOOTS -> "setBootsDropChance"
        }
        runCatching {
            equipment.javaClass.getMethod(setter, java.lang.Float.TYPE).invoke(equipment, 0f)
        }
    }

    /** Calls a `setX(boolean)` if this version has it. Returns whether it existed and succeeded. */
    private fun invokeBooleanSetter(target: Any, methodName: String, value: Boolean): Boolean =
        runCatching {
            target.javaClass.getMethod(methodName, java.lang.Boolean.TYPE).invoke(target, value)
            true
        }.getOrElse { false }

    private fun hasBooleanSetter(target: Any, methodName: String): Boolean =
        runCatching { target.javaClass.getMethod(methodName, java.lang.Boolean.TYPE) }.isSuccess
}
