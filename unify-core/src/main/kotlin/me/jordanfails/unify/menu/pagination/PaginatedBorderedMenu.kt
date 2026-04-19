package me.jordanfails.unify.menu.pagination

import com.cryptomorin.xseries.XMaterial
import me.jordanfails.unify.menu.pagebuttons.ArrowPageButton
import me.jordanfails.unify.menu.pagebuttons.PaperPageButton
import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.menu.menus.PageButtonType
import me.jordanfails.unify.menu.pagebuttons.CarpetPageButton
import me.jordanfails.unify.menu.pagebuttons.HeadPageButton
import me.jordanfails.unify.menu.pagebuttons.MelonPageButton
import me.jordanfails.unify.nms.LegacyColorDataType
import me.jordanfails.unify.nms.LegacyItemColor
import me.jordanfails.unify.nms.NMSHandlerFactory
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import kotlin.math.ceil

/**
 * A bordered variant of [PaginatedMenu].
 *
 * Automatically fills border slots with placeholder buttons
 * and provides paginated content in the inner area.
 */
abstract class PaginatedBorderedMenu : PaginatedMenu() {

    /**
     * Whether the border should render.
     */
    open fun hasBorder(): Boolean = true


    /**
     * The total menu size. Override this if you want a specific size,
     * otherwise uses getMinSize() or defaults to 27 (3 rows).
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
     * Returns the "inner" (non-border) slots where paginated content is placed.
     * This automatically excludes any slots used by global buttons and page buttons.
     */
    open fun getInnerSlots(player: Player): List<Int> {
        val total = resolveMenuSize(player)
        val innerSlots = mutableListOf<Int>()

        // Get slots that are reserved (border, page buttons, global buttons)
        val reservedSlots = mutableSetOf<Int>()

        // Add border slots
        reservedSlots.addAll(computeBorderSlots(total))

        // Add page navigation button slots
        getPageButtonSlots()?.let { (prev, next) ->
            reservedSlots.add(prev)
            reservedSlots.add(next)
        }

        // Add global button slots (safely handle potential recursion)
        try {
            getGlobalButtons(player)?.keys?.let { reservedSlots.addAll(it) }
        } catch (e: Exception) {
            // If getGlobalButtons causes issues, just skip it
        }

        // All non-reserved slots are available for pagination
        for (slot in 0 until total) {
            if (slot !in reservedSlots) {
                innerSlots.add(slot)
            }
        }

        return innerSlots
    }

    override fun getButtons(player: Player): MutableMap<Int, Button> {
        val buttons = mutableMapOf<Int, Button>()
        val borderSlots = computeBorderSlots(resolveMenuSize(player))
        val blackPaneData = NMSHandlerFactory.getHandler()?.getLegacyColorData(LegacyItemColor.BLACK, LegacyColorDataType.BLOCK)
            ?: LegacyItemColor.BLACK.blockData

        // ── ① Border first (pure decoration) - LOWEST PRIORITY
        if (hasBorder()) {
            val filler = Button.placeholder(XMaterial.BLACK_STAINED_GLASS_PANE, blackPaneData, " ")
            for (slot in borderSlots) {
                buttons[slot] = filler
            }
        }

        // ── ② Paginated CONTENT zone - MEDIUM PRIORITY (never overwrites border)
        val all = getAllPagesButtons(player).entries.toList()
        val pageSlots = getPageContentSlots(player, borderSlots)
        val perPage = pageSlots.size.coerceAtLeast(1)

        val total = all.size
        val totalPages = ceil(total.toDouble() / perPage).toInt().coerceAtLeast(1)
        page = page.coerceIn(1, totalPages)

        val start = (page - 1) * perPage
        val end = (start + perPage).coerceAtMost(total)
        val visible = all.subList(start, end)

        visible.forEachIndexed { idx, entry ->
            if (idx < pageSlots.size) {
                buttons[pageSlots[idx]] = entry.value
            }
        }

        // ── ③ Global or utility buttons - HIGH PRIORITY (overwrites content/border)
        getGlobalButtons(player)?.forEach { (slot, btn) ->
            buttons[slot] = btn
        }

        // ── ④ Page navigation buttons - HIGHEST PRIORITY (overwrites everything)
        val nav = getPageButtonSlots()
        if (nav != null) {
            if (page > 1) {
                createPageButton(-1)?.let { buttons[nav.first] = it }
            }
            if (page < totalPages) {
                createPageButton(1)?.let { buttons[nav.second] = it }
            }
        }

        // ── ⑤ Border LAST - ABSOLUTE HIGHEST PRIORITY (always on top)
        if (hasBorder()) {
            val filler = Button.placeholder(XMaterial.BLACK_STAINED_GLASS_PANE, blackPaneData, " ")
            for (slot in borderSlots) {
                if (buttons[slot] == null) {
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
        val total = getAllPagesButtons(player).size
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
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                borderSlots += slot
            }
        }

        return borderSlots
    }

    private fun getPageContentSlots(player: Player, borderSlots: List<Int> = computeBorderSlots(resolveMenuSize(player))): List<Int> {
        val borderSlotSet = borderSlots.toSet()
        val rawPageSlots = getAllPagesButtonSlots().ifEmpty { getInnerSlots(player) }
        return rawPageSlots.filter { it !in borderSlotSet }
    }
}
