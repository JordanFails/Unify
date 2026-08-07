package me.jordanfails.unify.nms.v26_R1

import de.tr7zw.nbtapi.NBT
import io.papermc.paper.adventure.PaperAdventure
import com.google.common.collect.ArrayListMultimap
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.bossbar.BossBarColor
import me.jordanfails.unify.bossbar.BossBarStyle
import me.jordanfails.unify.bossbar.UnifyBossBar
import me.jordanfails.unify.hologram.HologramLine
import me.jordanfails.unify.hologram.UnifyHologram
import me.jordanfails.unify.menu.anvil.AnvilHandle
import me.jordanfails.unify.nms.NMSHandler
import me.jordanfails.unify.npc.BukkitNpcBody
import me.jordanfails.unify.npc.NPCRegistry
import me.jordanfails.unify.npc.NPCSkin
import me.jordanfails.unify.npc.NPCSpawnSpec
import me.jordanfails.unify.nms.ServerVersion
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.Connection
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundResetScorePacket
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket
import net.minecraft.network.protocol.game.ClientboundSetScorePacket
import net.minecraft.network.protocol.game.ClientboundTabListPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ClientInformation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.CommonListenerCookie
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.AnvilMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.world.scores.Scoreboard
import net.minecraft.world.scores.Team
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import org.bukkit.Bukkit
import io.papermc.paper.event.player.PlayerTrackEntityEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.Location
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.block.BlockState
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.craftbukkit.CraftServer
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.craftbukkit.event.CraftEventFactory
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.inventory.meta.SkullMeta
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale
import java.util.Optional
import java.util.UUID

@Suppress("unused")
class NMSHandler_v26_R1 : NMSHandler {
    private val fakePlayers = mutableMapOf<UUID, ServerPlayer>()
    
    override fun sendTitle(
        player: Player,
        title: String,
        subtitle: String?,
        fadeIn: Int,
        stay: Int,
        fadeOut: Int
    ) {
        player.sendTitle(title, subtitle ?: "", fadeIn, stay, fadeOut)
    }

    override fun getServerVersion(): ServerVersion {
        return ServerVersion.v26_R1
    }

    override fun getPing(player: Player): Int {
        return player.ping
    }

    override fun getTPS(): DoubleArray {
        val serverTPS = Bukkit.getServer().tps

        return serverTPS
    }

    override fun setItemDurability(item: ItemStack, durability: Int) {
        val meta = item.itemMeta
        if (meta is Damageable) {
            meta.damage = durability
            item.itemMeta = meta
        }
    }

    override fun setItemData(item: ItemStack, data: Short) { }

    override fun setItemUnbreakable(item: ItemStack, unbreakable: Boolean) {
        val meta = item.itemMeta ?: return
        meta.isUnbreakable = unbreakable
        item.itemMeta = meta
    }

    override fun applySkullOwner(item: ItemStack, ownerUuid: UUID?, ownerName: String?): Boolean {
        val meta = item.itemMeta as? SkullMeta ?: return false
        return try {
            val owningPlayer = when {
                ownerUuid != null -> Bukkit.getOfflinePlayer(ownerUuid)
                !ownerName.isNullOrBlank() -> Bukkit.getOfflinePlayer(ownerName)
                else -> return false
            }
            meta.setOwningPlayer(owningPlayer)
            item.itemMeta = meta
            true
        } catch (_: Throwable) {
            false
        }
    }

