package me.jordanfails.unify.menu.pagebuttons

import com.cryptomorin.xseries.XMaterial
import me.jordanfails.unify.menu.pagination.PaginatedMenu
import me.jordanfails.unify.menu.pagination.ViewAllPagesMenu
import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.utils.CC
import me.jordanfails.unify.utils.ItemBuilder
import me.jordanfails.unify.utils.SkullBuilder
import me.jordanfails.unify.utils.get
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack
import java.util.*


class HeadPageButton(private val mod: Int, private val menu: PaginatedMenu) : Button() {

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
            CC.translate("&aNext Page ->")
        }else{
            CC.translate("&c<- Previous Page")
        }
    }

    override fun getDescription(player: Player): MutableList<String> {
        return ArrayList()
    }

    override fun getDamageValue(player: Player): Byte {
        return 0.toByte()
    }

    override fun getMaterial(player: Player): Material {
        return XMaterial.PLAYER_HEAD.get()
    }

    fun getTexture(): String {
        return if(this.mod < 0) {
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjc2MjMwYTBhYzUyYWYxMWU0YmM4NDAwOWM2ODkwYTQwMjk0NzJmMzk0N2I0ZjQ2NWI1YjU3MjI4ODFhYWNjNyJ9fX0="
        }else{
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGJmOGI2Mjc3Y2QzNjI2NjI4M2NiNWE5ZTY5NDM5NTNjNzgzZTZmZjdkNmEyZDU5ZDE1YWQwNjk3ZTkxZDQzYyJ9fX0="
        }
    }


    override fun getButtonItem(player: Player): ItemStack {
        return getDescription(player).let {
            ItemBuilder(SkullBuilder().useBase64(getTexture()).build())
                .amount(1)
                .name(CC.translate(getName(player)))
                .lore(it)
                .build()
        }
    }

}