package me.jordanfails.unify.menu.listener

import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.menu.Menu
import me.jordanfails.unify.menu.menus.InteractiveMenu
import me.jordanfails.unify.menu.menus.SlotBehavior
import me.jordanfails.unify.utils.ItemBuilder
import org.bukkit.event.Listener
import org.bukkit.entity.Player
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.inventory.*
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.Bukkit
import kotlin.collections.get

object ButtonListeners : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onInventoryDragEvent(event: InventoryDragEvent) {
        val openMenu = Menu.currentlyOpenedMenus[event.whoClicked.uniqueId]
        if (openMenu != null) {
            if (event.inventory != event.view.topInventory) {
                event.isCancelled = true
                return
            }

            // check if dragging in both the menu and their own inventory
            // by comparing max used slot to max slots
            if (event.newItems.maxByOrNull { it.key }!!.key >= event.view.topInventory.size) {
                event.isCancelled = true
                return
            }

            // Check if any dragged slots contain non-moveable buttons
            val draggedSlots = event.rawSlots.filter { it < event.view.topInventory.size }
            val hasNonMoveableButton = draggedSlots.any { slot ->
                val button = openMenu.buttons[slot]
                button != null && !button.isMoveable()
            }

            if (hasNonMoveableButton) {
                event.isCancelled = true
                return
            }

            if (openMenu.acceptsDraggedItems(event.whoClicked as Player, event.newItems)) {
                if (openMenu.updateAfterClick) {
                    openMenu.openMenu(event.whoClicked as Player)
                }
            } else {
                event.isCancelled = true
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onButtonPress(event: InventoryClickEvent) {
        val player = event.whoClicked as Player

        val openMenu = Menu.currentlyOpenedMenus[player.uniqueId]
        if (openMenu != null) {
            if (event.click == ClickType.DOUBLE_CLICK) {
                event.isCancelled = true
                return
            }

            // handle bottom mechanics (edit own inventory)
            if (!openMenu.keepBottomMechanics) {
                if (event.clickedInventory != event.view.topInventory) {
                    event.isCancelled = true
                    return
                }
            }

            if (Button.clickCooldown.containsKey(player.uniqueId)) {
                if (System.currentTimeMillis() < Button.clickCooldown[player.uniqueId]!!) {
                    event.isCancelled = true
                    return
                }
            }

            // handle items being inserted via cursor
            if (event.cursor != null && event.cursor!!.type != Material.AIR && (event.click == ClickType.LEFT || event.click == ClickType.RIGHT || event.click == ClickType.MIDDLE)) {
                if (event.clickedInventory == event.view.topInventory) {
                    event.isCancelled = true

                    val itemInserted = when (event.click) {
                        ClickType.LEFT -> {
                            event.cursor
                        }
                        ClickType.RIGHT -> {
                            ItemBuilder(event.cursor!!.clone()).amount(1).build()
                        }
                        ClickType.MIDDLE -> {
                            val half = (event.cursor!!.amount / 2).coerceAtLeast(1)
                            ItemBuilder(event.cursor!!.clone()).amount(half).build()
                        }
                        else -> {
                            event.cursor
                        }
                    }

                    if (openMenu.acceptsInsertedItem(player, itemInserted!!.clone(), event.slot)) {
                        when (event.click) {
                            ClickType.LEFT -> {
                                event.cursor = null
                            }
                            ClickType.RIGHT -> {
                                if (event.cursor?.amount == 1) {
                                    event.cursor = null
                                } else {
                                    event.cursor =
                                        ItemBuilder(event.cursor!!.clone()).amount(event.cursor!!.amount - 1).build()
                                }
                            }
                            ClickType.MIDDLE -> {
                                val half = (event.cursor!!.amount - (event.cursor!!.amount / 2).coerceAtLeast(1)).coerceAtLeast(1)
                                event.cursor = ItemBuilder(event.cursor!!.clone()).amount(half).build()
                            }
                            else -> {
                                event.cursor = null
                            }
                        }

                        refreshOpenMenuIfSame(player, openMenu)
                    }

                    return
                }
            }

            // handle items being inserted via shift-clicking
            if (event.slot != event.rawSlot) {
                if (event.click == ClickType.SHIFT_LEFT || event.click == ClickType.SHIFT_RIGHT) {
                    event.isCancelled = true

                    if (event.currentItem != null) {
                        if (openMenu.acceptsShiftClickedItem(event.whoClicked as Player, event.currentItem!!)) {
                            event.currentItem = null
                            refreshOpenMenuIfSame(player, openMenu)
                        }
                    }
                }
                return
            }

            // handle button
            if (openMenu.buttons.containsKey(event.slot)) {
                val button = openMenu.buttons[event.slot]!!
                val isClickingInMenu = event.clickedInventory == event.view.topInventory

                // Special handling for InteractiveMenu
                if (openMenu is InteractiveMenu && isClickingInMenu) {
                    val behavior = openMenu.getSlotBehavior(event.slot)
                    
                    // Handle based on behavior
                    if (behavior != SlotBehavior.FIXED) {
                        val handled = handleInteractiveSlotClick(event, player, openMenu, behavior)
                        if (handled) {
                            refreshOpenMenuIfSame(player, openMenu)
                            return
                        }
                    }
                }

                // Check if button allows the action based on moveable/removable properties
                if (isClickingInMenu) {
                    val shouldBlockAction = when (event.action) {
                        // Block all removal/movement actions if not removable
                        InventoryAction.PICKUP_ALL,
                        InventoryAction.PICKUP_HALF,
                        InventoryAction.PICKUP_ONE,
                        InventoryAction.PICKUP_SOME,
                        InventoryAction.COLLECT_TO_CURSOR -> {
                            !button.isRemovable()
                        }

                        // Block shift-click movement if not removable
                        InventoryAction.MOVE_TO_OTHER_INVENTORY -> {
                            !button.isRemovable()
                        }

                        // Block swapping if not moveable
                        InventoryAction.SWAP_WITH_CURSOR,
                        InventoryAction.HOTBAR_SWAP -> {
                            !button.isMoveable()
                        }

                        // Block placing items over non-moveable buttons
                        InventoryAction.PLACE_ALL,
                        InventoryAction.PLACE_ONE,
                        InventoryAction.PLACE_SOME -> {
                            !button.isMoveable()
                        }

                        else -> false
                    }

                    if (shouldBlockAction) {
                        event.isCancelled = true
                        button.clicked(player, event.slot, event.click, event.view)

                        if (Menu.currentlyOpenedMenus.containsKey(player.uniqueId)) {
                            val newMenu = Menu.currentlyOpenedMenus[player.uniqueId]
                            if (newMenu === openMenu && newMenu.updateAfterClick) {
                                newMenu.openMenu(player)
                            }
                        }

                        Bukkit.getScheduler().scheduleSyncDelayedTask(UnifyCore.instance, {
                            player.updateInventory()
                        }, 1L)

                        return
                    }
                }

                val cancel = button.shouldCancel(player, event.slot, event.click)

                if (!cancel && (event.click == ClickType.SHIFT_LEFT || event.click == ClickType.SHIFT_RIGHT)) {
                    // Handle shift-click based on removable property
                    if (button.isRemovable()) {
                        event.isCancelled = true

                        if (event.currentItem != null && event.currentItem!!.type != Material.AIR) {
                            val removedItem = event.currentItem!!.clone()
                            player.inventory.addItem(removedItem)
                            event.view.topInventory.setItem(event.slot, null)
                            
                            // Sync with InteractiveMenu if applicable
                            if (openMenu is InteractiveMenu) {
                                openMenu.handleItemRemoval(player, event.slot, removedItem)
                            }
                        }
                    } else {
                        event.isCancelled = true
                    }
                } else {
                    event.isCancelled = cancel
                }

                button.clicked(player, event.slot, event.click, event.view)

                // check if player is still in the same menu and needs to update
                if (Menu.currentlyOpenedMenus.containsKey(player.uniqueId)) {
                    val newMenu = Menu.currentlyOpenedMenus[player.uniqueId]
                    if (newMenu === openMenu && newMenu.updateAfterClick) {
                        newMenu.openMenu(player)
                    }
                }

                if (event.isCancelled) {
                    Bukkit.getScheduler().scheduleSyncDelayedTask(UnifyCore.instance, {
                        player.updateInventory()
                    }, 1L)
                }

                return // we return here so we don't reach the block of code below
            } else {
                if (event.clickedInventory == event.view.topInventory) {
                    event.isCancelled = true
                    // Call the background click handler for menus that need to react to clicks on empty/filler slots
                    openMenu.onBackgroundClick(player, event.slot, event.click)
                }
            }

            // handle non-cancelling menu
            if (event.click == ClickType.SHIFT_LEFT || event.click == ClickType.SHIFT_RIGHT) {
                event.isCancelled = true

                if (openMenu.nonCancelling && event.currentItem != null) {
                    if (event.slot == event.rawSlot && event.clickedInventory == event.view.topInventory) {
                        player.openInventory.topInventory.addItem(event.currentItem!!)
                        event.currentItem = null
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as Player

        val openMenu = Menu.currentlyOpenedMenus[player.uniqueId]
        if (openMenu != null) {
            openMenu.closed = true

            if (event.view.cursor != null) {
                event.player.inventory.addItem(event.view.cursor)
                event.view.cursor = null
            }

            openMenu.onClose(player, openMenu.manualClose)
            openMenu.manualClose = false

            Menu.currentlyOpenedMenus.remove(player.uniqueId)
        }
    }

    private fun refreshOpenMenuIfSame(player: Player, menu: Menu) {
        if (Menu.currentlyOpenedMenus.containsKey(player.uniqueId)) {
            val openMenu = Menu.currentlyOpenedMenus[player.uniqueId]
            if (openMenu === menu && openMenu.updateAfterClick) {
                openMenu.openMenu(player)
            }
        }
    }

    @EventHandler
    fun onPlayerQuitEvent(event: PlayerQuitEvent) {
        Button.clickCooldown.remove(event.player.uniqueId)
    }

    /**
     * Handles click events for InteractiveMenu slots.
     * Returns true if the click was fully handled and the event should return.
     */
    private fun handleInteractiveSlotClick(
        event: InventoryClickEvent,
        player: Player,
        menu: InteractiveMenu,
        behavior: SlotBehavior
    ): Boolean {
        event.isCancelled = true
        val slot = event.slot
        val cursor = event.cursor
        val currentItem = menu.getSlotItem(slot)
        val inv = event.view.topInventory

        val canPlace = behavior == SlotBehavior.INTERACTIVE || behavior == SlotBehavior.INPUT_ONLY
        val canTake = behavior == SlotBehavior.INTERACTIVE || behavior == SlotBehavior.OUTPUT_ONLY

        when (event.action) {
            // Picking up items (taking from slot)
            InventoryAction.PICKUP_ALL,
            InventoryAction.PICKUP_HALF,
            InventoryAction.PICKUP_ONE,
            InventoryAction.PICKUP_SOME -> {
                if (!canTake || currentItem == null || currentItem.type == Material.AIR) {
                    return true
                }

                val takeAmount = when (event.action) {
                    InventoryAction.PICKUP_HALF -> (currentItem.amount + 1) / 2
                    InventoryAction.PICKUP_ONE -> 1
                    else -> currentItem.amount
                }

                val takenItem = currentItem.clone().apply { amount = takeAmount }
                val remaining = if (takeAmount >= currentItem.amount) null else currentItem.clone().apply { 
                    amount = currentItem.amount - takeAmount 
                }

                event.cursor = takenItem
                inv.setItem(slot, remaining)
                
                if (remaining == null) {
                    menu.handleItemRemoval(player, slot, currentItem)
                } else {
                    menu.setSlotItem(slot, remaining)
                }
                
                Bukkit.getScheduler().scheduleSyncDelayedTask(UnifyCore.instance, {
                    player.updateInventory()
                }, 1L)
                return true
            }

            // Placing items (putting into slot)
            InventoryAction.PLACE_ALL,
            InventoryAction.PLACE_ONE,
            InventoryAction.PLACE_SOME -> {
                if (!canPlace || cursor == null || cursor.type == Material.AIR) {
                    return true
                }

                // Check if slot is empty
                if (currentItem != null && currentItem.type != Material.AIR) {
                    return true
                }

                // Check item filter
                if (!menu.isItemAllowedInSlot(slot, cursor)) {
                    Button.playFail(player)
                    return true
                }

                val placeAmount = if (event.action == InventoryAction.PLACE_ONE) 1 else cursor.amount
                val placedItem = cursor.clone().apply { amount = placeAmount }
                val remainingCursor = if (placeAmount >= cursor.amount) null else cursor.clone().apply { 
                    amount = cursor.amount - placeAmount 
                }

                inv.setItem(slot, placedItem)
                event.cursor = remainingCursor
                menu.handleItemPlacement(player, slot, placedItem)
                
                Bukkit.getScheduler().scheduleSyncDelayedTask(UnifyCore.instance, {
                    player.updateInventory()
                }, 1L)
                return true
            }

            // Swapping items
            InventoryAction.SWAP_WITH_CURSOR -> {
                if (cursor == null || cursor.type == Material.AIR) {
                    return true
                }

                // Need both place and take ability
                if (!canPlace || !canTake) {
                    return true
                }

                // Check item filter for incoming item
                if (!menu.isItemAllowedInSlot(slot, cursor)) {
                    Button.playFail(player)
                    return true
                }

                val newItem = cursor.clone()
                event.cursor = currentItem
                inv.setItem(slot, newItem)
                
                if (currentItem != null && currentItem.type != Material.AIR) {
                    menu.handleItemRemoval(player, slot, currentItem)
                }
                menu.handleItemPlacement(player, slot, newItem)
                
                Bukkit.getScheduler().scheduleSyncDelayedTask(UnifyCore.instance, {
                    player.updateInventory()
                }, 1L)
                return true
            }

            // Shift-click to move to player inventory
            InventoryAction.MOVE_TO_OTHER_INVENTORY -> {
                if (!canTake || currentItem == null || currentItem.type == Material.AIR) {
                    return true
                }

                val leftover = player.inventory.addItem(currentItem.clone())
                if (leftover.isEmpty()) {
                    inv.setItem(slot, null)
                    menu.handleItemRemoval(player, slot, currentItem)
                } else {
                    // Some items couldn't fit, update with remaining
                    val remaining = leftover.values.first()
                    inv.setItem(slot, remaining)
                    menu.setSlotItem(slot, remaining)
                }
                
                Bukkit.getScheduler().scheduleSyncDelayedTask(UnifyCore.instance, {
                    player.updateInventory()
                }, 1L)
                return true
            }

            else -> return false
        }
    }
}