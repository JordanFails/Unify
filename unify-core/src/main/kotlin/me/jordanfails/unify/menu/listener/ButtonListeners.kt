package me.jordanfails.unify.menu.listener

import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.menu.Menu
import me.jordanfails.unify.menu.history.MenuHistory
import me.jordanfails.unify.menu.menus.SlotBehavior
import me.jordanfails.unify.menu.menus.menus.InteractiveMenu
import me.jordanfails.unify.utils.ItemBuilder
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack

object ButtonListeners : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onInventoryDragEvent(event: InventoryDragEvent) {
        val openMenu = Menu.currentlyOpenedMenus[event.whoClicked.uniqueId] ?: return
        val player = event.whoClicked as Player

        if (event.inventory != event.view.topInventory) {
            event.isCancelled = true
            return
        }

        if (event.newItems.maxByOrNull { it.key }!!.key >= event.view.topInventory.size) {
            event.isCancelled = true
            return
        }

        val draggedSlots = event.rawSlots.filter { it < event.view.topInventory.size }

        if (openMenu is InteractiveMenu) {
            // Only allow drag into placeable item slots
            val allowed = draggedSlots.all { openMenu.canPlaceIn(it) }
            if (!allowed) {
                event.isCancelled = true
                return
            }
            if (openMenu.acceptsDraggedItems(player, event.newItems)) {
                // Cursor/slots already updated by Bukkit; sync our state from newItems
                // acceptsDraggedItems already wrote via placeIntoSlot
                delayedUpdate(player)
            } else {
                event.isCancelled = true
            }
            return
        }

        val hasNonMoveableButton = draggedSlots.any { slot ->
            val button = openMenu.buttons[slot]
            button != null && !button.isMoveable()
        }

        if (hasNonMoveableButton) {
            event.isCancelled = true
            return
        }

