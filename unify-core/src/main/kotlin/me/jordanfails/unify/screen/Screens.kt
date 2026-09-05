package me.jordanfails.unify.screen

import me.jordanfails.unify.nms.NMSHandlerFactory
import org.bukkit.entity.Player
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Runtime bookkeeping for native custom screens: support check, currently-open
 * screen, and back-navigation history.
 */
object Screens {

    private const val MAX_DEPTH = 16

    private val open = ConcurrentHashMap<UUID, Screen>()
    private val stacks = ConcurrentHashMap<UUID, ArrayDeque<Screen>>()

    fun supported(): Boolean =
        NMSHandlerFactory.getHandler()?.supportsCustomScreens() == true

    fun current(player: Player): Screen? = open[player.uniqueId]

    /**
     * Show [screen] to [player].
     *
     * @return true if the native screen was shown
     */
    fun open(player: Player, screen: Screen, track: Boolean = true): Boolean {
        val handler = NMSHandlerFactory.getHandler()
        if (handler == null || !handler.supportsCustomScreens()) {
            return false
        }
        val shown = handler.openCustomScreen(player, screen)
        if (shown) {
            if (track) {
                val current = open[player.uniqueId]
                if (current != null && current !== screen) {
                    push(player, current)
                }
            }
            open[player.uniqueId] = screen
        }
        return shown
    }

    fun close(player: Player) {
        open.remove(player.uniqueId)
        NMSHandlerFactory.getHandler()?.closeCustomScreen(player)
    }

    fun push(player: Player, screen: Screen) {
        val stack = stacks.getOrPut(player.uniqueId) { ArrayDeque() }
        if (stack.peekLast() === screen) return
        stack.addLast(screen)
        while (stack.size > MAX_DEPTH) {
            stack.removeFirst()
        }
    }

    fun hasHistory(player: Player): Boolean =
        (stacks[player.uniqueId]?.size ?: 0) > 0

    /**
     * Pops and re-opens the previous screen.
     * @return true if a screen was restored
     */
    fun back(player: Player): Boolean {
        val stack = stacks[player.uniqueId] ?: return false
        val previous = stack.pollLast() ?: return false
        return open(player, previous, track = false)
    }

    fun openRoot(player: Player, screen: Screen): Boolean {
        clear(player)
        return open(player, screen, track = false)
    }

    fun clear(player: Player) {
        open.remove(player.uniqueId)
        stacks.remove(player.uniqueId)
    }

    internal fun forget(player: Player) {
        clear(player)
    }
}
