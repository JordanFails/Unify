package me.jordanfails.unify.menu

import me.jordanfails.unify.menu.dsl.borderedMenu
import me.jordanfails.unify.menu.dsl.interactiveMenu
import me.jordanfails.unify.menu.dsl.menu
import me.jordanfails.unify.menu.dsl.paginatedMenu
import me.jordanfails.unify.menu.dsl.BorderedMenuBuilder
import me.jordanfails.unify.menu.dsl.InteractiveMenuBuilder
import me.jordanfails.unify.menu.dsl.PaginatedMenuBuilder
import me.jordanfails.unify.menu.dsl.PlainMenuBuilder
import me.jordanfails.unify.menu.history.MenuHistory
import me.jordanfails.unify.menu.menus.menus.ConfirmMenu
import org.bukkit.entity.Player

// ── Open helpers ─────────────────────────────────────────────────────────────

/**
 * Open this menu for [player], optionally tracking history so back-navigation works.
 *
 * @param track when true, the currently open menu (if any) is pushed onto
 *   [MenuHistory] before this one opens
 */
fun Menu.open(player: Player, track: Boolean = false) {
    if (track) {
        MenuHistory.open(player, this)
    } else {
        openMenu(player)
    }
}

/**
 * Open [menu] for this player. Defaults to tracking history so nested menus
 * can call [MenuHistory.back] / DSL `ctx.back()`.
 */
fun Player.openMenu(menu: Menu, track: Boolean = true) {
    menu.open(this, track = track)
}

/**
 * Open [menu] as a navigation root (clears history first).
 */
fun Player.openMenuRoot(menu: Menu) {
    MenuHistory.openRoot(this, menu)
}

// ── Inline DSL open ──────────────────────────────────────────────────────────

/**
 * Build and open a plain menu in one shot.
 *
 * ```kotlin
 * player.openMenu("&8Quick") {
 *     rows(3)
 *     slot(1, 4) {
 *         material(Material.APPLE)
 *         name("&cHeal")
 *         onClick { it.player.health = 20.0; it.close() }
 *     }
 * }
 * ```
 */
fun Player.openMenu(title: String = "&8Menu", track: Boolean = true, block: PlainMenuBuilder.() -> Unit) {
    openMenu(menu(title, block), track = track)
}

/**
 * Build and open a bordered menu in one shot.
 */
fun Player.openBorderedMenu(
    title: String = "&8Menu",
    track: Boolean = true,
    block: BorderedMenuBuilder.() -> Unit
) {
    openMenu(borderedMenu(title, block), track = track)
}

/**
 * Build and open a paginated bordered menu in one shot.
 */
fun Player.openPaginatedMenu(
    title: String = "&8Menu",
    track: Boolean = true,
    block: PaginatedMenuBuilder.() -> Unit
) {
    openMenu(paginatedMenu(title, block), track = track)
}

/**
 * Build and open an interactive (crafting / tinkerer) menu in one shot.
 */
fun Player.openInteractiveMenu(
    title: String = "&8Menu",
    track: Boolean = true,
    block: InteractiveMenuBuilder.() -> Unit
) {
    openMenu(interactiveMenu(title, block), track = track)
}

// ── Confirm ──────────────────────────────────────────────────────────────────

/**
 * Show a confirm dialog, then run [onResult] with the player's choice.
 *
 * ```kotlin
 * player.confirm("Delete home?") { yes ->
 *     if (yes) deleteHome()
 * }
 * ```
 */
fun Player.confirm(
    title: String = "Are you sure?",
    info: List<String> = emptyList(),
    onResult: (Boolean) -> Unit
) {
    ConfirmMenu(
        titleText = title,
        extraInfo = info.toMutableList(),
        callback = onResult
    ).openMenu(this)
}

/**
 * Confirm with separate confirm/cancel lambdas.
 */
fun Player.confirm(
    title: String = "Are you sure?",
    info: List<String> = emptyList(),
    onCancel: () -> Unit = {},
    onConfirm: () -> Unit
) {
    confirm(title, info) { yes ->
        if (yes) onConfirm() else onCancel()
    }
}
