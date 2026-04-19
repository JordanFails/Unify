package me.jordanfails.unify.menu.scrolling

import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.menu.pagination.PaginatedMenu
import me.jordanfails.unify.utils.ItemBuilder
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack
import kotlin.math.min

abstract class ScrollableMenu(
    title: String = "&8Scrollable Menu"
) : PaginatedMenu() {
    private val menuTitle = title

    var scrollOffset: Int = 0

    override fun getMinSize(): Int = 54

    override fun getTitle(player: Player): String = menuTitle

    override fun getPrePaginatedTitle(player: Player): String = menuTitle

    override fun getPages(player: Player): Int {
        val step = getScrollStep(player).coerceAtLeast(1)
        val maxOffset = getMaxOffset(player)
        return if (maxOffset == 0) 1 else kotlin.math.ceil(maxOffset.toDouble() / step).toInt() + 1
    }

    override fun getButtons(player: Player): MutableMap<Int, Button> {
        val buttons = linkedMapOf<Int, Button>()
        val visibleSlots = getScrollableSlots(player)
        val allButtons = getScrollableButtons(player)
        val totalPages = getPages(player).coerceAtLeast(1)

        page = page.coerceIn(1, totalPages)
        scrollOffset = resolveScrollOffset(player)

        val endIndex = min(allButtons.size, scrollOffset + visibleSlots.size)
        val visibleButtons = allButtons.subList(scrollOffset, endIndex)

        visibleButtons.forEachIndexed { index, button ->
            buttons[visibleSlots[index]] = button
        }

        buttons.putAll(getFixedButtons(player))

        getScrollButtonSlots()?.let { (previousSlot, nextSlot) ->
            buttons[previousSlot] = ScrollButton(-1, this)
            buttons[nextSlot] = ScrollButton(1, this)
        }

        return buttons
    }

    fun scroll(player: Player, direction: Int) {
        val totalPages = getPages(player).coerceAtLeast(1)
        val nextPage = (page + direction).coerceIn(1, totalPages)
        if (nextPage == page) {
            Button.playFail(player)
            return
        }

        page = nextPage
        scrollOffset = resolveScrollOffset(player)
        Button.playClick(player)
        openMenu(player)
    }

    open fun getScrollableSlots(player: Player): List<Int> = (0..44).toList()

    open fun getScrollButtonSlots(): Pair<Int, Int>? = 45 to 53

    open fun getScrollStep(player: Player): Int = 9

    open fun getFixedButtons(player: Player): Map<Int, Button> = emptyMap()

    abstract fun getScrollableButtons(player: Player): List<Button>

    override fun getAllPagesButtons(player: Player): Map<Int, Button> {
        return getScrollableButtons(player).mapIndexed { index, button -> index to button }.toMap()
    }

    override fun getAllPagesButtonSlots(): List<Int> = emptyList()

    private fun getMaxOffset(player: Player): Int {
        val visibleSlots = getScrollableSlots(player).size.coerceAtLeast(1)
        return (getScrollableButtons(player).size - visibleSlots).coerceAtLeast(0)
    }

    private fun resolveScrollOffset(player: Player): Int {
        val step = getScrollStep(player).coerceAtLeast(1)
        return ((page - 1) * step).coerceIn(0, getMaxOffset(player))
    }

    private class ScrollButton(
        private val direction: Int,
        private val menu: ScrollableMenu
    ) : Button() {
        override fun getButtonItem(player: Player): ItemStack {
            val canScroll = if (direction < 0) menu.page > 1 else menu.page < menu.getPages(player)

            return ItemBuilder(if (canScroll) Material.ARROW else Material.BARRIER)
                .name(
                    when {
                        direction < 0 && canScroll -> "&aScroll Back"
                        direction > 0 && canScroll -> "&aScroll Forward"
                        direction < 0 -> "&cAt Start"
                        else -> "&cAt End"
                    }
                )
                .lore(
                    listOf(
                        if (canScroll) "&7Move the view by &f${menu.getScrollStep(player)}&7 slots." else "&7There is nothing more to show in this direction."
                    )
                )
                .build()
        }

        override fun clicked(player: Player, slot: Int, clickType: ClickType, view: InventoryView) {
            menu.scroll(player, direction)
        }
    }
}
