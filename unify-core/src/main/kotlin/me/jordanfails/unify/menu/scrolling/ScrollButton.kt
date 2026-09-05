package me.jordanfails.unify.menu.scrolling

import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.utils.CC
import me.jordanfails.unify.utils.ItemBuilder
import me.jordanfails.unify.utils.SkullBuilder
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack

/**
 * Directional navigation button for a [ScrollableMenu].
 *
 * - Left-click scrolls by one step
 * - Shift-click scrolls by [ScrollableMenu.getFastScrollMultiplier]
 * - Drop (Q) resets the viewport to the origin
 */
class ScrollButton(
    private val direction: ScrollDirection,
    private val menu: ScrollableMenu
) : Button() {

    override fun clicked(player: Player, slot: Int, clickType: ClickType, view: InventoryView) {
        when {
            clickType == ClickType.DROP || clickType == ClickType.CONTROL_DROP -> {
                if (menu.offsetX != 0 || menu.offsetY != 0) {
                    menu.resetScroll(player)
                    playNeutral(player)
                } else {
                    playFail(player)
                }
            }

            menu.canScroll(player, direction) -> {
                val steps = if (clickType.isShiftClick) {
                    menu.getFastScrollMultiplier(player)
                } else {
                    1
                }
                menu.scroll(player, direction, steps)
            }

            else -> playFail(player)
        }
    }

    override fun getButtonItem(player: Player): ItemStack {
        val canScroll = menu.canScroll(player, direction)
        val stepX = menu.getScrollStepX(player)
        val stepY = menu.getScrollStepY(player)
        val step = if (direction.isHorizontal) stepX else stepY
        val fast = step * menu.getFastScrollMultiplier(player)

        val name = when {
            canScroll -> "&aScroll ${direction.label}"
            else -> "&cCan't Scroll ${direction.label}"
        }

        val lore = mutableListOf<String>()
        if (canScroll) {
            lore += "&7Left-click: move &f$step &7cell${if (step == 1) "" else "s"}"
            lore += "&7Shift-click: move &f$fast &7cells"
        } else {
            lore += "&7You've reached the edge of the view."
        }
        lore += ""
        lore += "&8Position: &7(${menu.offsetX}, ${menu.offsetY})"
        lore += "&8Drop (Q): &7reset to origin"

        return when (menu.scrollButtonType) {
            ScrollButtonType.HEAD -> headItem(name, lore, canScroll)
            ScrollButtonType.ARROW -> arrowItem(name, lore)
            ScrollButtonType.PAPER -> paperItem(name, lore)
        }
    }

    private fun headItem(name: String, lore: List<String>, canScroll: Boolean): ItemStack {
        val texture = if (canScroll) {
            textureFor(direction)
        } else {
            // Dim / blocked feel — reuse left texture but name already says can't scroll
            textureFor(direction)
        }
        return ItemBuilder(SkullBuilder().useBase64(texture).build())
            .name(CC.translate(name))
            .lore(lore.map { CC.translate(it) })
            .build()
    }

    private fun arrowItem(name: String, lore: List<String>): ItemStack {
        return ItemBuilder(Material.ARROW)
            .name(CC.translate(name))
            .lore(lore.map { CC.translate(it) })
            .build()
    }

    private fun paperItem(name: String, lore: List<String>): ItemStack {
        return ItemBuilder(Material.PAPER)
            .name(CC.translate(name))
            .lore(lore.map { CC.translate(it) })
            .build()
    }

    private fun textureFor(direction: ScrollDirection): String = when (direction) {
        ScrollDirection.UP -> CC.WOOD_ARROW_UP_TEXTURE
        ScrollDirection.DOWN -> CC.WOOD_ARROW_DOWN_TEXTURE
        ScrollDirection.LEFT -> CC.WOOD_ARROW_LEFT_TEXTURE
        ScrollDirection.RIGHT -> CC.WOOD_ARROW_RIGHT_TEXTURE
    }
}
