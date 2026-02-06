package me.jordanfails.unify.menu.buttons

import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.menu.menus.menus.InteractiveMenu
import me.jordanfails.unify.menu.menus.SlotBehavior
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack

class InteractiveSlotButton(
    private val menu: InteractiveMenu,
    private val slot: Int,
    private val behavior: SlotBehavior,
    private val emptyPlaceholder: ItemStack? = null
) : Button() {

    override fun getButtonItem(player: Player): ItemStack {
        val storedItem = menu.getSlotItem(slot)

        // Show actual item if the slot has one
        if (storedItem != null && storedItem.type != Material.AIR) {
            return storedItem
        }

        // If a placeholder is explicitly set, show it
        if (emptyPlaceholder != null) {
            return emptyPlaceholder
        }

        // Otherwise, leave it empty — no glass, no indicators
        return ItemStack(Material.AIR)
    }

    override fun isMoveable(): Boolean {
        return behavior == SlotBehavior.INTERACTIVE || behavior == SlotBehavior.INPUT_ONLY
    }

    override fun isRemovable(): Boolean {
        return behavior == SlotBehavior.INTERACTIVE || behavior == SlotBehavior.OUTPUT_ONLY
    }

    override fun shouldCancel(player: Player, slot: Int, clickType: ClickType): Boolean = false

    override fun clicked(player: Player, slot: Int, clickType: ClickType, view: InventoryView) {
        // Click handling handled elsewhere (InteractiveMenu)
    }
}