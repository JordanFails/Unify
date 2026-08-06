package me.jordanfails.unify.menu.anvil

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Snapshot of anvil contents and the interacting player.
 */
data class AnvilState(
    val player: Player,
    val leftItem: ItemStack,
    val rightItem: ItemStack,
    val outputItem: ItemStack,
    val text: String,
) {
    companion object {
        private val AIR = ItemStack(Material.AIR)

        fun from(session: AnvilSession): AnvilState {
            val inv = session.handle.inventory
            val left = inv.getItem(AnvilSlot.INPUT_LEFT)?.clone() ?: AIR.clone()
            val right = inv.getItem(AnvilSlot.INPUT_RIGHT)?.clone() ?: AIR.clone()
            val output = inv.getItem(AnvilSlot.OUTPUT)?.clone() ?: AIR.clone()
            return AnvilState(
                player = session.player,
                leftItem = left,
                rightItem = right,
                outputItem = output,
                text = resolveText(session.handle, left, output),
            )
        }

        fun resolveText(handle: AnvilHandle, left: ItemStack? = null, output: ItemStack? = null): String {
            val rename = handle.getRenameText()
            if (rename.isNotEmpty()) return rename

            val out = output ?: handle.inventory.getItem(AnvilSlot.OUTPUT)
            if (out != null && out.type != Material.AIR && out.hasItemMeta()) {
                val name = out.itemMeta?.displayName
                if (!name.isNullOrEmpty()) return name
            }

            val leftItem = left ?: handle.inventory.getItem(AnvilSlot.INPUT_LEFT)
            if (leftItem != null && leftItem.type != Material.AIR && leftItem.hasItemMeta()) {
                val name = leftItem.itemMeta?.displayName
                if (!name.isNullOrEmpty()) return name
            }

            return ""
        }
    }
}
