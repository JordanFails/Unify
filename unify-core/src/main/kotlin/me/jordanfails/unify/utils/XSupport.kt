package me.jordanfails.unify.utils

import com.cryptomorin.xseries.XMaterial
import com.cryptomorin.xseries.XSound
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.inventory.ItemStack

/**
 * Utility wrapper for safely resolving XSeries stuff.
 * Works for modern and legacy versions.
 */
object XSupport {
    fun resolve(xMaterial: XMaterial, amount: Int = 1): ItemStack {
        val parsed = xMaterial.parseItem()
        return parsed?.clone()?.apply { this.amount = amount }
            ?: ItemStack(Material.LIGHT_GRAY_STAINED_GLASS)
    }

    fun resolveSound(xSound: XSound): Sound {
        val parsed = xSound.parseSound()
        return parsed ?: XSound.BLOCK_ANVIL_HIT.parseSound()!!
    }

    fun resolve(xMaterial: XMaterial): Material {
        val parsed = xMaterial.parseMaterial()
        return parsed ?: Material.BEDROCK
    }
}