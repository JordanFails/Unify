package me.jordanfails.ascendduels.utils.menu.pagination

import me.jordanfails.unify.menu.pagebuttons.ArrowPageButton
import me.jordanfails.unify.menu.pagebuttons.PaperPageButton
import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.menu.Menu
import me.jordanfails.unify.menu.menus.PageButtonType
import me.jordanfails.unify.menu.pagebuttons.CarpetPageButton
import me.jordanfails.unify.menu.pagebuttons.MelonPageButton
import me.jordanfails.unify.menu.pagination.PageButton
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import java.util.HashMap
import kotlin.collections.iterator
import kotlin.math.ceil
import kotlin.math.min

abstract class PaginatedMenu : Menu() {

    var page: Int = 1
    var verticalView: Boolean = false
    var pageButtonType: PageButtonType = PageButtonType.HEAD

    override fun getTitle(player: Player): String {
        return getPrePaginatedTitle(player) + ChatColor.RESET.toString() + " - " + page + "/" + getPages(player)
    }

    fun modPage(player: Player, mod: Int) {
        page += mod
        buttons.clear()
        openMenu(player)
    }

    open fun getPages(player: Player): Int {
        val buttonAmount = getAllPagesButtons(player).size

        return if (buttonAmount == 0) {
            1
        } else {
            ceil(buttonAmount / getMaxItemsPerPage(player).toDouble()).toInt()
        }
    }

    override fun getButtons(player: Player): MutableMap<Int, Button> {
        val buttons = HashMap<Int, Button>()

        // ── ① page navigation buttons
        getPageButtonSlots()?.let { slots ->
            when (pageButtonType) {
                PageButtonType.HEAD -> {
                    buttons[slots.first] = PageButton(-1, this)
                    buttons[slots.second] = PageButton(1, this)
                }
                PageButtonType.ARROW -> {
                    buttons[slots.first] = ArrowPageButton(-1, this)
                    buttons[slots.second] = ArrowPageButton(1, this)
                }
                PageButtonType.PAPER -> {
                    buttons[slots.first] = PaperPageButton(-1, this)
                    buttons[slots.second] = PaperPageButton(1, this)
                }

                PageButtonType.CARPET -> {
                    buttons[slots.first] = CarpetPageButton(-1, this)
                    buttons[slots.second] = CarpetPageButton(1, this)
                }

                PageButtonType.MELON -> {
                    buttons[slots.first] = MelonPageButton(-1, this)
                    buttons[slots.second] = MelonPageButton(1, this)
                }
            }
        }

        // insert entry buttons
        val buttonSlots = getAllPagesButtonSlots()
        if (buttonSlots.isEmpty()) {
            val minIndex = ((page - 1) * getMaxItemsPerPage(player).toDouble()).toInt()
            val maxIndex = (page * getMaxItemsPerPage(player).toDouble()).toInt()

            for (entry in getAllPagesButtons(player).entries) {
                var ind = entry.key
                if (ind in minIndex until maxIndex) {
                    ind -= (getMaxItemsPerPage(player) * (page - 1).toDouble()).toInt() - 9
                    buttons[getButtonsStartOffset() + ind] = entry.value
                }
            }
        } else {
            val maxPerPage = buttonSlots.size
            val minIndex = (page - 1) * maxPerPage
            val maxIndex = page * maxPerPage

            for ((index, entry) in getAllPagesButtons(player).entries.withIndex()) {
                if (index in minIndex until min(maxIndex, buttonSlots.size)) {
                    buttons[buttonSlots[index]] = entry.value
                }
            }
        }

        // insert global buttons AFTER inserting entry buttons
        val global = getGlobalButtons(player)
        if (global != null) {
            for ((key, value) in global) {
                buttons[key] = value
            }
        }

        return buttons
    }

    open fun getMaxItemsPerPage(player: Player): Int {
        return 18
    }

    open fun getButtonsStartOffset(): Int {
        return 0
    }

    open fun getPageButtonSlots(): Pair<Int, Int>? {
        return Pair(0, 8)
    }

    open fun getGlobalButtons(player: Player): Map<Int, Button>? {
        return null
    }

    abstract fun getPrePaginatedTitle(player: Player): String

    abstract fun getAllPagesButtons(player: Player): Map<Int, Button>

    open fun getAllPagesButtonSlots(): List<Int> {
        return emptyList()
    }

}