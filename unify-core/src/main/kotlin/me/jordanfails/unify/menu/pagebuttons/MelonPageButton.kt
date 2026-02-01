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
import java.util.*


class MelonPageButton(private val mod: Int, private val menu: PaginatedMenu) : Button() {

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

        if(!this.hasNext(player)) {
            return if(this.mod > 0) {
                CC.translate("&7Last Page")
            } else {
                CC.translate("&7First Page")
            }
        }

        return if(this.mod > 0) {
            CC.translate("&e&lNext Page ->")
        }else{
            CC.translate("&e&l<- Previous Page")
        }
    }

    override fun getDescription(player: Player): MutableList<String> {
        return ArrayList()
    }

    override fun getDamageValue(player: Player): Byte {
        return 0.toByte()
    }

    override fun getMaterial(player: Player): Material {
        return Material.MELON_SLICE
    }

    override fun getButtonItem(player: Player): ItemStack {
        return getDescription(player).let {
            ItemBuilder(getMaterial(player), 1)
                .name(CC.translate(getName(player)))
                .lore(it)
                .build()
        }
    }

}