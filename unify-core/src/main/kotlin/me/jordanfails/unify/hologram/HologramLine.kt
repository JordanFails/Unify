package me.jordanfails.unify.hologram

import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * Represents a single line in a hologram.
 * Can be either text or a floating item.
 */
sealed class HologramLine {
    
    /**
     * A text line displayed as an invisible armor stand's custom name.
     */
    data class Text(val text: String) : HologramLine() {
        override fun toString(): String = text
    }
    
    /**
     * A floating item displayed at the hologram location.
     * The item hovers and optionally spins.
     */
    data class Item(val itemStack: ItemStack, val spin: Boolean = true) : HologramLine() {
        constructor(material: Material, spin: Boolean = true) : this(ItemStack(material), spin)
        
        override fun toString(): String = "[ITEM:${itemStack.type.name}]"
    }
    
    companion object {
        /**
         * Parse a string into a HologramLine.
         * Holograms are text-only, so this always returns a text line.
         */
        fun parse(input: String): HologramLine {
            return Text(input.trim())
        }
        
        fun text(text: String): HologramLine = Text(text)
        fun item(material: Material, spin: Boolean = true): HologramLine = Item(material, spin)
        fun item(itemStack: ItemStack, spin: Boolean = true): HologramLine = Item(itemStack, spin)
    }
}
