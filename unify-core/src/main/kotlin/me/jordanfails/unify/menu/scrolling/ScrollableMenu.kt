package me.jordanfails.unify.menu.scrolling

import com.cryptomorin.xseries.XMaterial
import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.menu.Menu
import me.jordanfails.unify.nms.LegacyColorDataType
import me.jordanfails.unify.nms.LegacyItemColor
import me.jordanfails.unify.nms.NMSHandlerFactory
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import kotlin.math.max

/**
 * A menu that shows a **viewport** into a larger virtual content grid.
 *
 * Ideal for skill trees, large maps, or any layout bigger than a chest inventory.
 * Content lives at absolute `(x, y)` coordinates; the inventory only shows the
 * window starting at ([offsetX], [offsetY]).
 *
 * ### Basic usage (skill tree)
 * ```kotlin
 * class SkillTreeMenu : ScrollableMenu("&8Skill Tree") {
 *     override fun getContentWidth(player: Player) = 16
 *     override fun getContentHeight(player: Player) = 12
 *
 *     override fun getContentButtons(player: Player): Map<ScrollPosition, Button> = mapOf(
 *         ScrollPosition(0, 0) to RootNodeButton(),
 *         ScrollPosition(2, 1) to FireballButton(),
 *         ScrollPosition(4, 1) to IceBoltButton(),
 *         // sparse placements are fine — empty cells stay empty (or use a filler)
 *     )
 * }
 * ```
 *
 * ### List layout (flowing left-to-right)
 * ```kotlin
 * override fun getContentButtons(player: Player) =
 *     layoutList(myButtons, width = 7)
 *
 * override fun getContentWidth(player: Player) = 7
 * override fun getContentHeight(player: Player) =
 *     (myButtons.size + 6) / 7
 * ```
 *
 * ### Size control
 * Override **[getMenuSize]** or [getMinSize] — **do not** override
 * [size][Menu.size]. Border and viewport math read [getMenuSize] directly
 * (same contract as [me.jordanfails.unify.menu.menus.menus.BorderedMenu]).
 *
 * ### Layout layers (low → high priority)
 * 1. Border filler
 * 2. Empty viewport fillers (optional)
 * 3. Visible content buttons
 * 4. Fixed / global buttons
 * 5. Scroll navigation buttons
 */
