package me.jordanfails.unify.menu.menus.menus

import com.cryptomorin.xseries.XMaterial
import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.menu.Menu
import me.jordanfails.unify.nms.LegacyColorDataType
import me.jordanfails.unify.nms.LegacyItemColor
import me.jordanfails.unify.nms.NMSHandlerFactory
import org.bukkit.entity.Player
import kotlin.math.ceil

/**
 * A menu with an automatic border around its edges (top row, bottom row,
 * left column, right column). Override [getContentButtons] to populate
 * only the inner area.
 *
 * ### ⚠️ Controlling the menu size
 *
 * Override **[getMenuSize]** (or [getMinSize]) to set a specific inventory
 * size — **do NOT override [size][me.jordanfails.unify.menu.Menu.size]**.
 *
 * The border calculation in [fillBorder] reads [getMenuSize] directly.
 * Overriding [size][me.jordanfails.unify.menu.Menu.size] only affects the
 * inventory creation in [Menu.createInventory] but **not** which slots
 * are considered border. If [getMenuSize] returns a size smaller than your
 * intended row count, [fillBorder] may treat every row as "first or last",
 * filling the entire menu with border panes instead of just the edges.
 *
 * Correct:
 * ```
 * override fun getMinSize(): Int = 27    // 3 rows
 * // or
 * override fun getMenuSize(player: Player): Int = 36  // 4 rows
 * ```
 *
 * Wrong (has no effect on the border):
 * ```
 * override fun size(buttons: Map<Int, Button>): Int = 9 * 3
 * ```
 */
