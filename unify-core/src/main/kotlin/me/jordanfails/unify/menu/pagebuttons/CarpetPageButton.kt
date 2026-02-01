package me.jordanfails.unify.menu.pagebuttons

import me.jordanfails.ascendduels.utils.menu.pagination.PaginatedMenu
import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.menu.pagination.ViewAllPagesMenu
import me.jordanfails.unify.utils.CC
import me.jordanfails.unify.utils.ItemBuilder
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack

class CarpetPageButton(private val mod: Int, private val menu: PaginatedMenu) : Button() {

    override fun clicked(player: Player, slot: Int, clickType: ClickType, view: InventoryView) {
        when {
            clickType == ClickType.RIGHT -> {
                ViewAllPagesMenu(menu).openMenu(player)
                playNeutral(player)
            }
            hasNext(player) -> {
                menu.modPage(player, mod)
                playNeutral(player)
            }
            else -> playFail(player)
        }
    }

    private fun hasNext(player: Player): Boolean {
        val pg = menu.page + mod
        return pg > 0 && menu.getPages(player) >= pg
    }

    override fun getName(player: Player): String {
        return if (!hasNext(player)) {
            if (mod > 0) {
                CC.translate("&7Last Page")
            } else {
                CC.translate("&7First Page")
            }
        } else {
            if (mod > 0) {
                CC.translate("&e&lNext Page ->")
            } else {
                CC.translate("&e&l<- Previous Page")
            }
        }
    }

    override fun getDescription(player: Player): MutableList<String> {
        return mutableListOf()
    }

    override fun getDamageValue(player: Player): Byte {
        return 0.toByte()
    }

    override fun getMaterial(player: Player): Material {
        return if (!hasNext(player)) {
            Material.GRAY_CARPET // last/only page – gray
        } else {
            Material.BLUE_CARPET // has more pages – dark blue
        }
    }

    override fun getButtonItem(player: Player): ItemStack {
        return ItemBuilder(getMaterial(player), 1)
            .name(CC.translate(getName(player)))
            .lore(getDescription(player))
            .build()
    }
}