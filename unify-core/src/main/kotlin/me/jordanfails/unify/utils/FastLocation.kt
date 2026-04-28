package me.jordanfails.unify.utils

import org.bukkit.Bukkit
import org.bukkit.Location

class FastLocation {
    var x = 0.0
    var y = 0.0
    var z = 0.0
    var yaw = 0.0F
    var pitch = 0.0F
    var worldName: String? = null

    constructor(location: Location) {
        x = location.x
        y = location.y
        z = location.z
        yaw = location.yaw
        pitch = location.pitch
        worldName = location.world?.name
    }

    fun toBukkitLocation(): Location {
        return Location(
            Bukkit.getWorld(worldName!!),
            x,
            y,
            z,
            yaw,
            pitch
        )
    }

    fun fromBukkitLocation(location: Location): FastLocation {
        return this.apply {
            x = location.x
            y = location.y
            z = location.z
            yaw = location.yaw
            pitch = location.pitch
            worldName = location.world?.name
        }
    }
}