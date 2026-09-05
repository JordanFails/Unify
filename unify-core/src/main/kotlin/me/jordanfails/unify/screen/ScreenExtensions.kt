package me.jordanfails.unify.screen

import me.jordanfails.unify.screen.dsl.ConfirmScreenBuilder
import me.jordanfails.unify.screen.dsl.FormScreenBuilder
import me.jordanfails.unify.screen.dsl.NoticeScreenBuilder
import me.jordanfails.unify.screen.dsl.ScreenBuilder
import me.jordanfails.unify.screen.dsl.confirmScreen
import me.jordanfails.unify.screen.dsl.formScreen
import me.jordanfails.unify.screen.dsl.noticeScreen
import me.jordanfails.unify.screen.dsl.screen
import org.bukkit.entity.Player

/** Whether this server can show native custom screens (Paper 1.21.6+ / 26.x). */
val Player.hasCustomScreens: Boolean
    get() = Screens.supported()

fun Player.openScreen(screen: Screen, track: Boolean = true): Boolean =
    Screens.open(this, screen, track)

fun Player.openScreenRoot(screen: Screen): Boolean =
    Screens.openRoot(this, screen)

fun Player.closeScreen() {
    Screens.close(this)
}

/**
 * Build and open a multi-action custom screen in one shot.
 *
 * ```kotlin
 * player.openScreen("Warps") {
 *     body("Choose a destination")
 *     columns(2)
 *     button("&aSpawn") { onClick { it.player.teleport(spawn) } }
 *     exit("&7Close")
 * }
 * ```
 */
fun Player.openScreen(
    title: String,
    track: Boolean = true,
    block: ScreenBuilder.() -> Unit,
): Boolean = openScreen(screen(title, block), track)

fun Player.openNotice(
    title: String,
    track: Boolean = true,
    block: NoticeScreenBuilder.() -> Unit = {},
): Boolean = openScreen(noticeScreen(title, block), track)

fun Player.openConfirm(
    title: String,
    track: Boolean = true,
    block: ConfirmScreenBuilder.() -> Unit,
): Boolean = openScreen(confirmScreen(title, block), track)

fun Player.openForm(
    title: String,
    track: Boolean = true,
    block: FormScreenBuilder.() -> Unit,
): Boolean = openScreen(formScreen(title, block), track)
