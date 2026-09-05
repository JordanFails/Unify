package me.jordanfails.unify.screen

import me.jordanfails.unify.menu.Menu
import me.jordanfails.unify.menu.history.MenuHistory
import org.bukkit.entity.Player

/**
 * Typed lookup of every input on the screen that produced this click.
 */
class ScreenValues(
    private val data: Map<String, Any?>,
) {
    operator fun get(key: String): Any? = data[key]

    fun text(key: String): String = textOrNull(key) ?: ""

    fun textOrNull(key: String): String? = data[key] as? String

    fun bool(key: String): Boolean = boolOrNull(key) ?: false

    fun boolOrNull(key: String): Boolean? = when (val value = data[key]) {
        is Boolean -> value
        is String -> value.equals("true", ignoreCase = true)
        else -> null
    }

    fun number(key: String): Float = numberOrNull(key) ?: 0f

    fun numberOrNull(key: String): Float? = when (val value = data[key]) {
        is Float -> value
        is Double -> value.toFloat()
        is Int -> value.toFloat()
        is Number -> value.toFloat()
        is String -> value.toFloatOrNull()
        else -> null
    }

    fun int(key: String): Int = number(key).toInt()

    fun keys(): Set<String> = data.keys

    fun asMap(): Map<String, Any?> = data.toMap()
}

/**
 * Context handed to a [ScreenAction.Click] handler.
 */
class ScreenClick(
    val player: Player,
    val values: ScreenValues,
    val screen: Screen,
) {
    fun close() {
        Screens.close(player)
    }

    /** Rebuild and re-open the current screen. */
    fun refresh() {
        Screens.open(player, screen, track = false)
    }

    /**
     * Open another native screen. When [track] is true the current screen is
     * pushed onto [Screens] history so [back] works.
     */
    fun open(next: Screen, track: Boolean = true) {
        Screens.open(player, next, track = track)
    }

    /** Close the native screen and open an inventory [Menu]. */
    fun open(menu: Menu, track: Boolean = true) {
        Screens.close(player)
        if (track) {
            MenuHistory.open(player, menu)
        } else {
            menu.openMenu(player)
        }
    }

    /** Restore the previous native screen, or close if none. */
    fun back() {
        if (!Screens.back(player)) {
            close()
        }
    }
}
