package me.jordanfails.unify.menu.pagination

import com.cryptomorin.xseries.XMaterial
import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.menu.MenuFillMode
import me.jordanfails.unify.menu.MenuFiller
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import kotlin.math.ceil

/**
 * A bordered variant of [PaginatedMenu].
 *
 * Automatically fills border slots with placeholder glass and places
 * paginated content in the inner area.
 *
 * ### Content vs border
 * - **[getAllPagesButtons]** — paginated content (inner slots only)
 * - **[getBorderButtons]** — controls on the glass frame (back, close, tabs…)
 * - **[getGlobalButtons]** — same priority band as border buttons: reserved
 *   from the glass and drawn above it, wherever they sit. A slot claimed by
 *   both resolves to the border button.
 * - Keys in [autoPlaceExceptions] / [getAutoPlaceExceptions] keep absolute
 *   inventory slots on every page and are **not** paginated
 * - Page navigation from [getPageButtonSlots] always wins last
 *
 * ### Absolute / exempt slots
 * ```kotlin
 * class ShopMenu : PaginatedBorderedMenu() {
 *     init { autoPlaceExceptions = setOf(22) } // info item always at slot 22
 *
 *     override fun getAllPagesButtons(player: Player) = buildMap {
 *         put(22, InfoButton())           // exempt — absolute, every page
 *         items.forEachIndexed { i, it -> put(i, ItemButton(it)) } // paginated
 *     }
 * }
 * ```
 *
 * ### ⚠️ Controlling the menu size
 *
 * Override **[getMenuSize]** (or [getMinSize]) to set a specific inventory
 * size — **do NOT override [size][me.jordanfails.unify.menu.Menu.size]**.
 *
 * The border calculation in [computeBorderSlots] reads [getMenuSize] directly.
 * Overriding [size][me.jordanfails.unify.menu.Menu.size] alone will **not**
 * affect which slots are considered border.
 *
 * Correct:
 * ```
 * override fun getMinSize(): Int = 27    // 3 rows
 * // or
 * override fun getMenuSize(): Int = 36   // 4 rows
 * ```
 *
 * Wrong (has no effect on the border):
 * ```
 * override fun size(buttons: Map<Int, Button>): Int = 9 * 3
 * ```
 */
abstract class PaginatedBorderedMenu : PaginatedMenu() {

    /**
     * Map keys from [getAllPagesButtons] that keep absolute inventory slots
     * on every page and are excluded from pagination. All other entries are
     * packed into free page content slots.
     */
    var autoPlaceExceptions: Set<Int> = emptySet()

    /**
     * Whether the border should render.
     */
    open fun hasBorder(): Boolean = true

    /**
     * Material used for the decorative border frame.
     * Override [getPlaceholderButton] when the frame item depends on the viewing player.
     */
    open var borderMaterial: XMaterial = XMaterial.BLACK_STAINED_GLASS_PANE

    /** Display name of the border filler item. Blank by default. */
    open var borderName: String = " "

    /**
     * Which regions get decorative filler. Defaults to [MenuFillMode.BORDER] (frame only) —
     * set [MenuFillMode.INNER] to fill only the non-border slots, or [MenuFillMode.BOTH] for both.
     *
     * On a paginated menu the inner fill also covers the empty page slots of a partially filled
     * last page, which is usually the point of turning it on.
     */
    open var fillMode: MenuFillMode = MenuFillMode.BORDER

    /**
     * Material used to fill leftover **inner** slots when [fillMode] fills the inner region.
     * Kept separate from [borderMaterial] so a frame and a background can differ.
     */
    open var fillMaterial: XMaterial = XMaterial.BLACK_STAINED_GLASS_PANE

    /** Display name of the inner filler item. Blank by default. */
    open var fillName: String = " "

    /**
     * Buttons placed on the **border frame** (where glass filler would go).
     *
     * Absolute inventory slots. Examples: back, close, category tabs, filters.
     * These render above the glass and above paginated content on those slots.
     *
     * Prefer this over stuffing frame controls into [getGlobalButtons], though
     * both are still applied (border buttons after global).
     */
    open fun getBorderButtons(player: Player): Map<Int, Button> = emptyMap()

    /**
     * Keys from [getAllPagesButtons] that keep absolute inventory positions
     * and never enter the page list. Defaults to [autoPlaceExceptions].
     */
    open fun getAutoPlaceExceptions(player: Player): Set<Int> = autoPlaceExceptions

