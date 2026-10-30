package me.jordanfails.unify.menu.menus.listener

import me.jordanfails.unify.menu.Menu
import me.jordanfails.unify.menu.menus.menus.SelectItemStackMenu
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.EventPriority
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.PlayerInventory

object SelectItemListeners : Listener {

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onInventoryClickEvent(event: InventoryClickEvent) {
        val menu = Menu.currentlyOpenedMenus[event.whoClicked.uniqueId] as? SelectItemStackMenu ?: return
        if (!menu.ownsInventory(event.view.topInventory)) return
        if (event.clickedInventory !is PlayerInventory) return
        event.isCancelled = true
        val item = event.currentItem?.takeUnless { it.type == Material.AIR }?.clone() ?: return
        menu.select.invoke(item)
    }
}
