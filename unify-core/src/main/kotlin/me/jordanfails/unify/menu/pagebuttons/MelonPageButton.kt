package me.jordanfails.unify.menu.pagebuttons

import com.cryptomorin.xseries.XMaterial
import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.menu.pagination.PaginatedMenu
import me.jordanfails.unify.menu.pagination.ViewAllPagesMenu
import me.jordanfails.unify.nms.LegacyColorDataType
import me.jordanfails.unify.nms.LegacyItemColor
import me.jordanfails.unify.nms.NMSHandlerFactory
import me.jordanfails.unify.utils.CC
import me.jordanfails.unify.utils.ItemBuilder
import me.jordanfails.unify.utils.get
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack

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
        if (!hasNext(player)) {
            return if (mod > 0) {
                CC.translate("&7Last Page")
            } else {
                CC.translate("&7First Page")
            }
        }

        return if (mod > 0) {
            CC.translate("&e&lNext Page ->")
        } else {
            CC.translate("&e&l<- Previous Page")
        }
    }

    override fun getDescription(player: Player): MutableList<String> {
        return ArrayList()
    }

    override fun getDamageValue(player: Player): Byte {
        if (!hasNext(player)) {
            return NMSHandlerFactory.getHandler()
                ?.getLegacyColorData(LegacyItemColor.GRAY, LegacyColorDataType.BLOCK)
                ?: LegacyItemColor.GRAY.blockData
        }
        return 0.toByte()
    }

    override fun getMaterial(player: Player): Material {
        if (!hasNext(player)) {
            return XMaterial.GRAY_STAINED_GLASS_PANE.get() ?: Material.GRAY_STAINED_GLASS_PANE
        }
        return Material.MELON_SLICE
    }

    override fun getButtonItem(player: Player): ItemStack {
        return ItemBuilder(getMaterial(player), 1)
            .data(getDamageValue(player).toShort())
            .name(CC.translate(getName(player)))
            .lore(getDescription(player))
            .build()
    }
}
