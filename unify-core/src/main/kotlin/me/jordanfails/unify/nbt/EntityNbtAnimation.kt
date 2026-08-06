package me.jordanfails.unify.nbt

import org.bukkit.entity.Entity
import org.bukkit.util.Vector

/**
 * Vanilla entity-NBT helpers intended for animation frames. They can be applied
 * repeatedly from a Bukkit task without relying on Bukkit persistent-data tags.
 */
object EntityNbtAnimation {
    fun setMotion(entity: Entity, velocity: Vector) {
        UnifyNbt.modifyEntity(entity) { nbt ->
            nbt.getDoubleList("Motion").apply {
                clear()
                add(velocity.x)
                add(velocity.y)
                add(velocity.z)
            }
        }
    }

    fun setRotation(entity: Entity, yaw: Float, pitch: Float) {
        UnifyNbt.modifyEntity(entity) { nbt ->
            nbt.getFloatList("Rotation").apply {
                clear()
                add(yaw)
                add(pitch)
            }
        }
    }

    fun setNoAi(entity: Entity, value: Boolean) = setFlag(entity, "NoAI", value)
    fun setNoGravity(entity: Entity, value: Boolean) = setFlag(entity, "NoGravity", value)
    fun setSilent(entity: Entity, value: Boolean) = setFlag(entity, "Silent", value)
    fun setGlowing(entity: Entity, value: Boolean) = setFlag(entity, "Glowing", value)
    fun setInvulnerable(entity: Entity, value: Boolean) = setFlag(entity, "Invulnerable", value)
    fun setPersistent(entity: Entity, value: Boolean) = setFlag(entity, "PersistenceRequired", value)

    /** Applies the common NBT switches used by display and mob animations in one write. */
    fun apply(entity: Entity, state: State) {
        UnifyNbt.modifyEntity(entity) { nbt ->
            state.noAi?.let { nbt.setBoolean("NoAI", it) }
            state.noGravity?.let { nbt.setBoolean("NoGravity", it) }
            state.silent?.let { nbt.setBoolean("Silent", it) }
            state.glowing?.let { nbt.setBoolean("Glowing", it) }
            state.invulnerable?.let { nbt.setBoolean("Invulnerable", it) }
            state.persistent?.let { nbt.setBoolean("PersistenceRequired", it) }
            state.motion?.let { velocity ->
                nbt.getDoubleList("Motion").apply {
                    clear()
                    add(velocity.x)
                    add(velocity.y)
                    add(velocity.z)
                }
            }
            state.rotation?.let { (yaw, pitch) ->
                nbt.getFloatList("Rotation").apply {
                    clear()
                    add(yaw)
                    add(pitch)
                }
            }
        }
    }

    private fun setFlag(entity: Entity, tag: String, value: Boolean) {
        UnifyNbt.modifyEntity(entity) { it.setBoolean(tag, value) }
    }

    data class State(
        val noAi: Boolean? = null,
        val noGravity: Boolean? = null,
        val silent: Boolean? = null,
        val glowing: Boolean? = null,
        val invulnerable: Boolean? = null,
        val persistent: Boolean? = null,
        val motion: Vector? = null,
        val rotation: Pair<Float, Float>? = null,
    )
}
