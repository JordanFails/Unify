package me.jordanfails.unify.menu.menus.listener

import me.jordanfails.unify.menu.Menu
import me.jordanfails.unify.menu.menus.SelectItemStackMenu
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.PlayerInventory

object SelectItemListeners : Listener {

    @EventHandler
    fun onInventoryClickEvent(event: InventoryClickEvent) {
        val openMenu = Menu.currentlyOpenedMenus[event.whoClicked.uniqueId]
        if (openMenu is SelectItemStackMenu) {
            if (event.clickedInventory != null && event.clickedInventory is PlayerInventory && event.currentItem != null && event.currentItem!!.type != Material.AIR) {
                val currentItem = event.currentItem!!.clone()
                event.currentItem = null
                event.whoClicked.inventory.addItem(currentItem)

                openMenu.select.invoke(currentItem)

                if (Menu.currentlyOpenedMenus[event.whoClicked.uniqueId] === openMenu) {
                    openMenu.update(event.whoClicked as Player)
                }
            }
        }
    }

}