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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryDragEvent(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        val menu = Menu.currentlyOpenedMenus[player.uniqueId] ?: return
        if (!menu.ownsInventory(event.view.topInventory)) return
        val topSize = event.view.topInventory.size
        val topItems = event.newItems.filterKeys { it in 0 until topSize }
        if (topItems.isEmpty()) {
            if (!menu.keepBottomMechanics) event.isCancelled = true
            return
        }
        event.isCancelled = true
        if (menu is InteractiveMenu) {
            if (!topItems.keys.all(menu::canPlaceIn)) return
        } else if (topItems.keys.any { menu.buttons[it]?.isMoveable() == false }) {
            return
        }
        val accepted = if (menu.nonCancelling && menu !is InteractiveMenu) true
            else menu.acceptsDraggedItems(player, topItems)
        if (!accepted || Menu.currentlyOpenedMenus[player.uniqueId] !== menu) return
        event.newItems.forEach { (slot, item) ->
            val placed = if (slot < topSize && menu is InteractiveMenu) menu.getSlotItem(slot) else item.clone()
            event.view.setItem(slot, placed)
        }
        event.view.setCursor(event.cursor)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onButtonPress(event: InventoryClickEvent) {
        handleButtonPress(event)
    }

    private fun handleButtonPress(event: InventoryClickEvent) {
        val player = event.whoClicked as Player
        val openMenu = Menu.currentlyOpenedMenus[player.uniqueId] ?: return
        if (!openMenu.ownsInventory(event.view.topInventory)) return

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
            (event.click == ClickType.LEFT || event.click == ClickType.RIGHT)
        ) {
            if (event.clickedInventory == event.view.topInventory) {
                if (openMenu.nonCancelling && (openMenu.buttons[event.slot]?.isMoveable() != false)) return
                event.isCancelled = true

                val itemInserted = when (event.click) {
                    ClickType.LEFT -> event.cursor
                    ClickType.RIGHT -> ItemBuilder(event.cursor!!.clone()).amount(1).build()
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
                    if (openMenu.nonCancelling) {
                        event.currentItem = moveIntoMenu(event.view.topInventory, openMenu, event.currentItem!!)
                    } else if (openMenu.acceptsShiftClickedItem(player, event.currentItem!!.clone())) {
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

                    InventoryAction.MOVE_TO_OTHER_INVENTORY,
                    InventoryAction.DROP_ALL_SLOT,
                    InventoryAction.DROP_ONE_SLOT -> !button.isRemovable()

                    InventoryAction.CLONE_STACK -> !openMenu.nonCancelling

                    InventoryAction.SWAP_WITH_CURSOR,
                    InventoryAction.HOTBAR_SWAP,
                    InventoryAction.HOTBAR_MOVE_AND_READD -> !button.isMoveable()

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
                            refreshOpenMenuIfSame(player, newMenu)
                        }
                    }

                    return
                }
            }

            val cancel = button.shouldCancel(player, event.slot, event.click)

            if (!cancel && (event.click == ClickType.SHIFT_LEFT || event.click == ClickType.SHIFT_RIGHT)) {
                if (button.isRemovable()) {
                    event.isCancelled = true
                    if (event.currentItem != null && event.currentItem!!.type != Material.AIR) {
                        val removedItem = event.currentItem!!.clone()
                        val leftover = player.inventory.addItem(removedItem)
                        event.currentItem = leftover.values.firstOrNull()
                    }
                } else {
                    event.isCancelled = true
                }
            } else {
                if (cancel) event.isCancelled = true
            }

            button.clicked(player, event.slot, event.click, event.view)

            if (Menu.currentlyOpenedMenus.containsKey(player.uniqueId)) {
                val newMenu = Menu.currentlyOpenedMenus[player.uniqueId]
                if (newMenu === openMenu && newMenu.updateAfterClick) {
                    if (event.isCancelled) refreshOpenMenuIfSame(player, newMenu)
                    else Bukkit.getScheduler().runTask(UnifyCore.instance, Runnable {
                        refreshOpenMenuIfSame(player, newMenu)
                    })
                }
            }

            return
        } else {
            if (event.clickedInventory == event.view.topInventory) {
                if (!openMenu.nonCancelling) event.isCancelled = true
                openMenu.onBackgroundClick(player, event.slot, event.click)
            }
        }

    }

    private fun moveIntoMenu(inventory: org.bukkit.inventory.Inventory, menu: Menu, source: ItemStack): ItemStack? {
        val remaining = source.clone()
        for (empty in listOf(false, true)) {
            for (slot in 0 until inventory.size) {
                if (menu.buttons[slot]?.isMoveable() == false) continue
                val current = inventory.getItem(slot)?.takeUnless { it.type == Material.AIR }
                if ((current == null) != empty || (current != null && !current.isSimilar(remaining))) continue
                val room = minOf(inventory.maxStackSize, remaining.maxStackSize) - (current?.amount ?: 0)
                val amount = minOf(remaining.amount, room.coerceAtLeast(0))
                if (amount == 0) continue
                inventory.setItem(slot, remaining.clone().apply { this.amount = (current?.amount ?: 0) + amount })
                remaining.amount -= amount
                if (remaining.amount == 0) return null
            }
        }
        return remaining
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
            }
            return true
        }

        // Clicks on top inventory item slots
        if (event.clickedInventory == event.view.topInventory && menu.isItemSlot(event.slot)) {
            handleInteractiveSlotClick(event, player, menu, menu.getSlotBehavior(event.slot))
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
                var capacity = 0
                for (inventorySlot in 0..35) {
                    val item = player.inventory.getItem(inventorySlot)?.takeUnless { it.type == Material.AIR }
                    val maximum = minOf(current.maxStackSize, player.inventory.maxStackSize)
                    capacity += when {
                        item == null -> maximum
                        item.isSimilar(current) -> (maximum - item.amount).coerceAtLeast(0)
                        else -> 0
                    }
                }
                if (capacity == 0) return
                val taken = menu.takeFromSlot(player, slot, minOf(current.amount, capacity)) ?: return
                player.inventory.addItem(taken).values.forEach { leftover ->
                    player.world.dropItemNaturally(player.location, leftover)
                }
            }

            InventoryAction.HOTBAR_SWAP -> {
                val hotbarSlot = if (event.click == ClickType.SWAP_OFFHAND) 40 else event.hotbarButton
                if (hotbarSlot !in 0..8 && hotbarSlot != 40) return
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
                    if (hotbarItem.amount > minOf(menu.getMaxAmount(slot), hotbarItem.maxStackSize)) return
                    val old = menu.swapWithSlot(player, slot, hotbarItem.clone())
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
        val player = event.player as? Player ?: return
        val menu = Menu.currentlyOpenedMenus[player.uniqueId] ?: return
        if (!menu.ownsInventory(event.view.topInventory)) return
        closeMenu(player, menu, event.view)
    }

    private fun closeMenu(player: Player, menu: Menu, view: org.bukkit.inventory.InventoryView) {
        if (!menu.beginClose(player)) return
        menu.closed = true
        try {
            val cursor = view.cursor?.takeUnless { it.type == Material.AIR }?.clone()
            view.setCursor(null)
            if (cursor != null) player.inventory.addItem(cursor).values.forEach {
                player.world.dropItemNaturally(player.location, it)
            }
            menu.onClose(player, menu.manualClose)
        } finally {
            Menu.currentlyOpenedMenus.remove(player.uniqueId, menu)
            menu.finishClose(player)
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
        MenuHistory.clear(event.player)
        val menu = Menu.currentlyOpenedMenus[event.player.uniqueId]
        if (menu != null) closeMenu(event.player, menu, event.player.openInventory)
        Menu.cancelPendingOpen(event.player.uniqueId)
    }
}
