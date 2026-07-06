package me.jordanfails.unify.nms.v26_R1

import de.tr7zw.nbtapi.NBT
import io.papermc.paper.adventure.PaperAdventure
import com.mojang.authlib.GameProfile
import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.bossbar.BossBarColor
import me.jordanfails.unify.bossbar.BossBarStyle
import me.jordanfails.unify.bossbar.UnifyBossBar
import me.jordanfails.unify.hologram.HologramLine
import me.jordanfails.unify.hologram.UnifyHologram
import me.jordanfails.unify.nms.NMSHandler
import me.jordanfails.unify.nms.ServerVersion
import me.jordanfails.unify.npc.UnifyNPC
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.Connection
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.PacketFlow
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
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.world.scores.Scoreboard
import net.minecraft.world.scores.Team
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.BlockState
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.craftbukkit.CraftServer
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.entity.CraftPlayer
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
    private val npcPlayers = mutableMapOf<UUID, ServerPlayer>()
    
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

    override fun spawnPlayerNpc(id: String, location: Location, skinType: UnifyNPC.SkinType?, skinValue: String?): UUID? {
        return try {
            val bukkitWorld = location.world ?: return null
            val world = (bukkitWorld as CraftWorld).handle
            val server = (Bukkit.getServer() as CraftServer).server

            val requestedName = skinValue?.trim().orEmpty()
            val resolvedNamedSkin = if (skinType == UnifyNPC.SkinType.NAME && requestedName.isNotEmpty()) {
                resolveNamedSkin(requestedName)
            } else {
                null
            }

            val profileUuid = when (skinType) {
                UnifyNPC.SkinType.NAME -> resolvedNamedSkin?.uuid
                    ?: Bukkit.getOfflinePlayer(requestedName.ifEmpty { id }).uniqueId
                else -> UUID.randomUUID()
            }
            val profileName = sanitizeNpcName(
                if (skinType == UnifyNPC.SkinType.NAME) requestedName.ifEmpty { id } else id
            )
            val profile = GameProfile(profileUuid, profileName)
            applySkin(profile, skinType, skinValue, resolvedNamedSkin)

            val npc = ServerPlayer(server, world, profile, ClientInformation.createDefault())
            positionServerPlayer(npc, location)
            attachFakeConnection(server, npc, profile)
            npc.noPhysics = true
            npc.isNoGravity = true
            npc.isInvulnerable = true

            world.addNewPlayer(npc)
            val bukkitEntity = npc.bukkitEntity
            bukkitEntity.isCollidable = false
            bukkitEntity.canPickupItems = false
            bukkitEntity.isSilent = true
            bukkitEntity.isInvulnerable = true

            npcPlayers[profileUuid] = npc
            Bukkit.getOnlinePlayers().forEach { viewer ->
                sendPlayerNpcSpawnPackets(viewer, npc)
                scheduleHidePlayerNpcFromTab(viewer, profileUuid, 20L)
            }
            profileUuid
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun despawnPlayerNpc(uuid: UUID) {
        val npc = npcPlayers.remove(uuid) ?: return
        try {
            npc.remove(Entity.RemovalReason.DISCARDED)
            Bukkit.getOnlinePlayers().forEach { viewer ->
                val craftViewer = viewer as CraftPlayer
                craftViewer.handle.connection.send(ClientboundPlayerInfoRemovePacket(listOf(uuid)))
                craftViewer.handle.connection.send(ClientboundRemoveEntitiesPacket(npc.id))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun teleportPlayerNpc(uuid: UUID, location: Location): Boolean {
        val npc = npcPlayers[uuid] ?: return false
        return try {
            positionServerPlayer(npc, location)
            npc.bukkitEntity.teleport(location)
            npc.noPhysics = true
            npc.isNoGravity = true
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun hidePlayerNpcFromTab(viewer: Player, npcUuid: UUID) {
        try {
            (viewer as CraftPlayer).handle.connection.send(ClientboundPlayerInfoRemovePacket(listOf(npcUuid)))
        } catch (_: Exception) {
        }
    }

    private fun scheduleHidePlayerNpcFromTab(viewer: Player, npcUuid: UUID, delayTicks: Long) {
        Bukkit.getScheduler().runTaskLater(UnifyCore.instance, Runnable {
            hidePlayerNpcFromTab(viewer, npcUuid)
        }, delayTicks)
    }

    override fun showPlayerNpcToViewer(viewer: Player, npcUuid: UUID) {
        val npc = npcPlayers[npcUuid] ?: return
        sendPlayerNpcSpawnPackets(viewer, npc)
        scheduleHidePlayerNpcFromTab(viewer, npcUuid, 20L)
    }

    private fun sendPlayerNpcSpawnPackets(viewer: Player, npc: ServerPlayer) {
        try {
            val craftViewer = viewer as CraftPlayer
            val connection = craftViewer.handle.connection
            createPlayerInfoAddPacket(npc)?.let { connection.send(it) }

            val addPacket = ClientboundAddEntityPacket(
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
                npc.yHeadRot.toDouble()
            )
            connection.send(addPacket)

            val dataValues = npc.entityData.packAll()
            if (dataValues != null) {
                connection.send(ClientboundSetEntityDataPacket(npc.id, dataValues))
            }

            sendHideNpcNametag(connection, npc)
        } catch (_: Exception) {
        }
    }

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

    private fun sanitizeNpcName(source: String?): String {
        val raw = (source ?: "npc").trim().ifEmpty { "npc" }
        return raw.replace(Regex("[^A-Za-z0-9_]"), "_").take(16).ifEmpty { "npc" }
    }

    private fun applySkin(
        profile: GameProfile,
        skinType: UnifyNPC.SkinType?,
        skinValue: String?,
        resolvedNamedSkin: ResolvedNamedSkin? = null,
    ) {
        if (skinType == null || skinValue.isNullOrBlank()) {
            return
        }

        when (skinType) {
            UnifyNPC.SkinType.NAME -> {
                resolvedNamedSkin?.let { resolved ->
                    setProfileTexture(profile, resolved.textureValue, resolved.textureSignature)
                    return
                }
                val onlineSource = Bukkit.getPlayerExact(skinValue) as? CraftPlayer
                if (onlineSource != null) {
                    val sourceProfile = onlineSource.handle.gameProfile
                    getProfileTextures(sourceProfile).forEach { texture ->
                        setProfileTexture(profile, texture.value, texture.signature)
                    }
                }
            }
            UnifyNPC.SkinType.URL -> {
                val json = "{\"textures\":{\"SKIN\":{\"url\":\"$skinValue\"}}}"
                val encoded = Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
                setProfileTexture(profile, encoded, null)
            }
            UnifyNPC.SkinType.BASE64 -> {
                setProfileTexture(profile, skinValue, null)
            }
        }
    }

    private data class ResolvedNamedSkin(
        val uuid: UUID,
        val textureValue: String,
        val textureSignature: String?,
    )

    private fun resolveNamedSkin(name: String): ResolvedNamedSkin? {
        val onlineSource = Bukkit.getPlayerExact(name) as? CraftPlayer
        if (onlineSource != null) {
            val sourceProfile = onlineSource.handle.gameProfile
            val texture = getProfileTextures(sourceProfile).firstOrNull()
            if (texture != null) {
                return ResolvedNamedSkin(sourceProfile.id, texture.value, texture.signature)
            }
        }

        return runCatching {
            val profileJson = URL("https://api.mojang.com/users/profiles/minecraft/$name").readJson() ?: return null
            val idMatch = Regex("\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\"").find(profileJson) ?: return null
            val idWithoutDashes = idMatch.groupValues[1]
            val mojangUuid = parseMojangUuid(idWithoutDashes) ?: return null

            val textureJson = URL("https://sessionserver.mojang.com/session/minecraft/profile/$idWithoutDashes?unsigned=false").readJson()
                ?: return null
            val textureBlock = Regex(
                "\"name\"\\s*:\\s*\"textures\"[\\s\\S]*?\"value\"\\s*:\\s*\"([^\"]+)\"(?:[\\s\\S]*?\"signature\"\\s*:\\s*\"([^\"]+)\")?"
            ).find(textureJson) ?: return null

            ResolvedNamedSkin(
                uuid = mojangUuid,
                textureValue = textureBlock.groupValues[1],
                textureSignature = textureBlock.groupValues.getOrNull(2)?.ifBlank { null }
            )
        }.getOrNull()
    }

    private fun parseMojangUuid(compactUuid: String): UUID? {
        val cleaned = compactUuid.lowercase(Locale.ROOT).trim()
        if (!cleaned.matches(Regex("^[0-9a-f]{32}$"))) {
            return null
        }

        val dashed = buildString {
            append(cleaned, 0, 8)
            append('-')
            append(cleaned, 8, 12)
            append('-')
            append(cleaned, 12, 16)
            append('-')
            append(cleaned, 16, 20)
            append('-')
            append(cleaned, 20, 32)
        }

        return runCatching { UUID.fromString(dashed) }.getOrNull()
    }

    private fun URL.readJson(): String? {
        val connection = (openConnection() as? HttpURLConnection) ?: return null
        connection.requestMethod = "GET"
        connection.connectTimeout = 3000
        connection.readTimeout = 3000
        connection.setRequestProperty("Accept", "application/json")

        return try {
            if (connection.responseCode !in 200..299) {
                null
            } else {
                connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            }
        } finally {
            connection.disconnect()
        }
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
        for (i in 0 until text.length - 1) {
            if ((text[i] == '\u00A7' || text[i] == '&') && colorChars.contains(text[i + 1])) {
                return text[i + 1].lowercaseChar()
            }
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

            // Parse MiniMessage directly into Component (no legacy detour)
            // This avoids bloated hex color codes from legacy conversion
            val adventureComponent = if (title.contains('<') && title.contains('>')) {
                MiniMessage.miniMessage().deserialize(title)
            } else {
                LegacyComponentSerializer
                    .legacyAmpersand()
                    .deserialize(title)
            }
            val nmsComponent = PaperAdventure.asVanilla(adventureComponent)
            
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
    ) {
        try {
            val scoreboard = Scoreboard()
            val team = PlayerTeam(scoreboard, teamName)
            team.displayName = Component.literal(teamName)
            team.setPlayerPrefix(Component.literal(prefix))
            team.setPlayerSuffix(Component.literal(suffix))
            setTeamColor(team, getChatFormatting(extractColorCode(prefix)))
            team.setNameTagVisibility(Team.Visibility.ALWAYS)
            team.setCollisionRule(Team.CollisionRule.NEVER)
            team.players.add(scoreboardEntry)
            val packet = ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true)
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
        val converted = convertLegacyToMiniMessage(text.replace('§', '&'))
        val adventureComponent = MiniMessage.miniMessage().deserialize(converted)
        return PaperAdventure.asVanilla(adventureComponent)
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
