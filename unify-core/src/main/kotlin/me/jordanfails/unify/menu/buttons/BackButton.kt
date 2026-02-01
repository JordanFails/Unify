package me.jordanfails.unify.menu.buttons

import me.jordanfails.unify.menu.Button
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.event.inventory.ClickType
import org.bukkit.entity.Player
import org.bukkit.inventory.InventoryView

class BackButton(
    private val destination: String? = null,
    private val callback: (Player) -> Unit
) : Button() {

    constructor(callback:(Player) -> Unit) : this(null, callback)

    override fun getName(player: Player): String {
        return "${ChatColor.GREEN}Go Back"
    }

    override fun getDescription(player: Player): MutableList<String> {
        return if (destination == null) {
            mutableListOf()
        } else {
            mutableListOf("${ChatColor.GRAY}To $destination")
        }
    }

    override fun getMaterial(player: Player): Material {
        return Material.ARROW
    }

    override fun clicked(player: Player, slot: Int, clickType: ClickType, view: InventoryView) {
        playNeutral(player)
        player.closeInventory()

        callback.invoke(player)
    }

}