    /**
     * The total menu size. Override this if you want a specific size,
     * otherwise uses [getMinSize] or defaults to 27 (3 rows).
     *
     * This is the method [computeBorderSlots] reads to determine edge slots.
     * Prefer this over overriding [size][me.jordanfails.unify.menu.Menu.size].
     *
     * @see PaginatedBorderedMenu class-level docs for the full explanation.
     */
    open fun getMenuSize(): Int {
        val minSize = getMinSize()
        return if (minSize > 0) minSize else 27
    }

    /**
     * Which slots are considered part of the border (top, bottom, sides).
     */
    open fun getBorderSlots(): List<Int> {
        return computeBorderSlots(getMenuSize())
    }

    /**
     * The placeholder (visual filler) used for empty border slots.
     * Built from [borderMaterial] / [borderName]; override to supply a custom item.
     */
    open fun getPlaceholderButton(): Button = MenuFiller.button(borderMaterial, borderName)

    /**
     * The filler used for leftover **inner** slots when [fillMode] fills the inner region.
     * Built from [fillMaterial] / [fillName]; override to supply a custom item.
     */
    open fun getFillButton(): Button = MenuFiller.button(fillMaterial, fillName)

    /**
     * Returns the "inner" (non-border) slots where paginated content is placed.
     * Automatically excludes border glass, page-nav, border-button, global-button,
     * and auto-place-exception slots.
     */
    open fun getInnerSlots(player: Player): List<Int> {
        val total = resolveMenuSize(player)
        val reservedSlots = mutableSetOf<Int>()

        reservedSlots.addAll(computeBorderSlots(total))

        getPageButtonSlots()?.let { (prev, next) ->
            reservedSlots.add(prev)
            reservedSlots.add(next)
        }

        try {
            getBorderButtons(player).keys.let { reservedSlots.addAll(it) }
        } catch (_: Exception) {
        }

        try {
            getGlobalButtons(player)?.keys?.let { reservedSlots.addAll(it) }
        } catch (_: Exception) {
        }

        try {
            reservedSlots.addAll(getAutoPlaceExceptions(player))
        } catch (_: Exception) {
        }

        return (0 until total).filter { it !in reservedSlots }
    }

    override fun getButtons(player: Player): MutableMap<Int, Button> {
        val buttons = mutableMapOf<Int, Button>()
        val menuSize = resolveMenuSize(player)
        val borderSlots = computeBorderSlots(menuSize)
        val borderButtons = try {
            getBorderButtons(player)
        } catch (_: Exception) {
            emptyMap()
        }
        val borderButtonSlots = borderButtons.keys
        // Resolved with the same tolerance as border buttons: the two are the
        // same kind of chrome, so a throwing override degrades the same way.
        val globalButtons = try {
            getGlobalButtons(player).orEmpty()
        } catch (_: Exception) {
            emptyMap()
        }
        val exceptions = try {
            getAutoPlaceExceptions(player)
        } catch (_: Exception) {
            emptySet()
        }
        val navSlots = getPageButtonSlots()
        // Edge slots reserved for chrome — glass must never cover these.
        // Global buttons are reserved alongside border buttons: both are frame
        // controls, and leaving globals out meant one placed on an edge slot
        // was silently painted over with glass.
        val frameReserved = buildSet {
            addAll(borderButtonSlots)
            addAll(globalButtons.keys)
            addAll(exceptions)
            navSlots?.let { (prev, next) ->
                add(prev)
                add(next)
            }
        }

        // ── ① Exempt absolute buttons (every page, fixed inventory slots)
        val allButtons = getAllPagesButtons(player)
        for ((key, btn) in allButtons) {
            if (key in exceptions && key in 0 until menuSize) {
                buttons[key] = btn
            }
        }

        // ── ② Paginated content (inner slots only; exceptions excluded)
        val paginated = allButtons.entries
            .filter { it.key !in exceptions }
            .sortedBy { it.key }
            .toList()
        val pageSlots = getPageContentSlots(player, borderSlots, exceptions)
        val perPage = pageSlots.size.coerceAtLeast(1)

        val total = paginated.size
        val totalPages = ceil(total.toDouble() / perPage).toInt().coerceAtLeast(1)
        page = page.coerceIn(1, totalPages)

        val start = (page - 1) * perPage
        val end = (start + perPage).coerceAtMost(total)
        val visible = paginated.subList(start, end)

        visible.forEachIndexed { idx, entry ->
            if (idx < pageSlots.size) {
                buttons[pageSlots[idx]] = entry.value
            }
        }


        // ── ③ Border glass seals the frame after content so leaked absolute
        //     keys cannot punch holes in the decorative edges.
        if (hasBorder() && fillMode.fillsBorder) {
            val filler = getPlaceholderButton()
            for (slot in borderSlots) {
                if (slot !in frameReserved) {
                    buttons[slot] = filler
                }
            }
        }

        // ── ④ Frame controls, above the glass. Globals and border buttons are
        //     one band: whichever map a control is declared in, it lands on the
        //     frame and outranks glass and content alike. Globals go on first
        //     so that a slot claimed by both resolves to the border button,
        //     which is the more specific of the two APIs.
        for ((slot, btn) in globalButtons) {
            if (slot in 0 until menuSize) {
                buttons[slot] = btn
            }
        }

        for ((slot, btn) in borderButtons) {
            if (slot in 0 until menuSize) {
                buttons[slot] = btn
            }
        }

        // ── ⑤ Page navigation (always placed; buttons render gray glass when disabled)
        if (navSlots != null) {
            createPageButton(-1)?.let { buttons[navSlots.first] = it }
            createPageButton(1)?.let { buttons[navSlots.second] = it }
        }

        // ── ⑥ Inner filler, last and only on slots that are still empty (including the unused
        //     page slots of a partial last page), so it can never cover content or chrome.
        if (fillMode.fillsInner) {
            val filler = getFillButton()
            val borderSlotSet = borderSlots.toSet()
            for (slot in 0 until menuSize) {
                if (hasBorder() && slot in borderSlotSet) continue
                if (slot !in buttons) {
                    buttons[slot] = filler
                }
            }
        }

        return buttons
    }

