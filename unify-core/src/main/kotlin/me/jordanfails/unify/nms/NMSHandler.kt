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

    // --- Nametag Visibility (ScoreboardTeam packets) ---
    /**
     * Sends a scoreboard team packet to hide a target player's nametag from the viewer.
     * Uses the "never" nameTagVisibility option.
     */
    fun sendHideNametagPacket(viewer: Player, target: Player)
    
    /**
     * Sends a scoreboard team packet to show a target player's nametag to the viewer.
     * Uses the "always" nameTagVisibility option.
     */
    fun sendShowNametagPacket(viewer: Player, target: Player)
    
    /**
     * Removes the nametag team for a target from a viewer (cleanup).
     */
    fun sendRemoveNametagTeamPacket(viewer: Player, target: Player)
    
    /**
     * Sends a nametag team packet with prefix and suffix for colored nametags.
     * @param viewer The player who will see the nametag
     * @param target The player whose nametag is being modified
     * @param teamName Unique team name for this nametag
     * @param prefix Prefix to display before the player's name (color codes)
     * @param suffix Suffix to display after the player's name
     */
    fun sendNametagPacket(viewer: Player, target: Player, teamName: String, prefix: String, suffix: String)
}