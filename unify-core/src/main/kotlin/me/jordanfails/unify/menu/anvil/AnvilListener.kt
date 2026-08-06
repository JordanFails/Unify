package me.jordanfails.unify.menu.anvil

import me.jordanfails.unify.UnifyCore
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

object AnvilListener : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = AnvilSession.get(player) ?: return
        if (!session.open) return
        if (event.inventory != session.handle.inventory) return

        val rawSlot = event.rawSlot
        if (rawSlot == -999) return

        // Block double-click merge / shift from player inv into anvil
        val clicked = event.clickedInventory
        if (clicked != null && clicked == player.inventory) {
            if (event.click == ClickType.DOUBLE_CLICK || event.isShiftClick) {
                event.isCancelled = true
                return
            }
        }

        // Block placing items into anvil slots (not interactable in v1)
        if (clicked != null && clicked == session.handle.inventory) {
            val cursor = event.cursor
            if (cursor != null && cursor.type != Material.AIR) {
                event.isCancelled = true
            }
        }

        val inAnvilSlots = rawSlot in 0..2
        val moveToOther = event.action == InventoryAction.MOVE_TO_OTHER_INVENTORY
        if (!inAnvilSlots && !moveToOther) return

        event.isCancelled = true

        if (!inAnvilSlots) return

        val state = session.snapshot()
        val result = try {
            when {
                session.onClick != null -> session.onClick.invoke(rawSlot, state)
                rawSlot == AnvilSlot.OUTPUT && session.onComplete != null ->
                    session.onComplete.invoke(player, state.text)
                else -> AnvilResult.keepOpen()
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            AnvilResult.close()
        }

        session.applyResult(result)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = AnvilSession.get(player) ?: return
        if (!session.open) return
        if (event.inventory != session.handle.inventory) return

        for (slot in AnvilSlot.ALL) {
            if (event.rawSlots.contains(slot)) {
                event.isCancelled = true
                return
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val session = AnvilSession.get(player) ?: return
        if (!session.open) return
        if (event.inventory != session.handle.inventory) return

        val shouldReopen = session.preventClose && !session.closingByApi
        val reopen = session.reopenIfPrevented

        // Client already closed; don't re-send close packet
        // Skip onClose when we will immediately reopen (password-style prompts)
        session.close(sendClosePacket = false, notifyClose = !shouldReopen)

        if (shouldReopen && player.isOnline && reopen != null) {
            Bukkit.getScheduler().runTask(UnifyCore.instance, Runnable {
                if (player.isOnline) {
                    reopen.invoke()
                }
            })
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        AnvilSession.get(event.player)?.close(sendClosePacket = false, notifyClose = true)
    }
}
