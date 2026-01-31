package me.jordanfails.unify.utils

import org.bukkit.Location
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import kotlin.math.*

object AngleUtils {

    /** Smallest difference between two yaws in degrees (0–180 range). */
    fun yawDiff(a: Double, b: Double): Double {
        val diff = (a - b) % 360
        val wrapped = (diff + 540) % 360 - 180 // Normalize to [-180, 180)
        return abs(wrapped)
    }

    /** Checks if 'b' is generally in front of 'a'. */
    fun faceTo(a: Location, b: Location): Boolean {
        val dx = b.x - a.x
        val dz = b.z - a.z
        val angleToTarget = Math.toDegrees(atan2(-dx, dz))
        return yawDiff(a.yaw.toDouble(), angleToTarget) <= 90
    }

    /** Quick distance check using eye or base position. */
    fun isInRange(player: Player, target: Player, range: Double): Boolean {
        return player.eyeLocation.distance(target.location) <= range ||
                player.location.distance(target.location) <= range
    }

    /** Map of cardinal/intercardinal BlockFaces to yaw angles. */
    private val FACE_YAWS: Map<BlockFace, Int> = mapOf(
        BlockFace.SOUTH to 0,
        BlockFace.SOUTH_WEST to 45,
        BlockFace.WEST to 90,
        BlockFace.NORTH_WEST to 135,
        BlockFace.NORTH to 180,
        BlockFace.NORTH_EAST to -135,
        BlockFace.EAST to -90,
        BlockFace.SOUTH_EAST to -45
    )

    /** Converts a BlockFace to a normalized yaw angle. */
    fun faceToYaw(face: BlockFace?): Int {
        val angle = FACE_YAWS[face] ?: 0
        return wrapAngle(angle)
    }

    private fun wrapAngle(angle: Int): Int {
        var wrapped = angle
        while (wrapped <= -180) wrapped += 360
        while (wrapped > 180) wrapped -= 360
        return wrapped
    }
}