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
        if (parsed != null) {
            return parsed.clone().apply { this.amount = amount }
        }
        return ItemStack(resolve(xMaterial), amount)
    }

    // Sounds are looked up by name, never as Sound.X fields: a field missing on the running server
    // (most modern names on 1.8-1.12) fails to link and would break every class that touches this one.
    fun resolveSound(xSound: XSound): Sound =
        xSound.parseSound()
            ?: soundNamed(xSound.name())
            ?: soundNamed("UI_BUTTON_CLICK")
            ?: soundNamed("CLICK")!! // the 1.8 name

    private fun soundNamed(name: String): Sound? = runCatching { Sound.valueOf(name) }.getOrNull()

    fun resolve(xMaterial: XMaterial): Material {
        xMaterial.parseMaterial()?.let { return it }
        return runCatching { Material.valueOf(xMaterial.name) }.getOrNull() ?: Material.BARRIER
    }
}
