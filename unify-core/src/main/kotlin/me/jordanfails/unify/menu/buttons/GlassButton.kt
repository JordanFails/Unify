package me.jordanfails.unify.menu.buttons

import com.cryptomorin.xseries.XMaterial
import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.utils.XSupport
import org.bukkit.Material
import org.bukkit.entity.Player

class GlassButton(val material: Material = XSupport.resolve(XMaterial.GRAY_STAINED_GLASS_PANE)) : Button() {

    override fun getName(player: Player): String {
        return " "
    }

    override fun getDescription(player: Player): MutableList<String> {
        return mutableListOf()
    }

    override fun getMaterial(player: Player): Material {
        return material
    }

}