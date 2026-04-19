package me.jordanfails.unify.utils

import com.cryptomorin.xseries.XMaterial
import me.jordanfails.unify.nms.NMSHandlerFactory
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta

class ItemBuilder(private val item: ItemStack) {

    private val nms = NMSHandlerFactory.getHandler()

    constructor(material: Material) : this(ItemStack(material))
    constructor(material: Material, amount: Int) : this(ItemStack(material, amount))
    constructor(xMaterial: XMaterial) : this(XSupport.resolve(xMaterial))
    constructor(xMaterial: XMaterial, amount: Int) : this(XSupport.resolve(xMaterial, amount))

    fun amount(amount: Int) = apply { item.amount = amount }

    fun durability(value: Int) = apply {
        try {
            nms?.setItemDurability(item, value)
                ?: run { item.durability = value.toShort() }
        } catch (_: Throwable) {
            item.durability = value.toShort()
        }
    }

    fun data(value: Short) = apply {
        try {
            nms?.setItemData(item, value)
                ?: run { item.durability = value }
        } catch (_: Throwable) {
            item.durability = value
        }
    }

    fun enchant(enchantment: Enchantment, level: Int, safe: Boolean = false) = apply {
        if (safe) item.addEnchantment(enchantment, level)
        else item.addUnsafeEnchantment(enchantment, level)
    }

    fun unenchant(enchantment: Enchantment) = apply { item.removeEnchantment(enchantment) }

    fun name(displayName: String?) = apply {
        val meta = item.itemMeta ?: Bukkit.getItemFactory().getItemMeta(item.type)
        meta?.setDisplayName(displayName?.let { ChatColor.translateAlternateColorCodes('&', it) })
        item.itemMeta = meta
    }

    fun lore(vararg entries: String) = lore(entries.toList())

    fun lore(lines: Collection<String>) = apply {
        val meta = item.itemMeta ?: Bukkit.getItemFactory().getItemMeta(item.type)
        meta?.lore = lines.map { ChatColor.translateAlternateColorCodes('&', it) }
        item.itemMeta = meta
    }

    fun addLore(vararg lines: String) = apply {
        val meta = item.itemMeta ?: Bukkit.getItemFactory().getItemMeta(item.type)
        val lore = (meta?.lore ?: mutableListOf()).toMutableList()
        lore += lines.map { ChatColor.translateAlternateColorCodes('&', it) }
        meta?.lore = lore
        item.itemMeta = meta
    }

    fun clearLore() = apply {
        val meta = item.itemMeta
        meta?.lore = null
        item.itemMeta = meta
    }

    fun color(color: Color) = apply {
        val meta = item.itemMeta as? LeatherArmorMeta
            ?: throw UnsupportedOperationException("Cannot apply color to non-leather armor item.")
        meta.setColor(color)
        item.itemMeta = meta
    }

    fun unbreakable(flag: Boolean) = apply {
        nms?.setItemUnbreakable(item, flag)
    }

    fun addFlags(vararg flags: ItemFlag) = apply {
        val meta = item.itemMeta
        meta?.addItemFlags(*flags)
        item.itemMeta = meta
    }

    fun hideAllFlags() = apply {
        val meta = item.itemMeta
        meta?.addItemFlags(*ItemFlag.entries.toTypedArray())
        item.itemMeta = meta
    }

    fun glow(enabled: Boolean = true) = apply {
        val meta = item.itemMeta ?: Bukkit.getItemFactory().getItemMeta(item.type)
        if (enabled) {
            if (!item.enchantments.containsKey(Enchantment.DURABILITY))
                item.addUnsafeEnchantment(Enchantment.DURABILITY, 1)
            meta?.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        } else {
            item.removeEnchantment(Enchantment.DURABILITY)
        }
        item.itemMeta = meta
    }

    fun customData(data: Int) = apply {
        val meta = item.itemMeta ?: Bukkit.getItemFactory().getItemMeta(item.type)
        meta?.setCustomModelData(data)
        item.itemMeta = meta
    }

    fun type(material: Material) = apply { item.type = material }

    fun type(xMaterial: XMaterial) = apply {
        val resolved = XSupport.resolve(xMaterial, 1)
        item.type = resolved.type
        if (resolved.durability != 0.toShort()) data(resolved.data!!.data.toShort())
    }

    fun clone(): ItemBuilder = ItemBuilder(item.clone())

    fun build(): ItemStack = item.clone()

    companion object {
        @JvmStatic fun of(material: Material) = ItemBuilder(material)
        @JvmStatic fun of(material: Material, amount: Int) = ItemBuilder(material, amount)
        @JvmStatic fun of(xMaterial: XMaterial) = ItemBuilder(xMaterial)
        @JvmStatic fun of(xMaterial: XMaterial, amount: Int) = ItemBuilder(xMaterial, amount)
        @JvmStatic fun from(item: ItemStack) = ItemBuilder(item.clone())

        @JvmStatic
        fun fancy(xMaterial: XMaterial, name: String, vararg lore: String): ItemBuilder {
            return of(xMaterial)
                .name(name)
                .lore(*lore)
                .glow()
                .hideAllFlags()
        }
    }
}