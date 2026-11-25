package me.jordanfails.unify.nms.v1_21_R1

import io.netty.buffer.Unpooled
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Interaction
import net.minecraft.world.entity.decoration.ArmorStand
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import java.util.UUID

/**
 * Extra nametag lines that ride a player on one viewer's client. The client carries passengers
 * along with their vehicle every frame, so the lines can't trail behind like teleported holograms.
 *
 * Each line is an invisible armor stand's name, which the client draws with the exact same code
 * as a player's name: faint through walls, dimmed while sneaking, lit like the player. Passengers
 * of one vehicle all sit at the same spot, so the stands are lifted by a chain of invisible,
 * zero-width interactions, one line tall each:
 *
 * player ← interaction ← (bottom stand, interaction ← (next stand, interaction ← …))
 */
internal class RidingNametagLines(private val parseText: (String) -> Component) {
    private class Mount(
        val interactionIds: List<Int>,
        val standIds: List<Int>,
        var lines: List<String>,
        var sneaking: Boolean,
        var age: Int = 0,
    )

    /** viewer -> target -> what that viewer's client has spawned. */
    private val mounts = mutableMapOf<UUID, MutableMap<UUID, Mount>>()

    fun show(viewer: Player, target: Player, lines: List<String>) {
        val connection = (viewer as CraftPlayer).handle.connection
        val vehicle = (target as CraftPlayer).handle
        // Without the player on the viewer's client there is nothing to ride, and the lines
        // would hang wherever they were spawned.
        if (vehicle.`moonrise$getTrackedEntity`()?.seenBy?.contains(connection) != true) {
            return hide(viewer, target.uniqueId)
        }

        val byTarget = mounts.getOrPut(viewer.uniqueId) { mutableMapOf() }
        val mount = byTarget[target.uniqueId]
        if (mount == null || mount.lines.size != lines.size) {
            if (mount != null) hide(viewer, target.uniqueId)
            byTarget[target.uniqueId] = spawn(connection, vehicle, lines)
            return
        }

        val sneaking = vehicle.isShiftKeyDown
        for (i in lines.indices) {
            if (mount.lines[i] != lines[i] || mount.sneaking != sneaking) {
                stand(vehicle, lines[i], sneaking).also { it.id = mount.standIds[i] }.sendData(connection)
            }
        }
        mount.lines = lines
        mount.sneaking = sneaking
        // The server's own passenger updates for this player replace ours, so mount again now and then.
        if (++mount.age % REMOUNT_TICKS == 0) mountOn(connection, vehicle, mount.interactionIds.first())
    }

    fun hide(viewer: Player, target: UUID) {
        val mount = mounts[viewer.uniqueId]?.remove(target) ?: return
        (viewer as CraftPlayer).handle.connection
            .send(ClientboundRemoveEntitiesPacket(*(mount.interactionIds + mount.standIds).toIntArray()))
    }

    private fun spawn(connection: ServerGamePacketListenerImpl, vehicle: ServerPlayer, lines: List<String>): Mount {
        val sneaking = vehicle.isShiftKeyDown
        val stands = lines.map { stand(vehicle, it, sneaking) }
        // Interaction i lifts stand i one line above the one below it; lines are listed top first.
        val interactions = lines.map {
            Interaction(EntityType.INTERACTION, vehicle.level()).apply {
                setPos(vehicle.x, vehicle.y, vehicle.z)
                width = 0f
                height = LINE_HEIGHT
            }
        }
        for (entity in interactions + stands) {
            connection.send(addEntityPacket(entity))
            entity.sendData(connection)
        }

        // Bottom line first: it rides the lowest interaction, which rides the player.
        val bottomUp = lines.indices.reversed().toList()
        mountOn(connection, vehicle, interactions[bottomUp.first()].id)
        bottomUp.forEachIndexed { level, line ->
            val riders = listOfNotNull(stands[line], bottomUp.getOrNull(level + 1)?.let { interactions[it] })
            connection.send(passengersPacket(interactions[line].id, riders.map { it.id }.toIntArray()))
        }
        return Mount(interactions.map { it.id }, stands.map { it.id }, lines, sneaking)
    }

    private fun mountOn(connection: ServerGamePacketListenerImpl, vehicle: ServerPlayer, interactionId: Int) {
        val riders = vehicle.passengers.map { it.id } + interactionId
        connection.send(passengersPacket(vehicle.id, riders.toIntArray()))
    }

    private fun stand(vehicle: ServerPlayer, line: String, sneaking: Boolean) =
        ArmorStand(vehicle.level(), vehicle.x, vehicle.y, vehicle.z).apply {
            customName = parseText(line)
            // A blank line still draws an empty background box, so leave it as a gap instead.
            isCustomNameVisible = line.isNotBlank()
            isInvisible = true
            isNoGravity = true
            isSmall = true
            isMarker = true
            // The client dims a "sneaking" name and stops drawing it through walls, like the player's own.
            isShiftKeyDown = sneaking
        }

    private fun Entity.sendData(connection: ServerGamePacketListenerImpl) {
        entityData.packAll()?.let { connection.send(ClientboundSetEntityDataPacket(id, it)) }
    }

    private fun addEntityPacket(entity: Entity) = ClientboundAddEntityPacket(
        entity.id, entity.uuid, entity.x, entity.y, entity.z, entity.xRot, entity.yRot,
        entity.type, 0, entity.deltaMovement, entity.yHeadRot.toDouble()
    )

    /** The packet only has a constructor that reads the real entity's passengers, so decode our own. */
    private fun passengersPacket(vehicle: Int, riders: IntArray): ClientboundSetPassengersPacket {
        val buf = FriendlyByteBuf(Unpooled.buffer())
        buf.writeVarInt(vehicle)
        buf.writeVarIntArray(riders)
        return ClientboundSetPassengersPacket.STREAM_CODEC.decode(buf)
    }

    private companion object {
        /**
         * Marker stands and players both draw their name 0.5 above their top, so a stand riding one
         * line height above the player's head puts its name exactly one line above the vanilla one.
         * A name's background is 10px at 0.025 per pixel, so stacked lines touch like vanilla's do.
         */
        const val LINE_HEIGHT = 0.25f
        const val REMOUNT_TICKS = 20
    }
}
