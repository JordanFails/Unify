package me.jordanfails.unify.menu.buttons

import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.menu.history.MenuHistory
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.event.inventory.ClickType
import org.bukkit.entity.Player
import org.bukkit.inventory.InventoryView

class BackButton(
    private val destination: String? = null,
    private val closeFirst: Boolean = true,
    private val callback: (Player) -> Unit
) : Button() {

    constructor(callback: (Player) -> Unit) : this(null, true, callback)

    constructor(destination: String?, callback: (Player) -> Unit) : this(destination, true, callback)

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
        if (closeFirst) {
            player.closeInventory()
        }
        callback.invoke(player)
    }

    companion object {
        /**
         * Back button wired to [MenuHistory]. Falls back to closing the inventory
         * when there is nothing to return to.
         *
         * Does not close first — [MenuHistory.back] re-opens the previous menu.
         */
        @JvmStatic
        @JvmOverloads
        fun history(destination: String? = null): BackButton =
            BackButton(destination, closeFirst = false) { player ->
                if (!MenuHistory.back(player)) {
                    player.closeInventory()
                }
            }
    }
}
