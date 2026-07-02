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
    private val soundFallbacks = mapOf(
        XSound.UI_BUTTON_CLICK to Sound.UI_BUTTON_CLICK,
        XSound.BLOCK_NOTE_BLOCK_PLING to Sound.BLOCK_NOTE_BLOCK_PLING,
        XSound.BLOCK_GRASS_HIT to Sound.BLOCK_GRASS_HIT,
        XSound.BLOCK_ANVIL_HIT to Sound.BLOCK_ANVIL_HIT,
        XSound.BLOCK_NOTE_BLOCK_BASEDRUM to Sound.BLOCK_NOTE_BLOCK_BASEDRUM,
    )

    fun resolve(xMaterial: XMaterial, amount: Int = 1): ItemStack {
        val parsed = xMaterial.parseItem()
        if (parsed != null) {
            return parsed.clone().apply { this.amount = amount }
        }
        return ItemStack(resolve(xMaterial), amount)
    }

    fun resolveSound(xSound: XSound): Sound {
        xSound.parseSound()?.let { return it }
        soundFallbacks[xSound]?.let { return it }
        return runCatching { Sound.valueOf(xSound.get()!!.name) }.getOrNull() ?: Sound.UI_BUTTON_CLICK
    }

    fun resolve(xMaterial: XMaterial): Material {
        xMaterial.parseMaterial()?.let { return it }
        return runCatching { Material.valueOf(xMaterial.name) }.getOrNull() ?: Material.BARRIER
    }
}
