package me.jordanfails.unify.menu.pagination

import me.jordanfails.unify.menu.pagebuttons.ArrowPageButton
import me.jordanfails.unify.menu.pagebuttons.PaperPageButton
import me.jordanfails.ascendduels.utils.menu.pagination.PaginatedMenu
import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.menu.menus.PageButtonType
import me.jordanfails.unify.menu.pagebuttons.CarpetPageButton
import me.jordanfails.unify.menu.pagebuttons.MelonPageButton
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import kotlin.math.ceil

/**
 * A bordered variant of [me.jordanfails.ascendduels.utils.menu.pagination.PaginatedMenu].
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
     * otherwise uses getMinSize() or defaults to 54 (6 rows).
     */
    open fun getMenuSize(): Int {
        val minSize = getMinSize()
        return if (minSize > 0) minSize else 54
    }

    /**
     * Which slots are considered part of the border (top, bottom, sides).
     */
    open fun getBorderSlots(): List<Int> {
        val total = getMenuSize()
        val borderSlots = mutableListOf<Int>()
        val rows = total / 9

        for (slot in 0 until total) {
            val row = slot / 9
            val col = slot % 9
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                borderSlots += slot
            }
        }

        return borderSlots
    }

    /**
     * Returns the "inner" (non-border) slots where paginated content is placed.
     * This automatically excludes any slots used by global buttons and page buttons.
     */
    open fun getInnerSlots(player: Player): List<Int> {
        val total = getMenuSize()
        val innerSlots = mutableListOf<Int>()
        val rows = total / 9

        // Get slots that are reserved (border, page buttons, global buttons)
        val reservedSlots = mutableSetOf<Int>()

        // Add border slots
        for (slot in 0 until total) {
            val row = slot / 9
            val col = slot % 9
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                reservedSlots.add(slot)
            }
        }

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

        // ── ① Border first (pure decoration) - LOWEST PRIORITY
        if (hasBorder()) {
            val filler = Button.placeholder(Material.BLACK_STAINED_GLASS_PANE, 15.toByte(), " ")
            for (slot in getBorderSlots()) {
                buttons[slot] = filler
            }
        }

        // ── ② Paginated CONTENT zone - MEDIUM PRIORITY (overwrites border if needed)
        val all = getAllPagesButtons(player).entries.toList()
        val pageSlots = getAllPagesButtonSlots().ifEmpty { getInnerSlots(player) }
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
            when (pageButtonType) {
                PageButtonType.HEAD -> {
                    buttons[nav.first] = PageButton(-1, this)
                    buttons[nav.second] = PageButton(1, this)
                }

                PageButtonType.ARROW -> {
                    buttons[nav.first] = ArrowPageButton(-1, this)
                    buttons[nav.second] = ArrowPageButton(1, this)
                }

                PageButtonType.PAPER -> {
                    buttons[nav.first] = PaperPageButton(-1, this)
                    buttons[nav.second] = PaperPageButton(1, this)
                }

                PageButtonType.CARPET -> {
                    buttons[nav.first] = CarpetPageButton(-1, this)
                    buttons[nav.second] = CarpetPageButton(1, this)
                }

                PageButtonType.MELON -> {
                    buttons[nav.first] = MelonPageButton(-1, this)
                    buttons[nav.second] = MelonPageButton(1, this)
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
        val perPage = getInnerSlots(player).size.coerceAtLeast(1)
        return ceil(total / perPage.toDouble()).toInt().coerceAtLeast(1)
    }

    override fun getMaxItemsPerPage(player: Player): Int {
        return getInnerSlots(player).size
    }

    override fun getButtonsStartOffset(): Int = 0
}