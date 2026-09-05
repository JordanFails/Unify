package me.jordanfails.unify.nms

import me.jordanfails.unify.bossbar.UnifyBossBar
import me.jordanfails.unify.hologram.UnifyHologram
import me.jordanfails.unify.menu.anvil.AnvilHandle
import me.jordanfails.unify.screen.Screen
import me.jordanfails.unify.npc.BukkitNpcBody
import me.jordanfails.unify.npc.NPCEquipmentSlot
import me.jordanfails.unify.npc.NPCPose
import me.jordanfails.unify.npc.NPCSkin
import me.jordanfails.unify.npc.NPCSpawnSpec
import org.bukkit.Location
import org.bukkit.entity.EntityType
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
    fun getServerVersion(): ServerVersion

    fun getPing(player: Player): Int

    fun getTPS(): DoubleArray

    // --- New cross-version item helpers ---
    /** Sets item durability in a version-safe way. */
    fun setItemDurability(item: ItemStack, durability: Int)
    fun setItemData(item: ItemStack, data: Short)
    fun getLegacyColorData(color: LegacyItemColor, type: LegacyColorDataType): Byte =
        when (type) {
            LegacyColorDataType.BLOCK -> color.blockData
            LegacyColorDataType.DYE -> color.dyeData
        }
    fun setItemUnbreakable(item: ItemStack, unbreakable: Boolean)
    fun applySkullOwner(item: ItemStack, ownerUuid: UUID? = null, ownerName: String? = null): Boolean
    fun applySkullTexture(item: ItemStack, base64Texture: String): Boolean

    // --- New Menu Helpers ---
    fun openMenuInventory(player: Player, inventory: Inventory, title: String)
    fun updateMenuTitle(player: Player, title: String)
    fun refreshMenuInventory(player: Player)
    fun isCustomInventory(inventory: Inventory): Boolean

    // --- Anvil input GUI ---
    /**
     * Opens a custom anvil container for text input (no XP cost, always reachable).
     * @param title inventory title (supported on 1.14+; ignored on older versions)
     */
    fun openAnvil(player: Player, title: String): AnvilHandle

    /** Whether custom anvil titles are shown to the client (false on 1.8–1.13). */
    fun supportsAnvilTitle(): Boolean = true

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

    /**
     * Sidebar row styling: [scoreboardEntry] is a stable internal fake name (never shown as the full line);
     * [prefix] + [suffix] are legacy §-coded fragments (already truncated to version limits by caller).
     *
     * @param create true = create team, false = update existing team (required on modern clients)
     */
    fun sendScoreboardSidebarTeamLine(
        player: Player,
        teamName: String,
        scoreboardEntry: String,
        prefix: String,
        suffix: String,
        create: Boolean = true,
    )
    
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

    // --- NPC API ---
    //
    // Entity-generic: an NPC body may be any entity type, not only a player. Every method takes
    // the body's entity UUID, which changes on each respawn — callers hold the NPC, not the body.
    //
    // Methods return false (or null) when the running version cannot do what was asked, rather
    // than throwing. Callers degrade: a pose that does not exist on 1.8 leaves the NPC standing,
    // it does not fail the spawn.

    /**
     * Creates an NPC body and returns its entity UUID, or null if the type could not be spawned.
     *
     * Implementations must add the entity to the world as an ordinary entity — never to the
     * server's player list, even for player bodies. A body in the player list shows up in
     * [org.bukkit.Bukkit.getOnlinePlayers], enters the login pipeline, and forces every consumer
     * of the player list to filter it back out.
     *
     * The caller has already pinned the target chunk, so implementations need not keep the entity
     * alive themselves.
     */
    fun spawnNpcEntity(spec: NPCSpawnSpec): UUID? = BukkitNpcBody.spawn(spec)

    /** Removes the body. No-op for an unknown UUID. */
    fun despawnNpcEntity(entityUuid: UUID) = BukkitNpcBody.despawn(entityUuid)

    /** Moves the body within its current world. Returns false for cross-world moves. */
    fun teleportNpcEntity(entityUuid: UUID, location: Location): Boolean =
        BukkitNpcBody.teleport(entityUuid, location)

    /**
     * Swaps a player body's skin without recreating the entity.
     *
     * The skin lives on an immutable GameProfile, so implementations replace the profile and
     * re-send the entity to its current viewers. Returns false when that is not possible — the
     * caller then rebuilds the NPC, which is correct but more disruptive. Non-player bodies
     * return false.
     */
    fun setNpcSkin(entityUuid: UUID, skin: NPCSkin?): Boolean = false

    /**
     * Sets the body's nameplate. [name] is already colour-translated; null clears it.
     *
     * Player bodies need a scoreboard team rather than a custom name, since vanilla always renders
     * a player's profile name above its head.
     */
    fun setNpcName(entityUuid: UUID, name: String?, visible: Boolean): Boolean =
        BukkitNpcBody.setName(entityUuid, name, visible)

    /** Sets one equipment slot, or clears it when [item] is null. */
    fun setNpcEquipment(entityUuid: UUID, slot: NPCEquipmentSlot, item: ItemStack?): Boolean =
        BukkitNpcBody.setEquipment(entityUuid, slot, item)

    /**
     * Points the body's head and body at [yaw]/[pitch], in degrees.
     *
     * Called at the trait tick rate while look-close is active, so implementations should send
     * rotation packets directly rather than going through a Bukkit teleport.
     */
    fun setNpcLook(entityUuid: UUID, yaw: Float, pitch: Float): Boolean =
        BukkitNpcBody.setLook(entityUuid, yaw, pitch)

    /** Puts the body into [pose]. Only called for poses [supportsNpcPose] accepted. */
    fun setNpcPose(entityUuid: UUID, pose: NPCPose): Boolean = BukkitNpcBody.setPose(entityUuid, pose)

    /** Whether this version can render [pose]. Standing always works. */
    fun supportsNpcPose(pose: NPCPose): Boolean = pose == NPCPose.STANDING || pose == NPCPose.SNEAKING

    /**
     * Whether this version can spawn an NPC body of [type].
     *
     * Defaults to every spawnable non-player type. Modules that implement player bodies override
     * this to add [EntityType.PLAYER].
     */
    fun supportsNpcEntityType(type: EntityType): Boolean = BukkitNpcBody.supports(type)

    // --- Native custom screens (Minecraft dialogs, 1.21.6+ / 26.x) ---

    /**
     * Whether this version can show Minecraft's native custom screens (dialogs).
     * False on everything before 1.21.6.
     */
    fun supportsCustomScreens(): Boolean = false

    /**
     * Show [screen] to [player] as a native custom screen.
     * @return true if the screen was shown
     */
    fun openCustomScreen(player: Player, screen: Screen): Boolean = false

    /** Close the player's currently open native custom screen, if any. */
    fun closeCustomScreen(player: Player) {}
}
