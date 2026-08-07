package me.jordanfails.unify.nms.v1_21_R1

import de.tr7zw.nbtapi.NBT
import io.papermc.paper.adventure.PaperAdventure
import com.mojang.authlib.GameProfile
import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.bossbar.BossBarColor
import me.jordanfails.unify.bossbar.BossBarStyle
import me.jordanfails.unify.bossbar.UnifyBossBar
import me.jordanfails.unify.hologram.HologramLine
import me.jordanfails.unify.hologram.UnifyHologram
import me.jordanfails.unify.menu.anvil.AnvilHandle
import me.jordanfails.unify.nms.NMSHandler
import me.jordanfails.unify.nms.ServerVersion
import me.jordanfails.unify.npc.BukkitNpcBody
import me.jordanfails.unify.npc.NPCRegistry
import me.jordanfails.unify.npc.NPCSkin
import me.jordanfails.unify.npc.NPCSpawnSpec
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
import org.bukkit.Location
import org.bukkit.entity.EntityType
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
import io.papermc.paper.event.player.PlayerTrackEntityEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
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
class NMSHandler_v1_21_R1 : NMSHandler {
    private val npcPlayers = mutableMapOf<UUID, ServerPlayer>()

    private var npcTrackListenerRegistered = false
    
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
        return ServerVersion.v1_21_R1
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
            } catch (_: NoSuchMethodError) {
            }
            try {
                val method = CraftEventFactory::class.java.getMethod(
                    "handleInventoryCloseEvent",
                    net.minecraft.world.entity.player.Player::class.java,
                )
                method.invoke(null, serverPlayer)
            } catch (_: ReflectiveOperationException) {
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
        }

        override fun clearContainer(player: net.minecraft.world.entity.player.Player, container: net.minecraft.world.Container) {
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

    // ── NPC bodies ──────────────────────────────────────────────────────────
    //
    // Only PLAYER bodies are handled here. Every other entity type goes through [BukkitNpcBody],
    // which behaves identically on every supported version and needs no NMS at all.

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

            // The body must never share an identity with the account supplying its skin.
            // Scoreboard teams address entries by profile name, so reusing a real player's name
            // lets ordinary nametag updates pull the NPC out of its hidden-name team; reusing
            // their UUID collides with them in entity and player-info tracking.
            val profileUuid = UUID.randomUUID()
            val profileName = "npc${profileUuid.toString().replace("-", "").take(13)}"
            val profile = GameProfile(profileUuid, profileName)
            spec.skin?.let { setProfileTexture(profile, it.value, it.signature) }

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

            // No manual spawn burst: the entity is in the level, so the server's own tracker sends
            // the add-entity packet to every viewer in range, now and whenever one returns.
            //
            // The profile is a different matter — players already tracking this location may not
            // produce a fresh track event for the new body, so they are seeded explicitly. This is
            // what makes a skin change land immediately for people already standing there.
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
     * Player bodies render the profile name above their head, and that profile name is the
     * synthetic `npcXXXXXXXXXXXXX` id — so it is hidden by a team on spawn and there is no
     * nameplate to write to. Returning false tells [me.jordanfails.unify.npc.trait.NameTrait]
     * this is not available; label player NPCs with a hologram instead.
     */
    override fun setNpcName(entityUuid: UUID, name: String?, visible: Boolean): Boolean {
        if (npcPlayers.containsKey(entityUuid)) return false
        return BukkitNpcBody.setName(entityUuid, name, visible)
    }

    override fun setNpcEquipment(
        entityUuid: UUID,
        slot: me.jordanfails.unify.npc.NPCEquipmentSlot,
        item: org.bukkit.inventory.ItemStack?,
    ): Boolean {
        // Player bodies are LivingEntity on the Bukkit side too, so the shared path works for
        // both; it just needs the entity to exist, which it does.
        return BukkitNpcBody.setEquipment(entityUuid, slot, item)
    }

    /** Drops the NPC's tab-list entry after [delayTicks], once the client has bound its skin. */
    private fun scheduleHidePlayerNpcFromTab(viewer: Player, npcUuid: UUID, delayTicks: Long) {
        Bukkit.getScheduler().runTaskLater(UnifyCore.instance, Runnable {
            runCatching {
                (viewer as CraftPlayer).handle.connection
                    .send(ClientboundPlayerInfoRemovePacket(listOf(npcUuid)))
            }
        }, delayTicks)
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

    /**
     * How long a player-info entry lingers before being dropped again.
     *
     * Must outlive the add-entity packet sent alongside it or the client never binds the skin.
     */
    private val TAB_HIDE_DELAY_TICKS = 20L

    /**
     * Feeds the NPC's profile to each viewer the moment the server starts tracking it.
     *
     * Without this, only players online at spawn time ever received the profile packet;
     * anyone who joined later, changed worlds, or simply walked out of range and back got
     * the tracker's spawn packet with no profile attached, and rendered a default skin.
     */
    private fun ensureNpcTrackListener() {
        if (npcTrackListenerRegistered) return
        npcTrackListenerRegistered = true

        val plugin = UnifyCore.instance
        Bukkit.getPluginManager().registerEvents(object : Listener {
            @EventHandler
            fun onTrack(event: PlayerTrackEntityEvent) {
                val npc = npcPlayers[event.entity.uniqueId] ?: return
                refreshNpcForViewer(event.player, npc)
            }

            @EventHandler
            fun onJoin(event: org.bukkit.event.player.PlayerJoinEvent) {
                resendProfilesLater(event.player)
            }

            @EventHandler
            fun onWorldChange(event: org.bukkit.event.player.PlayerChangedWorldEvent) {
                resendProfilesLater(event.player)
            }
        }, plugin)
    }

    /**
     * Re-sends every nearby NPC's profile to [player] once their client has settled.
     *
     * A skin applied while nobody was online — the usual case at server startup — has no viewer to
     * send its profile to, so it relies entirely on the tracker firing later. That proved
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

    /** Ticks to wait after a join/world change before re-sending NPC profiles to that client. */
    private val JOIN_PROFILE_DELAY_TICKS = 20L

    private fun sendHideNpcNametag(connection: net.minecraft.server.network.ServerGamePacketListenerImpl, npc: ServerPlayer) {
        val teamName = "npc_nt_${npc.uuid.toString().take(8)}"
        val dummyScoreboard = Scoreboard()
        val team = PlayerTeam(dummyScoreboard, teamName)
        team.nameTagVisibility = Team.Visibility.NEVER
        connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true))
        connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(team, npc.scoreboardName, ClientboundSetPlayerTeamPacket.Action.ADD))
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

    private fun setProfileTexture(profile: GameProfile, value: String, signature: String?) {
        val properties = getProfileProperties(profile) ?: return
        removeTextureProperties(properties)
        val property = createTextureProperty(value, signature) ?: return
        runCatching {
            properties.javaClass.getMethod("put", Any::class.java, Any::class.java)
                .invoke(properties, "textures", property)
        }
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
            team.playerPrefix = Component.literal(prefix)
            team.playerSuffix = Component.literal(suffix)
            team.color = getChatFormatting(extractColorCode(prefix))
            team.nameTagVisibility = visibility
            team.collisionRule = Team.CollisionRule.NEVER
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

    /**
     * What a given viewer currently has spawned for a hologram.
     *
     * [anchor] is the location the entities were spawned at. As long as it and the line
     * shape are unchanged, updates are sent as metadata only — respawning the entities
     * makes the text visibly blink on every refresh, which is very obvious on holograms
     * that auto-update on a timer (leaderboards, countdowns).
     */
    private data class HologramView(
        val entityIds: List<Int>,
        val anchorWorld: UUID?,
        val anchorX: Double,
        val anchorY: Double,
        val anchorZ: Double,
        val shape: List<Boolean>,
    )

    private val playerHologramEntities = mutableMapOf<UUID, MutableMap<UUID, HologramView>>()

    /** Vertical gap between two stacked text lines. */
    private val TEXT_LINE_SPACING = 0.25
    /** Vertical gap taken by a floating item line. */
    private val ITEM_LINE_SPACING = 0.5

    /** True for a text line, false for an item line — used to detect shape changes. */
    private fun shapeOf(hologram: UnifyHologram): List<Boolean> =
        hologram.lines.map { it is HologramLine.Text }

    private fun viewOf(player: Player, hologram: UnifyHologram): HologramView? =
        playerHologramEntities[player.uniqueId]?.get(hologram.uuid)

    override fun showHologram(player: Player, hologram: UnifyHologram) {
        spawnHologram(player, hologram)
    }

    override fun hideHologram(player: Player, hologram: UnifyHologram) {
        val view = playerHologramEntities[player.uniqueId]?.remove(hologram.uuid) ?: return
        if (view.entityIds.isNotEmpty()) {
            val removePacket = ClientboundRemoveEntitiesPacket(*view.entityIds.toIntArray())
            (player as CraftPlayer).handle.connection.send(removePacket)
        }
    }

    override fun updateHologram(player: Player, hologram: UnifyHologram) {
        val view = viewOf(player, hologram)
        val location = hologram.location
        val sameAnchor = view != null &&
            view.anchorWorld == location.world?.uid &&
            view.anchorX == location.x &&
            view.anchorY == location.y &&
            view.anchorZ == location.z
        // Only a pure text swap at a fixed position can be done without respawning.
        if (view != null && sameAnchor && view.shape == shapeOf(hologram)) {
            updateHologramLines(player, hologram, view)
        } else {
            hideHologram(player, hologram)
            spawnHologram(player, hologram)
        }
    }

    /**
     * Builds the armour stand backing a text line.
     *
     * Every flag set here must also be set on the update path, otherwise the client
     * recalculates the nameplate offset and the line jumps.
     */
    private fun newTextStand(level: net.minecraft.world.level.Level, x: Double, y: Double, z: Double, text: String): ArmorStand {
        val armorStand = ArmorStand(level, x, y, z)
        armorStand.customName = parseText(text)
        armorStand.isCustomNameVisible = true
        armorStand.isInvisible = true
        armorStand.isNoGravity = true
        armorStand.isSmall = true
        armorStand.isMarker = true
        return armorStand
    }

    private fun spawnHologram(player: Player, hologram: UnifyHologram) {
        try {
            val lines = hologram.lines
            val entityIds = mutableListOf<Int>()
            var currentY = hologram.location.y

            // Spawn against the hologram's own world, not the viewer's — they can differ
            // while a player is mid-teleport.
            val bukkitWorld = hologram.location.world ?: player.world
            val world = (bukkitWorld as CraftWorld).handle
            val connection = (player as CraftPlayer).handle.connection

            for (line in lines) {
                when (line) {
                    is HologramLine.Text -> {
                        val armorStand = newTextStand(world, hologram.location.x, currentY, hologram.location.z, line.text)
                        // Reuse the id the entity allocated for itself: it comes from the
                        // server's real entity counter, so it cannot collide with a live
                        // entity the way a hand-rolled counter can.
                        val entityId = armorStand.id
                        entityIds.add(entityId)

                        connection.send(addEntityPacket(armorStand, entityId))
                        armorStand.entityData.packAll()?.let {
                            connection.send(ClientboundSetEntityDataPacket(entityId, it))
                        }
                        currentY -= TEXT_LINE_SPACING
                    }
                    is HologramLine.Item -> {
                        val nmsItem = CraftItemStack.asNMSCopy(line.itemStack)
                        val itemEntity = ItemEntity(world, hologram.location.x, currentY, hologram.location.z, nmsItem)
                        itemEntity.isNoGravity = true
                        itemEntity.setNeverPickUp()
                        val entityId = itemEntity.id
                        entityIds.add(entityId)

                        connection.send(addEntityPacket(itemEntity, entityId))
                        itemEntity.entityData.packAll()?.let {
                            connection.send(ClientboundSetEntityDataPacket(entityId, it))
                        }
                        currentY -= ITEM_LINE_SPACING
                    }
                }
            }

            playerHologramEntities.getOrPut(player.uniqueId) { mutableMapOf() }[hologram.uuid] = HologramView(
                entityIds = entityIds,
                anchorWorld = hologram.location.world?.uid,
                anchorX = hologram.location.x,
                anchorY = hologram.location.y,
                anchorZ = hologram.location.z,
                shape = shapeOf(hologram),
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun addEntityPacket(entity: Entity, entityId: Int): ClientboundAddEntityPacket =
        ClientboundAddEntityPacket(
            entityId,
            entity.uuid,
            entity.x,
            entity.y,
            entity.z,
            entity.xRot,
            entity.yRot,
            entity.type,
            0,
            entity.deltaMovement,
            entity.yHeadRot.toDouble()
        )

    /**
     * Re-sends only the entity metadata for each line, leaving the entities themselves
     * alone. The text changes in place with no despawn/respawn blink.
     */
    private fun updateHologramLines(player: Player, hologram: UnifyHologram, view: HologramView) {
        try {
            val lines = hologram.lines
            var currentY = hologram.location.y
            val bukkitWorld = hologram.location.world ?: player.world
            val world = (bukkitWorld as CraftWorld).handle
            val connection = (player as CraftPlayer).handle.connection

            for (i in lines.indices) {
                val entityId = view.entityIds[i]
                when (val line = lines[i]) {
                    is HologramLine.Text -> {
                        val armorStand = newTextStand(world, hologram.location.x, currentY, hologram.location.z, line.text)
                        armorStand.entityData.packAll()?.let {
                            connection.send(ClientboundSetEntityDataPacket(entityId, it))
                        }
                        currentY -= TEXT_LINE_SPACING
                    }
                    is HologramLine.Item -> {
                        val nmsItem = CraftItemStack.asNMSCopy(line.itemStack)
                        val itemEntity = ItemEntity(world, hologram.location.x, currentY, hologram.location.z, nmsItem)
                        itemEntity.isNoGravity = true
                        itemEntity.setNeverPickUp()
                        itemEntity.entityData.packAll()?.let {
                            connection.send(ClientboundSetEntityDataPacket(entityId, it))
                        }
                        currentY -= ITEM_LINE_SPACING
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
            team.playerPrefix = parseText(prefix)
            team.playerSuffix = parseText(suffix)
            team.color = getChatFormatting(extractColorCode(prefix))
            team.nameTagVisibility = Team.Visibility.ALWAYS
            team.collisionRule = Team.CollisionRule.NEVER
            team.players.add(scoreboardEntry)
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
