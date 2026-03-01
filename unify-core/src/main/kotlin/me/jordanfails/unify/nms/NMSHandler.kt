package me.jordanfails.unify.nms

import me.jordanfails.unify.bossbar.UnifyBossBar
import me.jordanfails.unify.hologram.UnifyHologram
import me.jordanfails.unify.npc.UnifyNPC
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.UUID

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
    
    // --- Scoreboard Limits ---
    /**
     * Returns the maximum character limit for scoreboard lines.
     * - 1.8-1.12: 32 characters (16 prefix + 16 suffix)
     * - 1.13+: 32767 characters (protocol max, components)
     */
    fun getScoreboardLineLimit(): Int
    
    /**
     * Returns the maximum character limit for team prefix/suffix.
     * - 1.8-1.12: 16 characters
     * - 1.13+: 32767 characters (protocol max, components)
     */
    fun getTeamPrefixLimit(): Int
    
    // --- Scoreboard Packet API ---
    /**
     * Sends a scoreboard objective packet to a player.
     * @param mode 0 = CREATE, 1 = REMOVE, 2 = UPDATE
     */
    fun sendScoreboardObjective(player: Player, name: String, title: String, mode: Int)
    
    /**
     * Sends a display slot packet to show an objective in a specific slot.
     * @param slot 0 = List, 1 = Sidebar, 2 = BelowName
     */
    fun sendScoreboardDisplaySlot(player: Player, objectiveName: String, slot: Int)
    
    /**
     * Sends a score packet to set a score for an entry.
     * @param mode 0 = CHANGE, 1 = REMOVE
     */
    fun sendScoreboardScore(player: Player, objectiveName: String, entry: String, score: Int, mode: Int)
    
    // --- BossBar API ---
    /**
     * Show a boss bar to a player.
     */
    fun showBossBar(player: Player, bossBar: UnifyBossBar)
    
    /**
     * Hide a boss bar from a player.
     */
    fun hideBossBar(player: Player, bossBar: UnifyBossBar)
    
    /**
     * Update a boss bar for a player (title, progress, color, style).
     */
    fun updateBossBar(player: Player, bossBar: UnifyBossBar)
    
    // --- Hologram API ---
    /**
     * Show a hologram to a player.
     */
    fun showHologram(player: Player, hologram: UnifyHologram)
    
    /**
     * Hide a hologram from a player.
     */
    fun hideHologram(player: Player, hologram: UnifyHologram)
    
    /**
     * Update a hologram for a player (text, lines, location).
     */
    fun updateHologram(player: Player, hologram: UnifyHologram)
    
    /**
     * Sends a tab list header and footer to a player.
     * Text supports MiniMessage on modern servers, legacy color codes on all.
     */
    fun sendTabHeaderFooter(player: Player, header: String, footer: String)

    // --- Player NPC API (optional, version-specific) ---
    fun spawnPlayerNpc(id: String, location: Location, skinType: UnifyNPC.SkinType?, skinValue: String?): UUID? = null
    fun despawnPlayerNpc(uuid: UUID) { }
    fun teleportPlayerNpc(uuid: UUID, location: Location): Boolean = false
    fun hidePlayerNpcFromTab(viewer: Player, npcUuid: UUID) { }
    fun showPlayerNpcToViewer(viewer: Player, npcUuid: UUID) { }
}
