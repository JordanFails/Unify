package me.jordanfails.unify.preview

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player

/** A visual block state. Legacy data is used on pre-1.13 servers. */
class PreviewBlock @JvmOverloads constructor(
    val material: Material,
    val legacyData: Byte = 0,
    val blockData: String? = null,
) {
    private val sender: (Player, Location) -> Unit

    init {
        require(material.isBlock) { "Preview material must be a block" }
        sender = if (WorldPreviews.supportsBlockData) {
            ModernPreviewBlocks.sender(material, blockData)
        } else {
            require(blockData == null) { "Block-data strings require a modern server" }
            val legacySender: (Player, Location) -> Unit = { player, location ->
                player.sendBlockChange(location, material, legacyData)
            }
            legacySender
        }
    }

    internal fun send(player: Player, location: Location) = sender(player, location)
}
