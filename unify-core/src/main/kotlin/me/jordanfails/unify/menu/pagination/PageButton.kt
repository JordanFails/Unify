package me.jordanfails.unify.menu.pagination

import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.utils.CC
import me.jordanfails.unify.utils.ItemBuilder
import me.jordanfails.unify.utils.SkullBuilder
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack
import java.util.*


class PageButton(private val mod: Int, private val menu: PaginatedMenu) : Button() {

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
        if (!this.hasNext(player)) {
            return if (this.mod > 0) {
                "&7Last Page"
            } else {
                "&7First Page"
            }
        }

        return if (this.mod > 0) {
            "${ChatColor.YELLOW}(${(menu.page + mod)}/${menu.getPages(player)}) ->"
        } else {
            "${ChatColor.YELLOW}<- (${(menu.page + mod)}/${menu.getPages(player)})"
        }
    }

    override fun getDescription(player: Player): MutableList<String> {
        return ArrayList()
    }

    override fun getDamageValue(player: Player): Byte {
        return 3.toByte()
    }

    override fun getMaterial(player: Player): Material {
        return Material.PLAYER_HEAD
    }


    override fun getButtonItem(player: Player): ItemStack {
        val texture = if (menu.verticalView) {
            if (mod >= 1) {
                CC.WOOD_ARROW_DOWN_TEXTURE
            } else {
                CC.WOOD_ARROW_UP_TEXTURE
            }
        } else {
            if (mod >= 1) {
                CC.WOOD_ARROW_RIGHT_TEXTURE
            } else {
                CC.WOOD_ARROW_LEFT_TEXTURE
            }
        }
        return ItemBuilder(
            SkullBuilder()
                .useBase64(texture)
                .build()
        )
            .lore(getDescription(player).toList())
            .name(getName(player))
            .build()
    }

}
