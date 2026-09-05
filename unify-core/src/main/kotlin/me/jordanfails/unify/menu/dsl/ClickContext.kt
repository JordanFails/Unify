package me.jordanfails.unify.menu.dsl

import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.menu.Menu
import me.jordanfails.unify.menu.history.MenuHistory
import me.jordanfails.unify.menu.menus.menus.ConfirmMenu
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.InventoryView

/**
 * Rich context passed to DSL click handlers.
 *
 * Gives you everything you need without hunting through parameters:
 * ```kotlin
 * onClick { ctx ->
 *     if (!ctx.isLeftClick) return@onClick
 *     ctx.soundSuccess()
 *     ctx.open(OtherMenu())
 * }
 * ```
 */
data class ClickContext(
    val player: Player,
    val slot: Int,
    val click: ClickType,
    val view: InventoryView,
    val menu: Menu?
) {
    val isLeftClick: Boolean get() = click.isLeftClick
    val isRightClick: Boolean get() = click.isRightClick
    val isShiftClick: Boolean get() = click.isShiftClick

    fun close() {
        player.closeInventory()
    }

    /** Re-open the current menu (full rebuild). */
    fun refresh() {
        menu?.openMenu(player)
    }

    /** Soft-refresh button items without reopening. */
    fun refreshButtons() {
        menu?.refreshButtons(player)
    }

    /**
     * Opens another menu. When [track] is true (default), the current menu
     * is pushed onto [MenuHistory] so [back] works.
     */
    fun open(next: Menu, track: Boolean = true) {
        if (track && menu != null) {
            MenuHistory.push(player, menu)
        }
        next.openMenu(player)
    }

    /** Pops the previous menu from history, or closes if none. */
    fun back() {
        if (!MenuHistory.back(player)) {
            close()
        }
    }

    fun soundClick() = Button.playClick(player)
    fun soundSuccess() = Button.playSuccess(player)
    fun soundFail() = Button.playFail(player)
    fun soundNeutral() = Button.playNeutral(player)

    fun message(text: String) {
        player.sendMessage(text)
    }

    /**
     * Opens a confirm dialog; on confirm runs [onConfirm], on cancel runs
     * [onCancel] (optional). The previous menu is restored from history when
     * either path finishes if [returnToMenu] is true.
     */
    fun confirm(
        title: String = "Are you sure?",
        info: List<String> = emptyList(),
        returnToMenu: Boolean = true,
        onCancel: (() -> Unit)? = null,
        onConfirm: () -> Unit
    ) {
        val previous = menu
        if (previous != null) {
            MenuHistory.push(player, previous)
        }

        ConfirmMenu(
            titleText = title,
            extraInfo = info.toMutableList()
        ) { confirmed ->
            if (confirmed) {
                onConfirm()
            } else {
                onCancel?.invoke()
            }
            if (returnToMenu) {
                MenuHistory.back(player)
            }
        }.openMenu(player)
    }
}
