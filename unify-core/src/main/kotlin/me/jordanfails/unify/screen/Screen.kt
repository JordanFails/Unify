package me.jordanfails.unify.screen

import org.bukkit.entity.Player

/**
 * A native Minecraft custom screen (dialog), shown with Paper's Dialog API
 * on 1.21.6+ / 26.x.
 *
 * Prefer the Kotlin DSL in [me.jordanfails.unify.screen.dsl] for one-off screens;
 * subclass this when you want a reusable, per-player screen.
 *
 * ```kotlin
 * player.openScreen("Warps") {
 *     body("Choose a destination")
 *     columns(2)
 *     button("&aSpawn") { onClick { it.player.teleport(spawn) } }
 *     button("&eShop")  { onClick { it.open(shopScreen) } }
 *     exit("&7Close")
 * }
 * ```
 *
 * Unsupported on older versions: [open] returns false. Check [Screens.supported]
 * first, or use inventory [me.jordanfails.unify.menu.Menu]s as a fallback.
 */
abstract class Screen {

    abstract fun getTitle(player: Player): String

    open fun getExternalTitle(player: Player): String? = null

    abstract fun getKind(): ScreenKind

    open fun getBodies(player: Player): List<ScreenBody> = emptyList()

    open fun getInputs(player: Player): List<ScreenInput> = emptyList()

    /** Grid / notice buttons. Ignored for [ScreenKind.CONFIRMATION]. */
    open fun getButtons(player: Player): List<ScreenButton> = emptyList()

    open fun getYesButton(player: Player): ScreenButton? = null

    open fun getNoButton(player: Player): ScreenButton? = null

    open fun getExitButton(player: Player): ScreenButton? = null

    open fun getColumns(): Int = 2

    open fun canCloseWithEscape(): Boolean = true

    /**
     * Pause the game while this screen is open (single-player only).
     * Forced off when [getAfterAction] is [ScreenAfterAction.KEEP_OPEN].
     */
    open fun pause(): Boolean = false

    open fun getAfterAction(): ScreenAfterAction = ScreenAfterAction.CLOSE

    /**
     * Show this screen to [player].
     *
     * @param track when true, the currently open screen is pushed onto history
     * @return true if the native screen was shown
     */
    fun open(player: Player, track: Boolean = true): Boolean =
        Screens.open(player, this, track)

    fun close(player: Player) {
        Screens.close(player)
    }

    companion object {
        /** Whether this server can show native custom screens. */
        @JvmStatic
        fun supported(): Boolean = Screens.supported()
    }
}
