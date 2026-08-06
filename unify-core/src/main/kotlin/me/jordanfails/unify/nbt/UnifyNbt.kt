package me.jordanfails.unify.nbt

import de.tr7zw.nbtapi.NBT
import de.tr7zw.nbtapi.iface.ReadWriteItemNBT
import de.tr7zw.nbtapi.iface.ReadWriteNBT
import de.tr7zw.nbtapi.iface.ReadableItemNBT
import de.tr7zw.nbtapi.iface.ReadableNBT
import org.bukkit.block.BlockState
import org.bukkit.entity.Entity
import org.bukkit.inventory.ItemStack
import java.util.function.Consumer
import java.util.function.Function

/**
 * Direct access to the NBTAPI-backed vanilla NBT of entities, placed tile entities,
 * and items. NBT compounds are only valid inside their callback; read primitive
 * values or modify the supplied compound before returning.
 *
 * NBTAPI must be installed. Unify declares it as a soft dependency so it is loaded
 * before this API is used.
 */
object UnifyNbt {
    fun <T> readEntity(entity: Entity, reader: (ReadableNBT) -> T): T =
        NBT.get(entity, Function(reader))

    fun modifyEntity(entity: Entity, writer: (ReadWriteNBT) -> Unit) {
        NBT.modify(entity, Consumer(writer))
    }

    fun <T> readBlock(state: BlockState, reader: (ReadableNBT) -> T): T =
        NBT.get(state, Function(reader))

    /**
     * Modifies the vanilla NBT of a placed tile entity, such as a spawner, chest,
     * sign, beacon, or command block. Ordinary blocks do not have tile NBT.
     */
    fun modifyBlock(state: BlockState, writer: (ReadWriteNBT) -> Unit) {
        NBT.modify(state, Consumer(writer))
    }

    fun <T> readItem(item: ItemStack, reader: (ReadableItemNBT) -> T): T =
        NBT.get(item, Function(reader))

    fun modifyItem(item: ItemStack, writer: (ReadWriteItemNBT) -> Unit) {
        NBT.modify(item, Consumer(writer))
    }
}
