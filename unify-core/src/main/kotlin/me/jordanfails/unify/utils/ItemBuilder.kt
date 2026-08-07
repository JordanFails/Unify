package me.jordanfails.unify.utils

import com.cryptomorin.xseries.XMaterial
import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.nms.NMSHandlerFactory
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import java.util.UUID

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

    /**
     * Adds an [AttributeModifier] for the given [attribute].
     *
     * By default, any existing modifiers for that attribute are removed first
     * (same pattern as remove + add on ItemMeta).
     *
     * @param slot equipment slot this modifier applies to, or null for all slots
     * @param name modifier name used for the Bukkit [AttributeModifier]
     * @param uuid stable id for the modifier; defaults to a name-based UUID from attribute + name
     * @param replace when true, clears existing modifiers for [attribute] before adding
     */
    fun attribute(
        attribute: Attribute,
        amount: Double,
        operation: AttributeModifier.Operation = AttributeModifier.Operation.ADD_NUMBER,
        slot: EquipmentSlot? = EquipmentSlot.HAND,
        name: String = "unify",
        uuid: UUID = UUID.nameUUIDFromBytes("unify:${attribute.name}:$name".toByteArray()),
        replace: Boolean = true,
    ) = attribute(
        attribute,
        AttributeModifier(uuid, name, amount, operation, slot),
        replace,
    )

    /**
     * Adds a pre-built [AttributeModifier]. When [replace] is true, existing
     * modifiers for [attribute] are removed first.
     */
    fun attribute(
        attribute: Attribute,
        modifier: AttributeModifier,
        replace: Boolean = true,
    ) = apply {
        val meta = item.itemMeta ?: Bukkit.getItemFactory().getItemMeta(item.type) ?: return@apply
        if (replace) {
            meta.removeAttributeModifier(attribute)
        }
        meta.addAttributeModifier(attribute, modifier)
        item.itemMeta = meta
    }

    /** Removes all attribute modifiers for [attribute]. */
    fun removeAttribute(attribute: Attribute) = apply {
        val meta = item.itemMeta ?: return@apply
        meta.removeAttributeModifier(attribute)
        item.itemMeta = meta
    }

    /** Removes every attribute modifier from this item. */
    fun clearAttributes() = apply {
        val meta = item.itemMeta ?: return@apply
        meta.attributeModifiers = null
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

    fun toButton(
        clickHandler: (Player, Int, ClickType, InventoryView) -> Unit = { _, _, _, _ -> }
    ): Button = object : Button() {
        override fun getButtonItem(player: Player): ItemStack {
            return item.clone()
        }

        override fun clicked(player: Player, slot: Int, clickType: ClickType, view: InventoryView) {
            clickHandler(player, slot, clickType, view)
        }
    }

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
        @JvmStatic
        fun fromItem(item: ItemStack): Button {
            return object : Button() {
                override fun getButtonItem(player: Player): ItemStack {
                    return item
                }
            }
        }
    }
}