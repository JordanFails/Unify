package me.jordanfails.unify.utils

import org.bukkit.Bukkit
import org.bukkit.Location

object LocationUtils {
    /**
     * Deserialize a string into a Bukkit Location.
     *
     * Format: world;x;y;z;yaw;pitch;
     */
    fun deserializeString(serialized: String): Location? {
        val parts = serialized.split(";").filter { it.isNotEmpty() }
        if (parts.size < 6) return null

        val world = Bukkit.getWorld(parts[0]) ?: return null
        val x = parts[1].toDoubleOrNull() ?: 0.0
        val y = parts[2].toDoubleOrNull() ?: 0.0
        val z = parts[3].toDoubleOrNull() ?: 0.0
        val yaw = parts[4].toFloatOrNull() ?: 0f
        val pitch = parts[5].toFloatOrNull() ?: 0f

        return Location(world, x, y, z, yaw, pitch)
    }

    /**
     * Serialize a Bukkit Location back into a string.
     */
    fun serialize(location: Location?): String {
        if (location == null) return ""
        return "${location.world?.name};" +
                "${location.x};" +
                "${location.y};" +
                "${location.z};" +
                "${location.yaw};" +
                "${location.pitch};"
    }

    /**
     * Returns a clean, human-readable string from a Location.
     *
     * Example: "world @ (100.0, 64.0, -200.0) yaw=90.0 pitch=0.0"
     */
    fun toString(location: Location?): String {
        if (location == null) return "null"

        val worldName = location.world?.name ?: "null"
        val x = "%.2f".format(location.x)
        val y = "%.2f".format(location.y)
        val z = "%.2f".format(location.z)
        val yaw = "%.1f".format(location.yaw)
        val pitch = "%.1f".format(location.pitch)

        return "$worldName @ ($x, $y, $z) yaw=$yaw pitch=$pitch"
    }
}