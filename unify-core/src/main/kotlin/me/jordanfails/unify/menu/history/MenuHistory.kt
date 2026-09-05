package me.jordanfails.unify.menu.history

import me.jordanfails.unify.menu.Menu
import org.bukkit.entity.Player
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-player stack of previously opened menus for natural back-navigation.
 *
 * ```kotlin
 * // Opening with history (DSL / extensions do this for you):
 * MenuHistory.push(player, currentMenu)
 * nextMenu.openMenu(player)
 *
 * // Later:
 * MenuHistory.back(player) // re-opens previous, or returns false
 * ```
 *
 * History is cleared on quit (via [clear]) and when a menu is opened with
 * [openRoot] semantics.
 */
object MenuHistory {

    private const val MAX_DEPTH = 16

    private val stacks = ConcurrentHashMap<UUID, ArrayDeque<Menu>>()

    fun push(player: Player, menu: Menu) {
        val stack = stacks.getOrPut(player.uniqueId) { ArrayDeque() }
        // Avoid pushing the same instance twice in a row
        if (stack.peekLast() === menu) return
        stack.addLast(menu)
        while (stack.size > MAX_DEPTH) {
            stack.removeFirst()
        }
    }

    fun peek(player: Player): Menu? = stacks[player.uniqueId]?.peekLast()

    fun size(player: Player): Int = stacks[player.uniqueId]?.size ?: 0

    fun hasHistory(player: Player): Boolean = size(player) > 0

    /**
     * Pops and opens the previous menu.
     * @return true if a menu was restored, false if the stack was empty
     */
    fun back(player: Player): Boolean {
        val stack = stacks[player.uniqueId] ?: return false
        val previous = stack.pollLast() ?: return false
        // Opening from history should not re-push
        previous.openMenu(player)
        return true
    }

    /**
     * Opens [menu] as a new root — clears existing history first.
     */
    fun openRoot(player: Player, menu: Menu) {
        clear(player)
        menu.openMenu(player)
    }

    /**
     * Opens [menu], pushing the currently open menu (if any) onto the stack.
     */
    fun open(player: Player, menu: Menu) {
        val current = Menu.currentlyOpenedMenus[player.uniqueId]
        if (current != null && current !== menu) {
            push(player, current)
        }
        menu.openMenu(player)
    }

    fun clear(player: Player) {
        stacks.remove(player.uniqueId)
    }

    fun clearAll() {
        stacks.clear()
    }
}