        if (openMenu.acceptsDraggedItems(player, event.newItems)) {
            if (openMenu.updateAfterClick) {
                openMenu.openMenu(player)
            }
        } else {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onButtonPress(event: InventoryClickEvent) {
        val player = event.whoClicked as Player
        val openMenu = Menu.currentlyOpenedMenus[player.uniqueId] ?: return

        if (event.click == ClickType.DOUBLE_CLICK) {
            event.isCancelled = true
            return
        }

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

        // ── InteractiveMenu: dedicated routing (before generic insert logic) ──
        if (openMenu is InteractiveMenu) {
            if (handleInteractiveMenuClick(event, player, openMenu)) {
                return
            }
        }

        // ── Generic: insert via cursor onto a top slot ───────────────────────
        if (event.cursor != null && event.cursor!!.type != Material.AIR &&
            (event.click == ClickType.LEFT || event.click == ClickType.RIGHT || event.click == ClickType.MIDDLE)
        ) {
            if (event.clickedInventory == event.view.topInventory) {
                event.isCancelled = true

                val itemInserted = when (event.click) {
                    ClickType.LEFT -> event.cursor
                    ClickType.RIGHT -> ItemBuilder(event.cursor!!.clone()).amount(1).build()
                    ClickType.MIDDLE -> {
                        val half = (event.cursor!!.amount / 2).coerceAtLeast(1)
                        ItemBuilder(event.cursor!!.clone()).amount(half).build()
                    }
                    else -> event.cursor
                }

                if (openMenu.acceptsInsertedItem(player, itemInserted!!.clone(), event.slot)) {
                    when (event.click) {
                        ClickType.LEFT -> event.cursor = null
                        ClickType.RIGHT -> {
                            if (event.cursor?.amount == 1) {
                                event.cursor = null
                            } else {
                                event.cursor = ItemBuilder(event.cursor!!.clone())
                                    .amount(event.cursor!!.amount - 1).build()
                            }
                        }
                        ClickType.MIDDLE -> {
                            val half = (event.cursor!!.amount - (event.cursor!!.amount / 2).coerceAtLeast(1))
                                .coerceAtLeast(1)
                            event.cursor = ItemBuilder(event.cursor!!.clone()).amount(half).build()
                        }
                        else -> event.cursor = null
                    }
                    refreshOpenMenuIfSame(player, openMenu)
                }
                return
            }
        }

        // ── Generic: shift-click from player inventory into menu ────────────
        if (event.slot != event.rawSlot) {
            if (event.click == ClickType.SHIFT_LEFT || event.click == ClickType.SHIFT_RIGHT) {
                event.isCancelled = true
                if (event.currentItem != null) {
                    if (openMenu.acceptsShiftClickedItem(player, event.currentItem!!)) {
                        event.currentItem = null
                        refreshOpenMenuIfSame(player, openMenu)
                    }
                }
            }
            return
        }

        // ── Button click ────────────────────────────────────────────────────
        if (openMenu.buttons.containsKey(event.slot)) {
            val button = openMenu.buttons[event.slot]!!
            val isClickingInMenu = event.clickedInventory == event.view.topInventory

            if (isClickingInMenu) {
                val shouldBlockAction = when (event.action) {
                    InventoryAction.PICKUP_ALL,
                    InventoryAction.PICKUP_HALF,
                    InventoryAction.PICKUP_ONE,
                    InventoryAction.PICKUP_SOME,
                    InventoryAction.COLLECT_TO_CURSOR -> !button.isRemovable()

                    InventoryAction.MOVE_TO_OTHER_INVENTORY -> !button.isRemovable()

                    InventoryAction.SWAP_WITH_CURSOR,
                    InventoryAction.HOTBAR_SWAP -> !button.isMoveable()

                    InventoryAction.PLACE_ALL,
                    InventoryAction.PLACE_ONE,
                    InventoryAction.PLACE_SOME -> !button.isMoveable()

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

                    delayedUpdate(player)
                    return
                }
            }

            val cancel = button.shouldCancel(player, event.slot, event.click)

            if (!cancel && (event.click == ClickType.SHIFT_LEFT || event.click == ClickType.SHIFT_RIGHT)) {
                if (button.isRemovable()) {
                    event.isCancelled = true
                    if (event.currentItem != null && event.currentItem!!.type != Material.AIR) {
                        val removedItem = event.currentItem!!.clone()
                        player.inventory.addItem(removedItem)
                        event.view.topInventory.setItem(event.slot, null)
                    }
                } else {
                    event.isCancelled = true
                }
            } else {
                event.isCancelled = cancel
            }

            button.clicked(player, event.slot, event.click, event.view)

            if (Menu.currentlyOpenedMenus.containsKey(player.uniqueId)) {
                val newMenu = Menu.currentlyOpenedMenus[player.uniqueId]
                if (newMenu === openMenu && newMenu.updateAfterClick) {
                    newMenu.openMenu(player)
                }
            }

            if (event.isCancelled) {
                delayedUpdate(player)
            }
            return
        } else {
            if (event.clickedInventory == event.view.topInventory) {
                event.isCancelled = true
                openMenu.onBackgroundClick(player, event.slot, event.click)
            }
        }

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

    /**
     * @return true if the click was fully handled for an InteractiveMenu
     */
    private fun handleInteractiveMenuClick(
        event: InventoryClickEvent,
        player: Player,
        menu: InteractiveMenu
    ): Boolean {
        // Shift-click from player inventory → menu inputs (supports partial stacks)
        if (event.clickedInventory != event.view.topInventory &&
            (event.click == ClickType.SHIFT_LEFT || event.click == ClickType.SHIFT_RIGHT)
        ) {
            event.isCancelled = true
            val item = event.currentItem
            if (item != null && item.type != Material.AIR) {
                val leftover = menu.shiftClickInto(player, item, notifyReject = true)
                event.currentItem = leftover
                delayedUpdate(player)
            }
            return true
        }

        // Clicks on top inventory item slots
        if (event.clickedInventory == event.view.topInventory && menu.isItemSlot(event.slot)) {
            handleInteractiveSlotClick(event, player, menu, menu.getSlotBehavior(event.slot))
            delayedUpdate(player)
            return true
        }

        // Clicks on top inventory fixed buttons — fall through to normal button handling
        return false
    }

    private fun handleInteractiveSlotClick(
        event: InventoryClickEvent,
        player: Player,
        menu: InteractiveMenu,
        behavior: SlotBehavior
    ) {
        event.isCancelled = true
        val slot = event.slot
        val cursor = event.cursor
        val current = menu.getSlotItem(slot)

        val canPlace = behavior == SlotBehavior.INTERACTIVE || behavior == SlotBehavior.INPUT_ONLY
        val canTake = behavior == SlotBehavior.INTERACTIVE || behavior == SlotBehavior.OUTPUT_ONLY

        when (event.action) {
            InventoryAction.PICKUP_ALL,
            InventoryAction.PICKUP_HALF,
            InventoryAction.PICKUP_ONE,
            InventoryAction.PICKUP_SOME -> {
                if (!canTake || current == null) {
                    // Empty / ghost slot — if cursor has item and can place, place instead
                    if (canPlace && cursor != null && cursor.type != Material.AIR) {
                        placeFromCursor(event, player, menu, slot, cursor, single = event.action == InventoryAction.PICKUP_ONE || event.click == ClickType.RIGHT)
                    }
                    return
                }

                val takeAmount = when (event.action) {
                    InventoryAction.PICKUP_HALF -> (current.amount + 1) / 2
                    InventoryAction.PICKUP_ONE -> 1
                    else -> current.amount
                }

                // Cursor already holding something: try merge if similar, else swap
                if (cursor != null && cursor.type != Material.AIR) {
                    if (cursor.isSimilar(current) && canTake) {
                        val room = cursor.maxStackSize - cursor.amount
                        if (room <= 0) return
                        val take = takeAmount.coerceAtMost(room)
                        val taken = menu.takeFromSlot(player, slot, take) ?: return
                        event.cursor = cursor.clone().apply { amount = cursor.amount + taken.amount }
                    } else if (canPlace && canTake) {
                        // Swap
                        val newCursor = menu.swapWithSlot(player, slot, cursor.clone())
                        event.cursor = newCursor
                    } else if (canPlace && !canTake) {
                        // Input only with item in cursor and occupied slot — try stack merge
                        if (current.isSimilar(cursor)) {
                            placeFromCursor(event, player, menu, slot, cursor, single = false)
                        } else {
                            Button.playFail(player)
                        }
                    }
                    return
                }

                val taken = menu.takeFromSlot(player, slot, takeAmount) ?: return
                event.cursor = taken
            }

            InventoryAction.PLACE_ALL,
            InventoryAction.PLACE_ONE,
            InventoryAction.PLACE_SOME -> {
                if (!canPlace || cursor == null || cursor.type == Material.AIR) {
                    return
                }
                val single = event.action == InventoryAction.PLACE_ONE || event.click == ClickType.RIGHT
                placeFromCursor(event, player, menu, slot, cursor, single)
            }

            InventoryAction.SWAP_WITH_CURSOR -> {
                if (cursor == null || cursor.type == Material.AIR) return

                if (current == null || current.type == Material.AIR) {
                    if (!canPlace) return
                    placeFromCursor(event, player, menu, slot, cursor, single = false)
                    return
                }

                if (current.isSimilar(cursor) && canPlace) {
                    placeFromCursor(event, player, menu, slot, cursor, single = false)
                    return
                }

                if (canPlace && canTake) {
                    if (!menu.isItemAllowedInSlot(slot, cursor)) {
                        Button.playFail(player)
                        return
                    }
                    val newCursor = menu.swapWithSlot(player, slot, cursor.clone())
                    event.cursor = newCursor
                } else {
                    Button.playFail(player)
                }
            }

            InventoryAction.MOVE_TO_OTHER_INVENTORY -> {
                // Shift-click item out of menu → player inventory
                if (!canTake || current == null) return
                val taken = menu.takeFromSlot(player, slot, current.amount) ?: return
                val leftover = player.inventory.addItem(taken)
                if (leftover.isNotEmpty()) {
                    // Put back what didn't fit
                    leftover.values.forEach { stack ->
                        menu.placeIntoSlot(player, slot, stack)
                    }
                }
            }

            InventoryAction.HOTBAR_SWAP -> {
                val hotbarSlot = event.hotbarButton
                if (hotbarSlot < 0) return
                val hotbarItem = player.inventory.getItem(hotbarSlot)

                if (hotbarItem == null || hotbarItem.type == Material.AIR) {
                    // Move menu item into empty hotbar slot
                    if (!canTake || current == null) return
                    val taken = menu.takeFromSlot(player, slot, current.amount) ?: return
                    player.inventory.setItem(hotbarSlot, taken)
                } else {
                    // Swap hotbar ↔ menu slot
                    if (!canPlace) {
                        // Output-only: only allow taking into empty hotbar (handled above)
                        Button.playFail(player)
                        return
                    }
                    if (!menu.isItemAllowedInSlot(slot, hotbarItem)) {
                        Button.playFail(player)
                        return
                    }
                    if (current != null && !canTake) {
                        // Input-only occupied: try merge
                        if (current.isSimilar(hotbarItem)) {
                            val leftover = menu.placeIntoSlot(player, slot, hotbarItem.clone())
                            player.inventory.setItem(hotbarSlot, leftover)
                        } else {
                            Button.playFail(player)
                        }
                        return
                    }
                    // Full swap for interactive
                    val old = current?.clone()
                    menu.setSlotItem(player, slot, hotbarItem.clone(), notify = true)
                    player.inventory.setItem(hotbarSlot, old)
                }
            }

            InventoryAction.COLLECT_TO_CURSOR -> {
                // Disabled — too easy to pull from output + inputs incorrectly
            }

            else -> {
                // Number-key style clicks sometimes arrive as UNKNOWN on older versions
                if (event.click == ClickType.NUMBER_KEY) {
                    // handled as HOTBAR_SWAP when available
                }
            }
        }
    }

    private fun placeFromCursor(
        event: InventoryClickEvent,
        player: Player,
        menu: InteractiveMenu,
        slot: Int,
        cursor: ItemStack,
        single: Boolean
    ) {
        if (!menu.isItemAllowedInSlot(slot, cursor)) {
            Button.playFail(player)
            return
        }
        val maxPlace = if (single) 1 else cursor.amount
        val probe = cursor.clone()
        val before = probe.amount
        val leftover = menu.placeIntoSlot(player, slot, probe, maxToPlace = maxPlace)

        if (leftover != null && leftover.amount == before) {
            // Nothing placed
            Button.playFail(player)
            return
        }

        event.cursor = leftover
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as Player
        val openMenu = Menu.currentlyOpenedMenus[player.uniqueId] ?: return

        openMenu.closed = true

        if (event.view.cursor != null && event.view.cursor!!.type != Material.AIR) {
            event.player.inventory.addItem(event.view.cursor)
            event.view.cursor = null
        }

        openMenu.onClose(player, openMenu.manualClose)
        openMenu.manualClose = false

        Menu.currentlyOpenedMenus.remove(player.uniqueId)
    }

    private fun refreshOpenMenuIfSame(player: Player, menu: Menu) {
        if (Menu.currentlyOpenedMenus.containsKey(player.uniqueId)) {
            val openMenu = Menu.currentlyOpenedMenus[player.uniqueId]
            if (openMenu === menu && openMenu.updateAfterClick) {
                openMenu.openMenu(player)
            }
        }
    }

    private fun delayedUpdate(player: Player) {
        Bukkit.getScheduler().scheduleSyncDelayedTask(UnifyCore.instance, {
            player.updateInventory()
        }, 1L)
    }

    @EventHandler
    fun onPlayerQuitEvent(event: PlayerQuitEvent) {
        Button.clickCooldown.remove(event.player.uniqueId)
        MenuHistory.clear(event.player)
        Menu.currentlyOpenedMenus.remove(event.player.uniqueId)
    }
}