    override fun getTitle(player: Player): String {
        return getPrePaginatedTitle(player) +
                ChatColor.RESET + " " +
                ChatColor.GRAY + "(${page}/${getPages(player)})"
    }

    override fun getPages(player: Player): Int {
        val exceptions = try {
            getAutoPlaceExceptions(player)
        } catch (_: Exception) {
            emptySet()
        }
        val total = getAllPagesButtons(player).keys.count { it !in exceptions }
        val perPage = getPageContentSlots(player).size.coerceAtLeast(1)
        return ceil(total / perPage.toDouble()).toInt().coerceAtLeast(1)
    }

    override fun getMaxItemsPerPage(player: Player): Int {
        return getPageContentSlots(player).size
    }

    override fun getButtonsStartOffset(): Int = 0

    private fun resolveMenuSize(player: Player): Int {
        val configuredSize = getMenuSize()
        var highestSlot = configuredSize - 1

        getPageButtonSlots()?.let { (prev, next) ->
            highestSlot = maxOf(highestSlot, prev, next)
        }

        getAllPagesButtonSlots().maxOrNull()?.let { highestSlot = maxOf(highestSlot, it) }

        try {
            getAutoPlaceExceptions(player).maxOrNull()?.let { highestSlot = maxOf(highestSlot, it) }
        } catch (_: Exception) {
        }

        try {
            getBorderButtons(player).keys.maxOrNull()?.let { highestSlot = maxOf(highestSlot, it) }
        } catch (_: Exception) {
        }

        try {
            getGlobalButtons(player)?.keys?.maxOrNull()?.let { highestSlot = maxOf(highestSlot, it) }
        } catch (_: Exception) {
        }

        val minSlots = if (hasBorder()) 27 else 9
        val resolved = (((highestSlot + 1) + 8) / 9) * 9
        return maxOf(configuredSize, minSlots, resolved)
    }

    private fun computeBorderSlots(totalSlots: Int): List<Int> {
        val borderSlots = mutableListOf<Int>()
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
                borderSlots += slot
            }
        }

        return borderSlots
    }

    private fun getPageContentSlots(
        player: Player,
        borderSlots: List<Int> = computeBorderSlots(resolveMenuSize(player)),
        exceptions: Set<Int> = try {
            getAutoPlaceExceptions(player)
        } catch (_: Exception) {
            emptySet()
        }
    ): List<Int> {
        val reserved = borderSlots.toSet() + exceptions
        val rawPageSlots = getAllPagesButtonSlots().ifEmpty { getInnerSlots(player) }
        return rawPageSlots.filter { it !in reserved }
    }
}
