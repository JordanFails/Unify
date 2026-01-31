package me.jordanfails.unify.menu.buttons

import com.cryptomorin.xseries.XMaterial
import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.menu.menus.InteractiveMenu
import me.jordanfails.unify.menu.menus.SlotBehavior
import me.jordanfails.unify.utils.ItemBuilder
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack

/**
 * A button for interactive slots in an [InteractiveMenu].
 *
 * This button dynamically displays the current contents of the slot
 * and adjusts its moveable/removable properties based on the [SlotBehavior].
 *
 * @param menu The InteractiveMenu this button belongs to
 * @param slot The slot number this button represents
 * @param behavior The behavior of this slot (INTERACTIVE, INPUT_ONLY, OUTPUT_ONLY)
 * @param emptyPlaceholder Optional item to show when the slot is empty
 */
class InteractiveSlotButton(
    private val menu: InteractiveMenu,
    private val slot: Int,
    private val behavior: SlotBehavior,
    private val emptyPlaceholder: ItemStack? = null
) : Button() {

    override fun getButtonItem(player: Player): ItemStack {
        val storedItem = menu.getSlotItem(slot)

        // Return the stored item if present
        if (storedItem != null && storedItem.type != XMaterial.AIR.parseMaterial()) {
            return storedItem
        }

        // Return empty placeholder if configured
        if (emptyPlaceholder != null) {
            return emptyPlaceholder
        }

        // Default: show a subtle indicator based on behavior
        return when (behavior) {
            SlotBehavior.INTERACTIVE -> ItemBuilder(XMaterial.LIGHT_GRAY_STAINED_GLASS_PANE)
                .name(" ")
                .build()
            SlotBehavior.INPUT_ONLY -> ItemBuilder(XMaterial.LIME_STAINED_GLASS_PANE)
                .name(" ")
                .build()
            SlotBehavior.OUTPUT_ONLY -> ItemBuilder(XMaterial.ORANGE_STAINED_GLASS_PANE)
                .name(" ")
                .build()
            SlotBehavior.FIXED -> ItemBuilder(XMaterial.AIR).build()
        }
    }

    /**
     * Determines if items can be placed over this button (for swapping).
     * Returns true for INTERACTIVE and INPUT_ONLY slots.
     */
    override fun isMoveable(): Boolean {
        return behavior == SlotBehavior.INTERACTIVE || behavior == SlotBehavior.INPUT_ONLY
    }

    /**
     * Determines if the item in this slot can be taken out.
     * Returns true for INTERACTIVE and OUTPUT_ONLY slots.
     */
    override fun isRemovable(): Boolean {
        return behavior == SlotBehavior.INTERACTIVE || behavior == SlotBehavior.OUTPUT_ONLY
    }

    override fun shouldCancel(player: Player, slot: Int, clickType: ClickType): Boolean {
        // Let the ButtonListeners handle the logic based on behavior
        return false
    }

    override fun clicked(player: Player, slot: Int, clickType: ClickType, view: InventoryView) {
        // Click handling is done in ButtonListeners for InteractiveMenu
    }
}