    override fun applySkullTexture(item: ItemStack, base64Texture: String): Boolean {
        return try {
            NBT.modifyComponents(item) { nbt ->
                val profile = nbt.getOrCreateCompound("minecraft:profile")
                profile.setUUID("id", UUID.randomUUID())
                profile.removeKey("name")
                val properties = profile.getCompoundList("properties")
                properties.clear()
                val textures = properties.addCompound()
                textures.setString("name", "textures")
                textures.setString("value", base64Texture)
            }
            true
        } catch (e: Throwable) {
            UnifyCore.instance.logger.warning("applySkullTexture failed: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    private fun findFieldInHierarchy(clazz: Class<*>, name: String): java.lang.reflect.Field? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            try {
                return current.getDeclaredField(name)
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }

    override fun openMenuInventory(player: Player, inventory: Inventory, title: String) {
        player.openInventory(inventory)
    }

    override fun updateMenuTitle(player: Player, title: String) {
        try {
            val top = player.openInventory.topInventory
            val contents = top.contents
            val newInv = player.server.createInventory(null, top.size, title.take(32))
            newInv.contents = contents
            player.openInventory(newInv)
        } catch (_: Throwable) { }
    }

    override fun refreshMenuInventory(player: Player) {
        player.updateInventory()
    }

    override fun isCustomInventory(inventory: Inventory): Boolean {
        val holder = inventory.holder
        return holder == null || holder is Player || holder !is BlockState
    }

    override fun openAnvil(player: Player, title: String): AnvilHandle {
        val serverPlayer = (player as CraftPlayer).handle
        handleInventoryClose(serverPlayer)

        val containerId = serverPlayer.nextContainerCounter()
        val titleComponent = Component.literal(title)
        val container = AnvilContainerHandle(player, containerId, titleComponent)

        serverPlayer.connection.send(
            ClientboundOpenScreenPacket(containerId, MenuType.ANVIL, titleComponent)
        )
        serverPlayer.containerMenu = container
        serverPlayer.initMenu(container)
        return container
    }

    override fun supportsAnvilTitle(): Boolean = true

    private fun handleInventoryClose(serverPlayer: ServerPlayer) {
        fireInventoryCloseEvent(serverPlayer)
        serverPlayer.doCloseContainer()
    }

    companion object {
        fun fireInventoryCloseEvent(serverPlayer: ServerPlayer) {
            try {
                val reasonClass = Class.forName("org.bukkit.event.inventory.InventoryCloseEvent\$Reason")
                val method = CraftEventFactory::class.java.getMethod(
                    "handleInventoryCloseEvent",
                    net.minecraft.world.entity.player.Player::class.java,
                    reasonClass,
                )
                method.invoke(null, serverPlayer, reasonClass.getField("UNKNOWN").get(null))
                return
            } catch (_: ReflectiveOperationException) {
                // fall through
            } catch (_: NoSuchMethodError) {
                // fall through
            }
            try {
                val method = CraftEventFactory::class.java.getMethod(
                    "handleInventoryCloseEvent",
                    net.minecraft.world.entity.player.Player::class.java,
                )
                method.invoke(null, serverPlayer)
            } catch (_: ReflectiveOperationException) {
                // ignore
            }
        }
    }

    private class AnvilContainerHandle(
        private val bukkitPlayer: Player,
        private val windowId: Int,
        guiTitle: Component,
    ) : AnvilMenu(
        windowId,
        (bukkitPlayer as CraftPlayer).handle.inventory,
        ContainerLevelAccess.create(
            (bukkitPlayer.world as CraftWorld).handle,
            BlockPos(0, 0, 0),
        ),
    ), AnvilHandle {

        init {
            this.checkReachable = false
            setTitle(guiTitle)
        }

        override val inventory: Inventory
            get() = bukkitView.topInventory

        override val containerId: Int
            get() = windowId

        override fun createResult() {
            val output = getSlot(2)
            if (!output.hasItem()) {
                output.set(getSlot(0).item.copy())
            }
            cost.set(0)
            sendAllDataToRemote()
            broadcastChanges()
        }

        override fun removed(player: net.minecraft.world.entity.player.Player) {
            // no drop
        }

        override fun clearContainer(player: net.minecraft.world.entity.player.Player, container: net.minecraft.world.Container) {
            // no drop
        }

        override fun getRenameText(): String = itemName ?: ""

        override fun setRenameText(text: String) {
            val inputLeft = getSlot(0)
            if (inputLeft.hasItem()) {
                inputLeft.item.set(DataComponents.CUSTOM_NAME, Component.literal(text))
            }
        }

        override fun close(sendClosePacket: Boolean) {
            val serverPlayer = (bukkitPlayer as CraftPlayer).handle
            if (sendClosePacket) {
                fireInventoryCloseEvent(serverPlayer)
                serverPlayer.doCloseContainer()
                serverPlayer.containerMenu = serverPlayer.inventoryMenu
                serverPlayer.connection.send(ClientboundContainerClosePacket(windowId))
            } else if (serverPlayer.containerMenu === this) {
                serverPlayer.containerMenu = serverPlayer.inventoryMenu
            }
        }

        override fun updateTitle(title: String, preserveRenameText: Boolean) {
            val rename = getRenameText()
            val component = Component.literal(title)
            (bukkitPlayer as CraftPlayer).handle.connection.send(
                ClientboundOpenScreenPacket(windowId, MenuType.ANVIL, component)
            )
            if (preserveRenameText) {
                setRenameText(rename)
            }
        }
    }






    private fun sendEntityPacket(viewer: Player, className: String, entity: ServerPlayer, vararg extras: Any): Boolean {
        return try {
            val packetClass = Class.forName(className)
            val args = arrayOf(entity, *extras)
            val constructor = packetClass.constructors.firstOrNull { candidate ->
                candidate.parameterTypes.size == args.size
            } ?: return false
            val packet = constructor.newInstance(*args) as? Packet<*> ?: return false
            (viewer as CraftPlayer).handle.connection.send(packet)
            true
        } catch (_: Throwable) {
            false
        }
    }










    // ── NPC bodies ──────────────────────────────────────────────────────────
    //
    // Only PLAYER bodies are handled here. Every other entity type goes through [BukkitNpcBody],
    // which behaves identically on every supported version and needs no NMS at all.

    /** Ticks to wait after a join/world change before re-sending NPC bodies to that client. */
    private val JOIN_PROFILE_DELAY_TICKS = 20L

    /**
     * How long a player-info entry lingers before being dropped again.
     *
     * Must outlive the add-entity packet sent alongside it or the client never binds the skin.
     * One tick is not enough; this matches the value the pre-rewrite implementation shipped with.
     */
    private val TAB_HIDE_DELAY_TICKS = 20L

    private val npcPlayers = java.util.concurrent.ConcurrentHashMap<UUID, ServerPlayer>()
    private var npcTrackListenerRegistered = false

    override fun supportsNpcEntityType(type: EntityType): Boolean =
        type == EntityType.PLAYER || BukkitNpcBody.supports(type)

    /**
     * Builds a player body.
     *
     * Added with `addFreshEntity`, not `addNewPlayer`. The body has to be an ordinary world
     * entity: one that reaches the server's player list shows up in `Bukkit.getOnlinePlayers()`,
     * enters the login pipeline, and forces every consumer of the player list to filter it back
     * out — which is what the previous implementation spent most of its code doing.
     *
     * The caller has already pinned the chunk, so nothing here needs to keep the entity alive.
     */
    override fun spawnNpcEntity(spec: NPCSpawnSpec): UUID? {
        if (spec.entityType != EntityType.PLAYER) return BukkitNpcBody.spawn(spec)

        return try {
            val bukkitWorld = spec.location.world ?: return null
            val world = (bukkitWorld as CraftWorld).handle
            val server = (Bukkit.getServer() as CraftServer).server

            // Never reuse a real player's UUID or name:
            //  - same UUID  -> "Force-added player with duplicate UUID" when they join.
            //  - same name  -> vanilla nametags, BELOW_NAME and nametag plugins all key scoreboard
            //    entries by name, so the NPC inherits their display name and any team update for
            //    that player yanks the NPC out of its hidden-nametag team.
            // The skin still comes from them; only the identity is independent.
            val profileUuid = UUID.randomUUID()
            val profileName = "npc" + profileUuid.toString().replace("-", "").take(13)

            // Textures are baked in at construction. On Paper 26 / modern authlib
            // GameProfile.properties is final and often immutable, so mutating it afterwards
            // throws — and used to abort the spawn *after* the old body had been despawned.
            val profile = createGameProfile(
                profileUuid,
                profileName,
                spec.skin?.let { TextureProperty(it.value, it.signature) },
            )

            val npc = ServerPlayer(server, world, profile, ClientInformation.createDefault())
            positionServerPlayer(npc, spec.location)
            attachFakeConnection(server, npc, profile)
            npc.noPhysics = true
            npc.isNoGravity = true
            npc.isInvulnerable = true

            npcPlayers[profileUuid] = npc
            // Registered before the entity joins the level, so the very first tracking pass
            // already carries the profile packet and therefore the skin.
            ensureNpcTrackListener()

            world.addFreshEntity(npc)

            val bukkitEntity = npc.bukkitEntity
            bukkitEntity.isCollidable = false
            bukkitEntity.canPickupItems = false
            bukkitEntity.isSilent = true
            bukkitEntity.isInvulnerable = true
            bukkitEntity.setMetadata(
                NPCRegistry.NPC_METADATA_KEY, FixedMetadataValue(UnifyCore.instance, spec.npcId)
            )
            bukkitEntity.setMetadata(
                NPCRegistry.LEGACY_NPC_METADATA_KEY, FixedMetadataValue(UnifyCore.instance, true)
            )

            // Players already in range are tracked before the listener can fire for them, so they
            // are seeded explicitly. The tracker owns the entity packets from here on.
            Bukkit.getOnlinePlayers().forEach { viewer ->
                if (viewer.world == bukkitWorld) refreshNpcForViewer(viewer, npc)
            }

            profileUuid
        } catch (e: Exception) {
            UnifyCore.instance.logger.warning("Failed to spawn player NPC '${spec.npcId}': ${e.message}")
            e.printStackTrace()
            null
        }
    }

    override fun despawnNpcEntity(entityUuid: UUID) {
        val npc = npcPlayers.remove(entityUuid)
        if (npc == null) {
            BukkitNpcBody.despawn(entityUuid)
            return
        }

        try {
            npc.remove(Entity.RemovalReason.DISCARDED)
            // The tracker stops sending updates but does not retract what clients already have,
            // so both the entity and its player-info entry are removed explicitly.
            Bukkit.getOnlinePlayers().forEach { viewer ->
                val connection = (viewer as CraftPlayer).handle.connection
                connection.send(ClientboundPlayerInfoRemovePacket(listOf(entityUuid)))
                connection.send(ClientboundRemoveEntitiesPacket(npc.id))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun teleportNpcEntity(entityUuid: UUID, location: Location): Boolean {
        val npc = npcPlayers[entityUuid] ?: return BukkitNpcBody.teleport(entityUuid, location)
        if (npc.bukkitEntity.world != location.world) return false

        return try {
            positionServerPlayer(npc, location)
            npc.bukkitEntity.teleport(location)
            // The teleport re-enables both, and a body that falls or suffocates is the single most
            // common way an NPC setup breaks.
            npc.noPhysics = true
            npc.isNoGravity = true
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Skins cannot be swapped in place on this version: the profile's property map is immutable,
     * so the texture can only be set when the profile is built. Returning false tells
     * [me.jordanfails.unify.npc.trait.SkinTrait] to rebuild the NPC, which spawns a fresh body
     * with the new texture baked in.
     */
    override fun setNpcSkin(entityUuid: UUID, skin: NPCSkin?): Boolean = false

    /**
     * Player bodies render their profile name above their head, and that name is the synthetic
     * `npcXXXXXXXXXXXXX` id — hidden by a team on spawn, so there is no nameplate to write to.
     * Label player NPCs with a hologram instead.
     */
    override fun setNpcName(entityUuid: UUID, name: String?, visible: Boolean): Boolean {
        if (npcPlayers.containsKey(entityUuid)) return false
        return BukkitNpcBody.setName(entityUuid, name, visible)
    }

    /**
     * Feeds the NPC's profile to each viewer the moment the server starts tracking it.
     *
     * Without this, only players online at spawn time ever receive the profile packet; anyone who
     * joins later, changes worlds, or walks out of range and back gets the tracker's spawn packet
     * with no profile attached, and renders a default skin.
     */
    private fun ensureNpcTrackListener() {
        if (npcTrackListenerRegistered) return
        npcTrackListenerRegistered = true

        Bukkit.getPluginManager().registerEvents(object : Listener {
            @EventHandler
            fun onTrack(event: PlayerTrackEntityEvent) {
                val npc = npcPlayers[event.entity.uniqueId] ?: return
                refreshNpcForViewer(event.player, npc)
            }

            @EventHandler
            fun onJoin(event: PlayerJoinEvent) {
                resendProfilesLater(event.player)
            }

            @EventHandler
            fun onWorldChange(event: PlayerChangedWorldEvent) {
                resendProfilesLater(event.player)
            }
        }, UnifyCore.instance)
    }

    /**
     * Re-sends every nearby NPC's profile to [player] once their client has settled.
     *
     * A skin applied while nobody was online — the usual case at server startup — has no viewer
     * to send its profile to, so it relies entirely on the tracker firing later. That proved
     * unreliable: NPCs skinned at boot rendered as the default player, while NPCs skinned later
     * (when someone was already online to receive the explicit send) looked correct. Re-sending on
     * join closes that gap, and is cheap since it only touches NPCs in the player's world.
     */
    private fun resendProfilesLater(player: Player) {
        Bukkit.getScheduler().runTaskLater(UnifyCore.instance, Runnable {
            if (!player.isOnline) return@Runnable
            npcPlayers.values.forEach { npc ->
                if (runCatching { npc.bukkitEntity.world == player.world }.getOrDefault(false)) {
                    refreshNpcForViewer(player, npc)
                }
            }
        }, JOIN_PROFILE_DELAY_TICKS)
    }

    /**
     * Re-sends [npc] to [viewer] as an ordered burst: despawn, profile, respawn.
     *
     * A client binds a player entity's skin **when it spawns the entity**, from that entity's
     * player-info entry. Two consequences drive everything here:
     *
     *  - Sending the profile after the entity already exists client-side does nothing. The entity
     *    has to be removed and re-added for a new skin to take.
     *  - The profile entry must still be present when the add-entity packet lands, so it cannot be
     *    dropped a tick later — hence [TAB_HIDE_DELAY_TICKS].
     *
     * Sending both packets ourselves is what makes the order guaranteed. Leaving the spawn packet
     * to the server's entity tracker looked cleaner but raced: whenever the tracker's add-entity
     * arrived before our profile, the client fell back to a UUID-derived default skin and stayed
     * there, which is exactly what NPCs skinned before anyone was online did.
     */
    private fun refreshNpcForViewer(viewer: Player, npc: ServerPlayer) {
        try {
            val connection = (viewer as CraftPlayer).handle.connection

            // Drop the client's current copy first, so the re-add below is what binds the skin.
            connection.send(ClientboundRemoveEntitiesPacket(npc.id))

            createPlayerInfoAddPacket(npc)?.let { connection.send(it) }

            connection.send(
                ClientboundAddEntityPacket(
                    npc.id,
                    npc.uuid,
                    npc.x,
                    npc.y,
                    npc.z,
                    npc.xRot,
                    npc.yRot,
                    npc.type,
                    0,
                    npc.deltaMovement,
                    npc.yHeadRot.toDouble(),
                )
            )

            npc.entityData.packAll()?.let { connection.send(ClientboundSetEntityDataPacket(npc.id, it)) }

            sendHideNpcNametag(connection, npc)

            // Long enough that the add-entity packet above has certainly been processed. The cost
            // is the NPC sitting in this player's tab list for that window.
            Bukkit.getScheduler().runTaskLater(UnifyCore.instance, Runnable {
                runCatching {
                    if (viewer.isOnline) {
                        (viewer as CraftPlayer).handle.connection
                            .send(ClientboundPlayerInfoRemovePacket(listOf(npc.uuid)))
                    }
                }
            }, TAB_HIDE_DELAY_TICKS)
        } catch (_: Exception) {
        }
    }

    private fun sendHideNpcNametag(connection: ServerGamePacketListenerImpl, npc: ServerPlayer) {
        // Stable per-NPC team. The profile name is synthetic, so nametag plugins will not re-add
        // this entry to a visible team under the skin owner's real name.
        val teamName = "unpc_${npc.uuid.toString().replace("-", "").take(12)}"
        val team = PlayerTeam(Scoreboard(), teamName)
        team.nameTagVisibility = Team.Visibility.NEVER
        team.collisionRule = Team.CollisionRule.NEVER
        connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true))
        connection.send(
            ClientboundSetPlayerTeamPacket.createPlayerPacket(
                team,
                npc.scoreboardName,
                ClientboundSetPlayerTeamPacket.Action.ADD,
            ),
        )
    }

    private fun createPlayerInfoAddPacket(npc: ServerPlayer): Packet<*>? {
        return runCatching {
            val packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket")
            val createInitializing = packetClass.methods.firstOrNull {
                it.name == "createPlayerInitializing" && it.parameterTypes.size == 1
            }
            if (createInitializing != null) {
                return@runCatching createInitializing.invoke(null, listOf(npc)) as? Packet<*>
            }

            val actionClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket\$Action")
            val addAction = actionClass.getMethod("valueOf", String::class.java).invoke(null, "ADD_PLAYER") as Enum<*>
            val actions = java.util.EnumSet::class.java.getMethod("of", Enum::class.java).invoke(null, addAction)
            val ctor = packetClass.constructors.firstOrNull { constructor ->
                val types = constructor.parameterTypes
                types.size == 2 && types[0].isAssignableFrom(actions.javaClass)
            } ?: return@runCatching null
            ctor.newInstance(actions, listOf(npc)) as? Packet<*>
        }.getOrNull()
    }

    private fun createGameProfile(uuid: UUID, name: String, texture: TextureProperty?): GameProfile {
        val multimap = ArrayListMultimap.create<String, Property>()
        if (texture != null && texture.value.isNotBlank()) {
            multimap.put(
                "textures",
                if (texture.signature.isNullOrBlank()) {
                    Property("textures", texture.value)
                } else {
                    Property("textures", texture.value, texture.signature)
                },
            )
        }
        return GameProfile(uuid, name, PropertyMap(multimap))
    }

    private data class TextureProperty(
        val value: String,
        val signature: String?,
    )

    private fun getProfileProperties(profile: GameProfile): Any? {
        return runCatching {
            profile.javaClass.getMethod("getProperties").invoke(profile)
        }.getOrNull()
            ?: runCatching {
                profile.javaClass.getMethod("properties").invoke(profile)
            }.getOrNull()
    }


    private fun getProfileTextures(profile: GameProfile): List<TextureProperty> {
        val properties = getProfileProperties(profile) ?: return emptyList()
        val rawTextures = runCatching {
            properties.javaClass.getMethod("get", Any::class.java).invoke(properties, "textures")
        }.getOrNull() as? Iterable<*> ?: return emptyList()

        return rawTextures.mapNotNull { prop ->
            val raw = prop ?: return@mapNotNull null
            val value = readStringMember(raw, "value", "value") ?: readStringMember(raw, "value", "getValue")
                ?: return@mapNotNull null
            val signature = readStringMember(raw, "signature", "signature")
                ?: readStringMember(raw, "signature", "getSignature")
            TextureProperty(value, signature)
        }
    }

    private fun removeTextureProperties(properties: Any) {
        val removed = runCatching {
            properties.javaClass.getMethod("removeAll", Any::class.java).invoke(properties, "textures")
            true
        }.getOrElse { false }

        if (!removed) {
            runCatching {
                val current = properties.javaClass.getMethod("get", Any::class.java).invoke(properties, "textures")
                (current as? MutableCollection<*>)?.clear()
            }
        }
    }

    private fun createTextureProperty(value: String, signature: String?): Any? {
        return runCatching {
            val propertyClass = Class.forName("com.mojang.authlib.properties.Property")
            val constructor = if (signature.isNullOrBlank()) {
                propertyClass.getConstructor(String::class.java, String::class.java)
            } else {
                propertyClass.getConstructor(String::class.java, String::class.java, String::class.java)
            }
            if (signature.isNullOrBlank()) {
                constructor.newInstance("textures", value)
            } else {
                constructor.newInstance("textures", value, signature)
            }
        }.getOrNull()
    }

    private fun readStringMember(target: Any, fieldName: String, getterName: String): String? {
        return runCatching {
            target.javaClass.getMethod(getterName).invoke(target) as? String
        }.getOrNull() ?: runCatching {
            target.javaClass.getField(fieldName).get(target) as? String
        }.getOrNull()
    }

    /**
     * 1.21+ minor builds occasionally rename/move positional methods.
     * Use reflection fallbacks to avoid NoSuchMethodError across patch versions.
     */
    private fun positionServerPlayer(npc: ServerPlayer, location: Location) {
        if (invokePositionMethod(npc, "absMoveTo", location)) return
        if (invokePositionMethod(npc, "absSnapTo", location)) return
        if (invokePositionMethod(npc, "moveTo", location)) return

        npc.setPos(location.x, location.y, location.z)
        npc.yRot = location.yaw
        npc.xRot = location.pitch
        npc.setYHeadRot(location.yaw)
    }

    private fun invokePositionMethod(npc: ServerPlayer, methodName: String, location: Location): Boolean {
        return try {
            val method = npc.javaClass.getMethod(
                methodName,
                java.lang.Double.TYPE,
                java.lang.Double.TYPE,
                java.lang.Double.TYPE,
                java.lang.Float.TYPE,
                java.lang.Float.TYPE
            )
            method.invoke(npc, location.x, location.y, location.z, location.yaw, location.pitch)
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Paper 1.21 waypoint/tracking systems assume ServerPlayer.connection is non-null.
     * NPC server-players do not have a real client, so attach a no-op listener.
     */
    private fun attachFakeConnection(
        server: MinecraftServer,
        npc: ServerPlayer,
        profile: GameProfile,
    ) {
        if (npc.connection != null) return
        val networkConnection = Connection(PacketFlow.SERVERBOUND)
        val cookie = CommonListenerCookie.createInitial(profile, false)
        val fakeListener = object : ServerGamePacketListenerImpl(server, networkConnection, npc, cookie) {
            override fun send(packet: Packet<*>) {}
            override fun tick() {}
            override fun isAcceptingMessages() = true
        }
        npc.connection = fakeListener
    }

    private fun getTeamName(target: Player): String {
        return "nt_${target.name.take(12)}"
    }

    override fun sendHideNametagPacket(viewer: Player, target: Player) {
        sendTeamPacket(viewer, target, getTeamName(target), "", "", Team.Visibility.NEVER)
    }

    override fun sendShowNametagPacket(viewer: Player, target: Player) {
        sendTeamPacket(viewer, target, getTeamName(target), "", "", Team.Visibility.ALWAYS)
    }

    override fun sendRemoveNametagTeamPacket(viewer: Player, target: Player) {
        sendTeamRemovePacket(viewer, getTeamName(target))
    }
    
    override fun sendNametagPacket(viewer: Player, target: Player, teamName: String, prefix: String, suffix: String) {
        sendTeamPacket(viewer, target, teamName.take(16), prefix, suffix, Team.Visibility.ALWAYS)
    }
    
    private fun sendTeamPacket(viewer: Player, target: Player, teamName: String, prefix: String, suffix: String, visibility: Team.Visibility) {
        try {
            val scoreboard = Scoreboard()
            val team = PlayerTeam(scoreboard, teamName)
            
            team.displayName = Component.literal(teamName)
            team.setPlayerPrefix(Component.literal(prefix))
            team.setPlayerSuffix(Component.literal(suffix))
            setTeamColor(team, getChatFormatting(extractColorCode(prefix)))
            team.setNameTagVisibility(visibility)
            team.setCollisionRule(Team.CollisionRule.NEVER)
            team.players.add(target.name)
            
            val packet = ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true)
            (viewer as CraftPlayer).handle.connection.send(packet)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun sendTeamRemovePacket(viewer: Player, teamName: String) {
        try {
            val scoreboard = Scoreboard()
            val team = PlayerTeam(scoreboard, teamName)
            
            val packet = ClientboundSetPlayerTeamPacket.createRemovePacket(team)
            (viewer as CraftPlayer).handle.connection.send(packet)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun extractColorCode(text: String): Char {
        val colorChars = "0123456789abcdefABCDEF"
        var i = 0
        while (i < text.length - 1) {
            val marker = text[i]
            if (marker == '\u00A7' || marker == '&') {
                val code = text[i + 1]
                if (code.equals('x', ignoreCase = true) && i + 13 < text.length) {
                    i += 14
                    continue
                }
                if (colorChars.contains(code)) {
                    return code.lowercaseChar()
                }
            }
            i++
        }
        return 'f'
    }
    
    private fun getChatFormatting(colorCode: Char): ChatFormatting {
        return when (colorCode) {
            '0' -> ChatFormatting.BLACK
            '1' -> ChatFormatting.DARK_BLUE
            '2' -> ChatFormatting.DARK_GREEN
            '3' -> ChatFormatting.DARK_AQUA
            '4' -> ChatFormatting.DARK_RED
            '5' -> ChatFormatting.DARK_PURPLE
            '6' -> ChatFormatting.GOLD
            '7' -> ChatFormatting.GRAY
            '8' -> ChatFormatting.DARK_GRAY
            '9' -> ChatFormatting.BLUE
            'a' -> ChatFormatting.GREEN
            'b' -> ChatFormatting.AQUA
            'c' -> ChatFormatting.RED
            'd' -> ChatFormatting.LIGHT_PURPLE
            'e' -> ChatFormatting.YELLOW
            'f' -> ChatFormatting.WHITE
            else -> ChatFormatting.WHITE
        }
    }

    private fun setTeamColor(team: PlayerTeam, color: ChatFormatting) {
        runCatching {
            PlayerTeam::class.java
                .getMethod("setColor", ChatFormatting::class.java)
                .invoke(team, color)
        }
    }
    
    // 1.21+ uses components - effectively unlimited (32767 is protocol max)
    override fun getScoreboardLineLimit(): Int = 32767
    override fun getTeamPrefixLimit(): Int = 32767
    
    // --- BossBar Implementation (uses Bukkit API for 1.9+) ---
    private val playerBossBars = mutableMapOf<UUID, MutableMap<UUID, BossBar>>()
    
    override fun showBossBar(player: Player, bossBar: UnifyBossBar) {
        val bukkitBar = createBukkitBossBar(bossBar)
        bukkitBar.addPlayer(player)
        playerBossBars.getOrPut(player.uniqueId) { mutableMapOf() }[bossBar.uuid] = bukkitBar
    }
    
    override fun hideBossBar(player: Player, bossBar: UnifyBossBar) {
        val bars = playerBossBars[player.uniqueId] ?: return
        val bukkitBar = bars.remove(bossBar.uuid) ?: return
        bukkitBar.removePlayer(player)
    }
    
    override fun updateBossBar(player: Player, bossBar: UnifyBossBar) {
        val bars = playerBossBars[player.uniqueId] ?: return
        val bukkitBar = bars[bossBar.uuid] ?: return
        bukkitBar.setTitle(bossBar.title)
        bukkitBar.progress = bossBar.progress
        bukkitBar.color = toBukkitColor(bossBar.color)
        bukkitBar.style = toBukkitStyle(bossBar.style)
    }
    
    private fun createBukkitBossBar(bossBar: UnifyBossBar): BossBar {
        return Bukkit.createBossBar(bossBar.title, toBukkitColor(bossBar.color), toBukkitStyle(bossBar.style)).apply {
            progress = bossBar.progress
        }
    }
    
    private fun toBukkitColor(color: BossBarColor): BarColor {
        return when (color) {
            BossBarColor.PINK -> BarColor.PINK
            BossBarColor.BLUE -> BarColor.BLUE
            BossBarColor.RED -> BarColor.RED
            BossBarColor.GREEN -> BarColor.GREEN
            BossBarColor.YELLOW -> BarColor.YELLOW
            BossBarColor.PURPLE -> BarColor.PURPLE
            BossBarColor.WHITE -> BarColor.WHITE
        }
    }
    
    private fun toBukkitStyle(style: BossBarStyle): BarStyle {
        return when (style) {
            BossBarStyle.SOLID -> BarStyle.SOLID
            BossBarStyle.SEGMENTED_6 -> BarStyle.SEGMENTED_6
            BossBarStyle.SEGMENTED_10 -> BarStyle.SEGMENTED_10
            BossBarStyle.SEGMENTED_12 -> BarStyle.SEGMENTED_12
            BossBarStyle.SEGMENTED_20 -> BarStyle.SEGMENTED_20
        }
    }
    
    // --- Hologram Implementation (1.21 uses NMS ArmorStands) ---
    private val playerHologramEntities = mutableMapOf<UUID, MutableMap<UUID, List<Int>>>()
    private var entityIdCounter = 1000000
    
    override fun showHologram(player: Player, hologram: UnifyHologram) {
        spawnHologram(player, hologram)
    }
    
    override fun hideHologram(player: Player, hologram: UnifyHologram) {
        val entityIds = playerHologramEntities[player.uniqueId]?.remove(hologram.uuid) ?: return
        if (entityIds.isNotEmpty()) {
            val removePacket = ClientboundRemoveEntitiesPacket(*entityIds.toIntArray())
            (player as CraftPlayer).handle.connection.send(removePacket)
        }
    }
    
    override fun updateHologram(player: Player, hologram: UnifyHologram) {
        val currentIds = playerHologramEntities[player.uniqueId]?.get(hologram.uuid)
        if (currentIds != null && currentIds.size == hologram.lines.size) {
            updateHologramLines(player, hologram, currentIds)
        } else {
            hideHologram(player, hologram)
            spawnHologram(player, hologram)
        }
    }
    
    private fun spawnHologram(player: Player, hologram: UnifyHologram) {
        try {
            val lines = hologram.lines
            val entityIds = mutableListOf<Int>()
            var currentY = hologram.location.y
            
            val world = (player.world as CraftWorld).handle
            val connection = (player as CraftPlayer).handle.connection
            
            for (line in lines) {
                val entityId = entityIdCounter++
                entityIds.add(entityId)
                
                when (line) {
                    is HologramLine.Text -> {
                        val armorStand = ArmorStand(world, hologram.location.x, currentY, hologram.location.z)
                        armorStand.id = entityId
                        armorStand.customName = parseText(line.text)
                        armorStand.isCustomNameVisible = true
                        armorStand.isInvisible = true
                        armorStand.isNoGravity = true
                        armorStand.isSmall = true
                        armorStand.isMarker = true
                        
                        val addPacket = ClientboundAddEntityPacket(
                            entityId,
                            armorStand.uuid,
                            armorStand.x,
                            armorStand.y,
                            armorStand.z,
                            armorStand.xRot,
                            armorStand.yRot,
                            armorStand.type,
                            0,
                            armorStand.deltaMovement,
                            armorStand.yHeadRot.toDouble()
                        )
                        connection.send(addPacket)
                        
                        val dataValues = armorStand.entityData.packAll()
                        if (dataValues != null) {
                            val metaPacket = ClientboundSetEntityDataPacket(entityId, dataValues)
                            connection.send(metaPacket)
                        }
                        currentY -= 0.25
                    }
                    is HologramLine.Item -> {
                        val nmsItem = CraftItemStack.asNMSCopy(line.itemStack)
                        val itemEntity = ItemEntity(world, hologram.location.x, currentY, hologram.location.z, nmsItem)
                        itemEntity.id = entityId
                        itemEntity.isNoGravity = true
                        itemEntity.setNeverPickUp()
                        
                        val addPacket = ClientboundAddEntityPacket(
                            entityId,
                            itemEntity.uuid,
                            itemEntity.x,
                            itemEntity.y,
                            itemEntity.z,
                            itemEntity.xRot,
                            itemEntity.yRot,
                            itemEntity.type,
                            0,
                            itemEntity.deltaMovement,
                            itemEntity.yHeadRot.toDouble()
                        )
                        connection.send(addPacket)
                        
                        val dataValues = itemEntity.entityData.packAll()
                        if (dataValues != null) {
                            val metaPacket = ClientboundSetEntityDataPacket(entityId, dataValues)
                            connection.send(metaPacket)
                        }
                        currentY -= 0.5
                    }
                }
            }
            playerHologramEntities.getOrPut(player.uniqueId) { mutableMapOf() }[hologram.uuid] = entityIds
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun updateHologramLines(player: Player, hologram: UnifyHologram, entityIds: List<Int>) {
        try {
            val lines = hologram.lines
            var currentY = hologram.location.y
            val world = (player.world as CraftWorld).handle
            val connection = (player as CraftPlayer).handle.connection
            
            for (i in lines.indices) {
                val entityId = entityIds[i]
                // 1.21+ changed teleport packet internals between minor releases.
                // Re-spawn each existing line entity with the same id to avoid brittle teleport constructors.
                connection.send(ClientboundRemoveEntitiesPacket(entityId))
                when (val line = lines[i]) {
                    is HologramLine.Text -> {
                        val armorStand = ArmorStand(world, hologram.location.x, currentY, hologram.location.z)
                        armorStand.id = entityId
                        armorStand.customName = parseText(line.text)
                        armorStand.isCustomNameVisible = true
                        armorStand.isInvisible = true
                        armorStand.isMarker = true
                        
                        val addPacket = ClientboundAddEntityPacket(
                            entityId,
                            armorStand.uuid,
                            armorStand.x,
                            armorStand.y,
                            armorStand.z,
                            armorStand.xRot,
                            armorStand.yRot,
                            armorStand.type,
                            0,
                            armorStand.deltaMovement,
                            armorStand.yHeadRot.toDouble()
                        )
                        connection.send(addPacket)

                        val dataValues = armorStand.entityData.packAll()
                        if (dataValues != null) {
                            val metaPacket = ClientboundSetEntityDataPacket(entityId, dataValues)
                            connection.send(metaPacket)
                        }
                        currentY -= 0.25
                    }
                    is HologramLine.Item -> {
                        val nmsItem = CraftItemStack.asNMSCopy(line.itemStack)
                        val itemEntity = ItemEntity(world, hologram.location.x, currentY, hologram.location.z, nmsItem)
                        itemEntity.id = entityId
                        itemEntity.isNoGravity = true
                        itemEntity.setNeverPickUp()

                        val addPacket = ClientboundAddEntityPacket(
                            entityId,
                            itemEntity.uuid,
                            itemEntity.x,
                            itemEntity.y,
                            itemEntity.z,
                            itemEntity.xRot,
                            itemEntity.yRot,
                            itemEntity.type,
                            0,
                            itemEntity.deltaMovement,
                            itemEntity.yHeadRot.toDouble()
                        )
                        connection.send(addPacket)
                        
                        val dataValues = itemEntity.entityData.packAll()
                        if (dataValues != null) {
                            val metaPacket = ClientboundSetEntityDataPacket(entityId, dataValues)
                            connection.send(metaPacket)
                        }
                        currentY -= 0.5
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            hideHologram(player, hologram)
            spawnHologram(player, hologram)
        }
    }

    override fun sendScoreboardObjective(player: Player, name: String, title: String, mode: Int) {
        try {
            val connection = (player as CraftPlayer).handle.connection

            val nmsComponent = parseText(title)
            
            val packet = when (mode) {
                0 -> ClientboundSetObjectivePacket(
                    createObjective(name, nmsComponent),
                    0 // CREATE
                )
                1 -> ClientboundSetObjectivePacket(
                    createObjective(name, nmsComponent),
                    1 // REMOVE
                )
                2 -> ClientboundSetObjectivePacket(
                    createObjective(name, nmsComponent),
                    2 // UPDATE
                )
                else -> return
            }
            
            connection.send(packet)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun sendScoreboardDisplaySlot(player: Player, objectiveName: String, slot: Int) {
        try {
            val connection = (player as CraftPlayer).handle.connection
            val displaySlot = when (slot) {
                0 -> DisplaySlot.LIST
                1 -> DisplaySlot.SIDEBAR
                2 -> DisplaySlot.BELOW_NAME
                else -> return
            }
            
            // Create a dummy objective just to generate the packet structure
            // The actual objective is already registered from sendScoreboardObjective
            val dummyObjective = createObjective(objectiveName, Component.empty())
            val packet = ClientboundSetDisplayObjectivePacket(displaySlot, dummyObjective)
            
            connection.send(packet)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun sendScoreboardScore(player: Player, objectiveName: String, entry: String, score: Int, mode: Int) {
        try {
            val connection = (player as CraftPlayer).handle.connection

            val packet = when (mode) {
                0 -> ClientboundSetScorePacket(
                    entry,
                    objectiveName,
                    score,
                    Optional.empty(),
                    Optional.empty()
                )
                1 -> ClientboundResetScorePacket(
                    entry,
                    objectiveName
                )
                else -> return
            }

            connection.send(packet)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun sendScoreboardSidebarTeamLine(
        player: Player,
        teamName: String,
        scoreboardEntry: String,
        prefix: String,
        suffix: String,
        create: Boolean,
    ) {
        try {
            val scoreboard = Scoreboard()
            val team = PlayerTeam(scoreboard, teamName)
            team.displayName = Component.literal(teamName)
            // parseText handles §x hex gradients; Component.literal() would treat hex digits as legacy colors
            team.setPlayerPrefix(parseText(prefix))
            team.setPlayerSuffix(parseText(suffix))
            setTeamColor(team, getChatFormatting(extractColorCode(prefix)))
            team.nameTagVisibility = Team.Visibility.ALWAYS
            team.collisionRule = Team.CollisionRule.NEVER
            team.players.add(scoreboardEntry)
            // true = CREATE, false = UPDATE (modern clients reject duplicate CREATE)
            val packet = ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, create)
            (player as CraftPlayer).handle.connection.send(packet)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createObjective(name: String, displayName: Component): Objective {
        val dummyScoreboard = Scoreboard()
        return dummyScoreboard.addObjective(
            name,
            ObjectiveCriteria.DUMMY,
            displayName,
            ObjectiveCriteria.RenderType.INTEGER,
            true,
            null
        )
    }
    
    private fun parseText(text: String): Component {
        if (text.contains('<') && text.contains('>')) {
            try {
                val converted = convertLegacyToMiniMessage(text.replace('§', '&'))
                return PaperAdventure.asVanilla(MiniMessage.miniMessage().deserialize(converted))
            } catch (_: Exception) {
            }
        }
        val adventure = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build()
            .deserialize(text.replace('&', '§'))
        return PaperAdventure.asVanilla(adventure)
    }

    private fun convertLegacyToMiniMessage(text: String): String {
        val colorMap = mapOf(
            '0' to "black", '1' to "dark_blue", '2' to "dark_green", '3' to "dark_aqua",
            '4' to "dark_red", '5' to "dark_purple", '6' to "gold", '7' to "gray",
            '8' to "dark_gray", '9' to "blue", 'a' to "green", 'b' to "aqua",
            'c' to "red", 'd' to "light_purple", 'e' to "yellow", 'f' to "white"
        )
        val formatMap = mapOf(
            'k' to "obfuscated", 'l' to "bold", 'm' to "strikethrough",
            'n' to "underlined", 'o' to "italic", 'r' to "reset"
        )
        val result = StringBuilder()
        var i = 0
        while (i < text.length) {
            if (i + 1 < text.length && text[i] == '&') {
                val code = text[i + 1].lowercaseChar()
                if (code == 'x' && i + 13 < text.length) {
                    val hex = StringBuilder(6)
                    var valid = true
                    for (j in 0 until 6) {
                        val ampIndex = i + 2 + (j * 2)
                        val hexIndex = ampIndex + 1
                        if (text[ampIndex] != '&' || !text[hexIndex].isDigit() && text[hexIndex].lowercaseChar() !in 'a'..'f') {
                            valid = false
                            break
                        }
                        hex.append(text[hexIndex])
                    }
                    if (valid) {
                        result.append("<#").append(hex).append(">")
                        i += 14
                        continue
                    }
                }
                val color = colorMap[code]
                val format = formatMap[code]
                when {
                    color != null -> { result.append("<$color>"); i += 2 }
                    format != null -> { result.append("<$format>"); i += 2 }
                    else -> { result.append(text[i]); i++ }
                }
            } else {
                result.append(text[i])
                i++
            }
        }
        return result.toString()
    }
    
    override fun sendTabHeaderFooter(player: Player, header: String, footer: String) {
        try {
            val connection = (player as CraftPlayer).handle.connection
            val headerComponent = parseText(header)
            val footerComponent = parseText(footer)
            val packet = ClientboundTabListPacket(headerComponent, footerComponent)
            connection.send(packet)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
