package me.jordanfails.unify.utils

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World

object LocationUtils {

    @JvmStatic
    fun locToStr(loc: Location): String {
        return "(${loc.blockX}, ${loc.blockY}, ${loc.blockZ})"
    }

    @JvmStatic
    fun serialize(location: Location?): String {
        if (location == null) return "empty"
        return "${location.world?.name}:${location.x}:${location.y}:${location.z}:${location.yaw}:${location.pitch}"
    }

    @JvmStatic
    fun deserialize(source: String?): Location? {
        if (source == null) return null

        val split = source.split(":")
        val world: World = Bukkit.getServer().getWorld(split[0]) ?: return null

        return Location(
            world,
            split[1].toDouble(),
            split[2].toDouble(),
            split[3].toDouble(),
            split[4].toFloat(),
            split[5].toFloat()
        )
    }
}