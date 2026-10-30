package me.jordanfails.unify.preview

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player

// Kept separate so legacy servers never need to load BlockData signatures.
internal object ModernPreviewBlocks {
    fun sender(material: Material, serialized: String?): (Player, Location) -> Unit {
        val data = if (serialized == null) material.createBlockData() else Bukkit.createBlockData(serialized)
        require(data.material == material) { "Block data must match the preview material" }
        return { player, location -> player.sendBlockChange(location, data) }
    }

    fun restore(player: Player, location: Location) {
        player.sendBlockChange(location, location.block.blockData)
    }
}
