package me.jordanfails.unify.nms

import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

/**
 * NMSHandler defines version‑specific methods Unify calls.
 * Each Minecraft version module implements this interface.
 */
interface NMSHandler {
    /** Example: send a title to a player */
    fun sendTitle(player: Player, title: String, subtitle: String?, fadeIn: Int = 10, stay: Int = 70, fadeOut: Int = 20)

    /** Example: get server‑specific version string */
    fun getServerVersion(): String

    fun getPing(player: Player): Int

    fun getTPS(): DoubleArray

    // --- New cross-version item helpers ---
    /** Sets item durability in a version-safe way. */
    fun setItemDurability(item: ItemStack, durability: Int)
    fun setItemData(item: ItemStack, data: Short)
    fun setItemUnbreakable(item: ItemStack, unbreakable: Boolean)

    // --- New Menu Helpers ---
    fun openMenuInventory(player: Player, inventory: Inventory, title: String)
    fun updateMenuTitle(player: Player, title: String)
    fun refreshMenuInventory(player: Player)
    fun isCustomInventory(inventory: Inventory): Boolean
//    fun getTopInventory(player: Player): Inventory?

    /** You can add more (e.g., NBT utilities, packet methods, ActionBars, etc.) */
}