abstract class ScrollableMenu(
    private val scrollableTitle: String = "&8Scrollable Menu"
) : Menu(scrollableTitle) {

    /** Horizontal offset into the virtual content grid (columns). */
    var offsetX: Int = 0

    /** Vertical offset into the virtual content grid (rows). */
    var offsetY: Int = 0

    /** Visual style for directional scroll buttons. */
    var scrollButtonType: ScrollButtonType = ScrollButtonType.HEAD

    /**
     * When true, empty viewport cells receive [getEmptyContentButton].
     * Useful for a uniform skill-tree background behind nodes.
     */
    var fillEmptyContent: Boolean = false

    // ── Content ──────────────────────────────────────────────────────────────

    /**
     * Buttons placed on the virtual content grid.
     * Keys are absolute coordinates — not inventory slots.
     *
     * Sparse maps are fine; only positions that fall inside the current
     * viewport are rendered.
     */
    abstract fun getContentButtons(player: Player): Map<ScrollPosition, Button>

    /**
     * Width of the virtual content grid (columns).
     *
     * Defaults to `max(content x) + 1`, floored at the viewport width.
     * Override for skill trees with known dimensions (padding beyond the
     * last node is often desirable).
     */
    open fun getContentWidth(player: Player): Int {
        val maxX = getContentButtons(player).keys.maxOfOrNull { it.x } ?: -1
        return max(maxX + 1, getViewportWidth(player))
    }

    /**
     * Height of the virtual content grid (rows).
     *
     * Defaults to `max(content y) + 1`, floored at the viewport height.
     */
    open fun getContentHeight(player: Player): Int {
        val maxY = getContentButtons(player).keys.maxOfOrNull { it.y } ?: -1
        return max(maxY + 1, getViewportHeight(player))
    }

    /**
     * Lays out a linear list into a grid of the given [width], left-to-right
     * then top-to-bottom. Handy for simple scrollable lists.
     */
    protected fun layoutList(buttons: List<Button>, width: Int): Map<ScrollPosition, Button> {
        val w = width.coerceAtLeast(1)
        return buttons.mapIndexed { index, button ->
            ScrollPosition(index % w, index / w) to button
        }.toMap()
    }

    // ── Viewport / size ──────────────────────────────────────────────────────

    /**
     * Total inventory size in slots. Prefer this (or [getMinSize]) over
     * overriding [size].
     */
    open fun getMenuSize(player: Player): Int {
        val minSize = getMinSize().takeIf { it > 0 } ?: 54
        var highest = minSize - 1

        getScrollButtonSlots(player).values.forEach { highest = max(highest, it) }
        getFixedButtons(player).keys.forEach { highest = max(highest, it) }

        val resolved = (((highest + 1) + 8) / 9) * 9
        return resolved.coerceIn(if (hasBorder()) 27 else 9, 54)
    }

    override fun getMinSize(): Int = 54

    /**
     * Whether decorative border panes should be drawn around the edges.
     */
    open fun hasBorder(): Boolean = true

    /**
     * Inventory slots that make up the scrollable viewport, in row-major order.
     *
     * Default: inner slots of a bordered chest (cols 1–7, excluding top/bottom
     * border rows and side columns). Override for custom layouts.
     *
     * Fixed and scroll-button slots are excluded automatically.
     */
    open fun getViewportSlots(player: Player): List<Int> {
        val total = getMenuSize(player)
        val reserved = reservedNonContentSlots(player, total)

        return if (hasBorder()) {
            val rows = total / 9
            (0 until total).filter { slot ->
                if (slot in reserved) return@filter false
                val row = slot / 9
                val col = slot % 9
                val edgeRow = rows > 1 && (row == 0 || row == rows - 1)
                !edgeRow && col != 0 && col != 8
            }
        } else {
            (0 until total).filter { it !in reserved }
        }
    }

    /**
     * Width of the viewport in columns (derived from [getViewportSlots] by default).
     */
    open fun getViewportWidth(player: Player): Int {
        val slots = getViewportSlots(player)
        if (slots.isEmpty()) return 1
        val byRow = slots.groupBy { it / 9 }
        return byRow.values.maxOfOrNull { it.size } ?: 1
    }

    /**
     * Height of the viewport in rows (derived from [getViewportSlots] by default).
     */
    open fun getViewportHeight(player: Player): Int {
        val slots = getViewportSlots(player)
        if (slots.isEmpty()) return 1
        return slots.map { it / 9 }.toSet().size.coerceAtLeast(1)
    }

    // ── Scrolling ────────────────────────────────────────────────────────────

    /** Columns moved per horizontal scroll step. */
    open fun getScrollStepX(player: Player): Int = 1

    /** Rows moved per vertical scroll step. */
    open fun getScrollStepY(player: Player): Int = 1

    /** Multiplier applied when the player shift-clicks a scroll button. */
    open fun getFastScrollMultiplier(player: Player): Int = 3

    /**
     * Inventory slot for each directional scroll button.
     *
     * Default (6-row bordered chest):
     * - UP → 4 (top center)
     * - DOWN → 49 (bottom center)
     * - LEFT → 45 (bottom left)
     * - RIGHT → 53 (bottom right)
     *
     * Return an empty map to hide navigation buttons.
     */
    open fun getScrollButtonSlots(player: Player): Map<ScrollDirection, Int> = mapOf(
        ScrollDirection.UP to 4,
        ScrollDirection.DOWN to 49,
        ScrollDirection.LEFT to 45,
        ScrollDirection.RIGHT to 53
    )

    /**
     * When true, scroll buttons for directions that cannot move are omitted.
     * When false (default), they remain visible in a disabled style.
     */
    open fun hideDisabledScrollButtons(): Boolean = false

    fun canScroll(player: Player, direction: ScrollDirection): Boolean {
        val (maxX, maxY) = maxOffsets(player)
        return when (direction) {
            ScrollDirection.UP -> offsetY > 0
            ScrollDirection.DOWN -> offsetY < maxY
            ScrollDirection.LEFT -> offsetX > 0
            ScrollDirection.RIGHT -> offsetX < maxX
        }
    }

    /**
     * Scrolls the viewport in [direction] by [steps] step units
     * (each step uses [getScrollStepX] / [getScrollStepY]).
     */
    fun scroll(player: Player, direction: ScrollDirection, steps: Int = 1) {
        if (steps <= 0 || !canScroll(player, direction)) {
            Button.playFail(player)
            return
        }

        val stepX = getScrollStepX(player).coerceAtLeast(1) * steps
        val stepY = getScrollStepY(player).coerceAtLeast(1) * steps
        val (maxX, maxY) = maxOffsets(player)

        val nextX = (offsetX + direction.deltaX * stepX).coerceIn(0, maxX)
        val nextY = (offsetY + direction.deltaY * stepY).coerceIn(0, maxY)

        if (nextX == offsetX && nextY == offsetY) {
            Button.playFail(player)
            return
        }

        offsetX = nextX
        offsetY = nextY
        Button.playClick(player)
        openMenu(player)
    }

    /** Jumps the viewport so [position] sits near the top-left of the view. */
    fun scrollTo(player: Player, position: ScrollPosition) {
        val (maxX, maxY) = maxOffsets(player)
        offsetX = position.x.coerceIn(0, maxX)
        offsetY = position.y.coerceIn(0, maxY)
        openMenu(player)
    }

    /** Centers the viewport on [position] when content is larger than the view. */
    fun centerOn(player: Player, position: ScrollPosition) {
        val viewW = getViewportWidth(player)
        val viewH = getViewportHeight(player)
        val (maxX, maxY) = maxOffsets(player)

        offsetX = (position.x - viewW / 2).coerceIn(0, maxX)
        offsetY = (position.y - viewH / 2).coerceIn(0, maxY)
        openMenu(player)
    }

    fun resetScroll(player: Player) {
        offsetX = 0
        offsetY = 0
        openMenu(player)
    }

    // ── Fixed chrome ─────────────────────────────────────────────────────────

    /**
     * Buttons that never scroll — back, info, category tabs, etc.
     * Applied after content; can occupy border/nav slots when desired.
     */
    open fun getFixedButtons(player: Player): Map<Int, Button> = emptyMap()

    /** Placeholder used for border edge slots. */
    open fun getBorderButton(): Button =
        Button.placeholder(
            XMaterial.BLACK_STAINED_GLASS_PANE,
            NMSHandlerFactory.getHandler()?.getLegacyColorData(LegacyItemColor.GRAY, LegacyColorDataType.BLOCK)
                ?: LegacyItemColor.GRAY.blockData,
            " "
        )

    /**
     * Optional filler for empty cells inside the viewport.
     * Only used when [fillEmptyContent] is true.
     */
    open fun getEmptyContentButton(player: Player): Button =
        Button.placeholder(
            XMaterial.BLACK_STAINED_GLASS_PANE,
            NMSHandlerFactory.getHandler()?.getLegacyColorData(LegacyItemColor.BLACK, LegacyColorDataType.BLOCK)
                ?: LegacyItemColor.BLACK.blockData,
            " "
        )

    // ── Title ────────────────────────────────────────────────────────────────

    /**
     * Bare title without scroll coordinates.
     * Override for dynamic titles (player name, tree name, etc.).
     */
    open fun getScrollableTitle(player: Player): String = scrollableTitle

    /**
     * When true (default), appends `(x,y)` scroll offset to the title while
     * the content is larger than the viewport.
     */
    open fun showOffsetInTitle(): Boolean = true

    final override fun getTitle(player: Player): String {
        val base = getScrollableTitle(player)
        if (!showOffsetInTitle()) return base

        val (maxX, maxY) = maxOffsets(player)
        return if (maxX == 0 && maxY == 0) {
            base
        } else {
            "$base${ChatColor.RESET}${ChatColor.GRAY} (${offsetX},${offsetY})"
        }
    }

    // ── Assembly ─────────────────────────────────────────────────────────────

    final override fun getButtons(player: Player): MutableMap<Int, Button> {
        val buttons = linkedMapOf<Int, Button>()
        val total = getMenuSize(player)

        // Clamp offsets before painting
        val (maxX, maxY) = maxOffsets(player)
        offsetX = offsetX.coerceIn(0, maxX)
        offsetY = offsetY.coerceIn(0, maxY)

        // ① Border (lowest priority)
        if (hasBorder()) {
            val border = getBorderButton()
            for (slot in computeBorderSlots(total)) {
                buttons[slot] = border
            }
        }

        // ② Viewport content
        val viewport = getViewportSlots(player)
        val content = getContentButtons(player)
        val viewportByLocal = mapViewportLocalCoords(viewport)

        if (fillEmptyContent) {
            val empty = getEmptyContentButton(player)
            for (slot in viewport) {
                buttons[slot] = empty
            }
        }

        for ((pos, button) in content) {
            val localX = pos.x - offsetX
            val localY = pos.y - offsetY
            if (localX < 0 || localY < 0) continue

            val slot = viewportByLocal[localX to localY] ?: continue
            buttons[slot] = button
        }

        // ③ Fixed chrome
        buttons.putAll(getFixedButtons(player))

        // ④ Scroll navigation (highest priority among controls)
        for ((direction, slot) in getScrollButtonSlots(player)) {
            if (slot !in 0 until total) continue
            if (hideDisabledScrollButtons() && !canScroll(player, direction)) continue
            buttons[slot] = ScrollButton(direction, this)
        }

        return buttons
    }

    /**
     * Enforces [getMenuSize] / [getMinSize] so border math stays consistent
     * even when few buttons are present.
     */
    final override fun size(buttons: Map<Int, Button>): Int {
        val minSize = getMinSize().takeIf { it > 0 } ?: 54
        val fromButtons = super.size(buttons)
        return max(minSize, fromButtons).coerceAtMost(54)
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private fun maxOffsets(player: Player): Pair<Int, Int> {
        val contentW = getContentWidth(player).coerceAtLeast(1)
        val contentH = getContentHeight(player).coerceAtLeast(1)
        val viewW = getViewportWidth(player).coerceAtLeast(1)
        val viewH = getViewportHeight(player).coerceAtLeast(1)

        val maxX = (contentW - viewW).coerceAtLeast(0)
        val maxY = (contentH - viewH).coerceAtLeast(0)
        return maxX to maxY
    }

    private fun computeBorderSlots(totalSlots: Int): List<Int> {
        val borderSlots = mutableListOf<Int>()
        val rows = totalSlots / 9

        for (slot in 0 until totalSlots) {
            val row = slot / 9
            val col = slot % 9
            val isFirstOrLastRow = rows > 1 && (row == 0 || row == rows - 1)
            if (isFirstOrLastRow || col == 0 || col == 8) {
                borderSlots += slot
            }
        }
        return borderSlots
    }

    private fun reservedNonContentSlots(player: Player, total: Int): Set<Int> {
        val reserved = mutableSetOf<Int>()
        getScrollButtonSlots(player).values.forEach { reserved += it }
        getFixedButtons(player).keys.forEach { reserved += it }
        return reserved.filter { it in 0 until total }.toSet()
    }

    /**
     * Maps local viewport coordinates `(localX, localY)` → inventory slot.
     *
     * Uses actual slot geometry so irregular viewports still work: slots are
     * grouped by inventory row, then ordered by column.
     */
    private fun mapViewportLocalCoords(viewportSlots: List<Int>): Map<Pair<Int, Int>, Int> {
        if (viewportSlots.isEmpty()) return emptyMap()

        val rows = viewportSlots
            .groupBy { it / 9 }
            .toSortedMap()
            .values
            .map { rowSlots -> rowSlots.sorted() }

        val map = HashMap<Pair<Int, Int>, Int>(viewportSlots.size)
        rows.forEachIndexed { localY, rowSlots ->
            rowSlots.forEachIndexed { localX, slot ->
                map[localX to localY] = slot
            }
        }
        return map
    }
}
