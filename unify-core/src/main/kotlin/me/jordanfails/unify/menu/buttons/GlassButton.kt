package me.jordanfails.unify.menu.buttons

import com.cryptomorin.xseries.XMaterial
import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.nms.LegacyColorDataType
import me.jordanfails.unify.nms.LegacyItemColor
import me.jordanfails.unify.nms.NMSHandlerFactory
import me.jordanfails.unify.utils.XSupport
import org.bukkit.Material
import org.bukkit.entity.Player

class GlassButton(
    val material: Material = XSupport.resolve(XMaterial.GRAY_STAINED_GLASS_PANE),
    private val legacyColor: LegacyItemColor = LegacyItemColor.GRAY
) : Button() {

    override fun getName(player: Player): String {
        return " "
    }

    override fun getDescription(player: Player): MutableList<String> {
        return mutableListOf()
    }

    override fun getMaterial(player: Player): Material {
        return material
    }

    override fun getDamageValue(player: Player): Byte {
        return NMSHandlerFactory.getHandler()?.getLegacyColorData(legacyColor, LegacyColorDataType.BLOCK)
            ?: legacyColor.blockData
    }

}