abstract class BorderedMenu(
    private val emptyBorderSlots: Set<Int> = emptySet(),
    /**
     * When enabled, the keys returned by [getContentButtons] are treated only
     * as ordering indexes. Buttons are placed, in key order, into the next
     * available inner slot instead of using those keys as inventory slots.
     *
     * Keys listed in [autoPlaceExceptions] (or returned by
     * [getAutoPlaceExceptions]) keep their absolute inventory slots and are
     * skipped by auto-placement.
     *
     * This is disabled by default so existing menus with explicitly positioned
     * controls (such as back and navigation buttons) keep their absolute slots.
     * Enable it only for menus whose ordinary entries should flow through the
     * inner item spaces.
     */
    var autoPlaceContentButtons: Boolean = false,
    /**
     * Map keys from [getContentButtons] that should keep absolute inventory
     * slots when [autoPlaceContentButtons] is enabled. All other buttons are
     * auto-placed into remaining free content slots.
     *
     * Example: with auto-place on, key `0`/`1` fill inner slots in order, while
     * key `49` stays at inventory slot 49 if `49` is in this set.
     */
    var autoPlaceExceptions: Set<Int> = emptySet()
) : Menu() {

    init {
        autoUpdate = true
    }

    /**
     * Content-button map keys that keep absolute inventory positions when
     * [autoPlaceContentButtons] is enabled. Defaults to [autoPlaceExceptions];
     * override for player-specific fixed slots.
     */
    open fun getAutoPlaceExceptions(player: Player): Set<Int> = autoPlaceExceptions

    /**
     * Override this to fill the *content* (inner area) of your menu with actual buttons.
     * This should not include border buttons.
     */
    abstract fun getContentButtons(player: Player): MutableMap<Int, Button>

    /**
     * Whether to render a border around the menu.
     * Override to `false` if you want a full-screen layout.
     */
    open fun hasBorder(): Boolean = true

    /**
     * The total menu size. Override this to set a specific size,
     * otherwise it's calculated from content buttons + [getMinSize].
     *
     * This is the method [fillBorder] reads to determine which slots are
     * edge slots. Prefer this over overriding [size][me.jordanfails.unify.menu.Menu.size].
     *
     * @see BorderedMenu class-level docs for the full explanation.
     */
    open fun getMenuSize(player: Player): Int {
        val contentButtons = getContentButtons(player)
        if (contentButtons.isEmpty()) return getMinSize().takeIf { it > 0 } ?: 27

        if (autoPlaceContentButtons) {
            val exceptions = getAutoPlaceExceptions(player)
            val autoPlaceCount = contentButtons.keys.count { it !in exceptions }
            val highestFixed = contentButtons.keys
                .filter { it in exceptions }
                .maxOrNull()
                ?: -1

            val contentRows = if (hasBorder()) {
                (autoPlaceCount + 6) / 7
            } else {
                (autoPlaceCount + 8) / 9
            }
            val requiredRows = if (hasBorder()) {
                (contentRows + 2).coerceAtLeast(3)
            } else {
                contentRows.coerceAtLeast(1)
            }
            var requiredSize = requiredRows * 9
            val minSize = getMinSize().takeIf { it > 0 } ?: 27
            requiredSize = maxOf(requiredSize, minSize)

            if (highestFixed >= 0) {
                val fixedSize = ((highestFixed / 9) + 1) * 9
                requiredSize = maxOf(requiredSize, fixedSize)
            }

            // Grow until enough free content slots remain after fixed positions
            val fixedSlots = contentButtons.keys.filter { it in exceptions }.toSet()
            while (requiredSize <= 54) {
                val freeSlots = getAvailableContentSlots(requiredSize).count { it !in fixedSlots }
                if (freeSlots >= autoPlaceCount) break
                requiredSize += 9
            }

            return requiredSize.coerceAtMost(54)
        }

        var highest = 0
        for (slot in contentButtons.keys) {
            if (slot > highest) highest = slot
        }

        val calculatedSize = (ceil((highest + 1) / 9.0) * 9.0).toInt()
        val minSize = getMinSize()
        return if (minSize > 0 && minSize > calculatedSize) minSize else calculatedSize
    }

    /**
     * The placeholder (visual filler) button used for the border slots.
     */
    open fun getPlaceholderButton(): Button =
        Button.placeholder(
            XMaterial.GRAY_STAINED_GLASS_PANE,
            NMSHandlerFactory.getHandler()?.getLegacyColorData(LegacyItemColor.GRAY, LegacyColorDataType.BLOCK)
                ?: LegacyItemColor.GRAY.blockData,
            " "
        )

    /**
     * Composes the menu layout with both borders and content. Auto-placed
     * content only uses inner slots, so the decorative border stays intact.
     * Explicit [autoPlaceExceptions] retain their requested absolute slots.
     */
    final override fun getButtons(player: Player): MutableMap<Int, Button> {
        val buttons = mutableMapOf<Int, Button>()

        // ── ① Get content buttons FIRST to determine size
        val suppliedContentButtons = getContentButtons(player)
        val menuSize = getMenuSize(player)
        val contentButtons = if (autoPlaceContentButtons) {
            placeContentButtonsInNextAvailableSlots(
                suppliedContentButtons,
                menuSize,
                getAutoPlaceExceptions(player)
            )
        } else {
            suppliedContentButtons
        }

        // ── ② Border (LOWEST PRIORITY - pure decoration)
        if (hasBorder()) {
            fillBorder(buttons, menuSize)
        }

        // ── ③ Content buttons. Auto-placed entries cannot overlap the border.
        buttons.putAll(contentButtons)

        return buttons
    }

    /**
     * Places content sequentially into non-border slots. Keys in [exceptions]
     * keep their absolute inventory slots; remaining buttons fill free content
     * slots in key order (skipping any slots already taken by exceptions).
     */
    private fun placeContentButtonsInNextAvailableSlots(
        contentButtons: Map<Int, Button>,
        totalSlots: Int,
        exceptions: Set<Int>
    ): MutableMap<Int, Button> {
        val placedButtons = linkedMapOf<Int, Button>()

        // Fixed positions first — absolute inventory slots
        contentButtons.forEach { (key, button) ->
            if (key in exceptions && key in 0 until totalSlots) {
                placedButtons[key] = button
            }
        }

        val reservedSlots = placedButtons.keys
        val availableSlots = getAvailableContentSlots(totalSlots)
            .filter { it !in reservedSlots }

        contentButtons.entries
            .filter { it.key !in exceptions }
            .sortedBy { it.key }
            .zip(availableSlots)
            .forEach { (entry, slot) ->
                placedButtons[slot] = entry.value
            }

        return placedButtons
    }

    private fun getAvailableContentSlots(totalSlots: Int): List<Int> {
        if (!hasBorder()) return (0 until totalSlots).toList()

        val rows = totalSlots / 9
        return (0 until totalSlots).filter { slot ->
            val row = slot / 9
            val col = slot % 9
            val isFirstOrLastRow = rows > 1 && (row == 0 || row == rows - 1)

            !isFirstOrLastRow && col != 0 && col != 8
        }
    }

    /**
     * Creates the border by filling all outer slots (top, bottom, left, right).
     */
    protected fun fillBorder(buttons: MutableMap<Int, Button>, totalSlots: Int) {
        val placeholder = getPlaceholderButton()
        val rows = totalSlots / 9

        for (slot in 0 until totalSlots) {
            val row = slot / 9
            val col = slot % 9

            val isFirstOrLastRow = if (rows > 1) {
                row == 0 || row == rows - 1
            } else {
                false
            }

            if (isFirstOrLastRow || col == 0 || col == 8) {
                if (!emptyBorderSlots.contains(slot)) {
                    buttons[slot] = placeholder
                }
            }
        }
    }

    /**
     * Returns all *inner* slot indexes (excluding the border) —
     * very handy for placing dynamic items programmatically.
     * Automatically excludes slots occupied by content buttons.
     */
    protected fun getInnerSlots(player: Player): List<Int> {
        val contentButtons = getContentButtons(player)
        val totalSlots = getMenuSize(player)
        val rows = totalSlots / 9
        val inner = mutableListOf<Int>()

        // Get reserved slots (border + content)
        val reservedSlots = mutableSetOf<Int>()
        reservedSlots.addAll(contentButtons.keys)

        // Add border slots
        for (slot in 0 until totalSlots) {
            val row = slot / 9
            val col = slot % 9

            val isFirstOrLastRow = if (rows > 1) {
                row == 0 || row == rows - 1
            } else {
                false
            }

            if (isFirstOrLastRow || col == 0 || col == 8) {
                reservedSlots.add(slot)
            }
        }

        // All non-reserved slots are inner/available slots
        for (slot in 0 until totalSlots) {
            if (slot !in reservedSlots) {
                inner.add(slot)
            }
        }

        return inner
    }
}
