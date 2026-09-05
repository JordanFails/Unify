package me.jordanfails.unify.menu.buttons

import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.menu.menus.menus.InteractiveMenu
import me.jordanfails.unify.menu.menus.SlotBehavior
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack

/**
 * Displays the live contents of an [InteractiveMenu] item slot.
 *
 * When empty, may show a ghost placeholder from
 * [InteractiveMenu.getEmptyPlaceholder] (not actually takeable).
 */
class InteractiveSlotButton(
    private val menu: InteractiveMenu,
    private val slot: Int,
    private val behavior: SlotBehavior
) : Button() {

    override fun getButtonItem(player: Player): ItemStack {
        val stored = menu.getSlotItem(slot)
        if (stored != null && stored.type != Material.AIR) {
            return stored
        }
        return menu.getEmptyPlaceholder(player, slot)
            ?: ItemStack(Material.AIR)
    }

    override fun isMoveable(): Boolean =
        behavior == SlotBehavior.INTERACTIVE || behavior == SlotBehavior.INPUT_ONLY

    override fun isRemovable(): Boolean =
        behavior == SlotBehavior.INTERACTIVE || behavior == SlotBehavior.OUTPUT_ONLY

    /** Clicks are fully handled by [me.jordanfails.unify.menu.listener.ButtonListeners]. */
    override fun shouldCancel(player: Player, slot: Int, clickType: ClickType): Boolean = true

    override fun clicked(player: Player, slot: Int, clickType: ClickType, view: InventoryView) {
        // no-op — ButtonListeners drives InteractiveMenu
    }
}
