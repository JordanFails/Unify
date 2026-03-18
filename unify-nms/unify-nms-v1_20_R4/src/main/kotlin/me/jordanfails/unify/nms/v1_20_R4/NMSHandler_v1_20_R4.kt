package me.jordanfails.unify.nms.v1_20_R4

import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.bossbar.BossBarColor
import me.jordanfails.unify.bossbar.BossBarStyle
import me.jordanfails.unify.bossbar.UnifyBossBar
import me.jordanfails.unify.hologram.HologramLine
import me.jordanfails.unify.hologram.UnifyHologram
import me.jordanfails.unify.nms.NMSHandler
import me.jordanfails.unify.npc.UnifyNPC
import net.minecraft.ChatFormatting
import net.minecraft.network.Connection
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket
import net.minecraft.server.level.ClientInformation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.CommonListenerCookie
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.component.ResolvableProfile
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.world.scores.Scoreboard
import net.minecraft.world.scores.Team
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
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

@Suppress("unused")
class NMSHandler_v1_20_R4 : NMSHandler {
    private val npcPlayers = mutableMapOf<UUID, ServerPlayer>()
    
    override fun sendTitle(
        player: Player,
        title: String,
        subtitle: String?,
        fadeIn: Int,
        stay: Int,
        fadeOut: Int
    ) {
        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut)
    }

    override fun getServerVersion(): String {
        return Bukkit.getBukkitVersion().split("-").first()
    }

    override fun getPing(player: Player): Int {
        return player.ping
    }

    override fun getTPS(): DoubleArray {
        return try {
            val serverClass = Bukkit.getServer().javaClass
            val getServerMethod = serverClass.getMethod("getServer")
            val minecraftServer = getServerMethod.invoke(Bukkit.getServer())
            val tpsField = minecraftServer.javaClass.getField("recentTps")
            tpsField.get(minecraftServer) as DoubleArray
        } catch (e: Exception) {
            doubleArrayOf(20.0, 20.0, 20.0)
        }
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

    override fun applySkullTexture(item: ItemStack, base64Texture: String): Boolean {
        val meta = item.itemMeta as? SkullMeta ?: return false
        return try {
            val profile = GameProfile(UUID.randomUUID(), "custom")
            profile.properties.removeAll("textures")
            profile.properties.put("textures", Property("textures", base64Texture))

            val field = findFieldInHierarchy(meta.javaClass, "profile") ?: return false
            field.isAccessible = true

            val profileValue: Any = try {
                val resolvable = ResolvableProfile(profile)
                if (field.type.isAssignableFrom(resolvable.javaClass)) resolvable else profile
            } catch (_: Throwable) {
                profile
            }

            field.set(meta, profileValue)
            item.itemMeta = meta
            true
        } catch (_: Throwable) {
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

            val profileUuid = UUID.randomUUID()
            val profileName = sanitizeNpcName(if (skinType == UnifyNPC.SkinType.NAME) skinValue else id)
            val profile = GameProfile(profileUuid, profileName)
            applySkin(profile, skinType, skinValue)

            val npc = ServerPlayer(server, world, profile, ClientInformation.createDefault())
            npc.absMoveTo(location.x, location.y, location.z, location.yaw, location.pitch)
            attachFakeConnection(server, npc, profile)
            npc.noPhysics = true
            npc.setNoGravity(true)
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
            npc.absMoveTo(location.x, location.y, location.z, location.yaw, location.pitch)
            npc.noPhysics = true
            npc.setNoGravity(true)
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

            val addPacket = ClientboundAddEntityPacket(npc)
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

    private fun attachFakeConnection(
        server: net.minecraft.server.MinecraftServer,
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

    private fun sanitizeNpcName(source: String?): String {
        val raw = (source ?: "npc").trim().ifEmpty { "npc" }
        return raw.replace(Regex("[^A-Za-z0-9_]"), "_").take(16).ifEmpty { "npc" }
    }

    private fun applySkin(profile: GameProfile, skinType: UnifyNPC.SkinType?, skinValue: String?) {
        if (skinType == null || skinValue.isNullOrBlank()) {
            return
        }

        when (skinType) {
            UnifyNPC.SkinType.NAME -> {
                val onlineSource = Bukkit.getPlayerExact(skinValue) as? CraftPlayer ?: return
                val sourceProfile = onlineSource.handle.gameProfile
                sourceProfile.properties.get("textures").forEach { texture ->
                    profile.properties.put("textures", Property("textures", texture.value, texture.signature))
                }
            }
            UnifyNPC.SkinType.URL -> {
                val json = "{\"textures\":{\"SKIN\":{\"url\":\"$skinValue\"}}}"
                val encoded = Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
                profile.properties.put("textures", Property("textures", encoded))
            }
            UnifyNPC.SkinType.BASE64 -> {
                profile.properties.put("textures", Property("textures", skinValue))
            }
        }
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
    
    // 1.20+ uses components - effectively unlimited (32767 is protocol max)
    override fun getScoreboardLineLimit(): Int = 32767
    override fun getTeamPrefixLimit(): Int = 32767
    
    override fun sendScoreboardObjective(player: Player, name: String, title: String, mode: Int) {
        try {
            val connection = (player as CraftPlayer).handle.connection
            
            // Parse MiniMessage directly into Component (no legacy detour)
            val adventureComponent = if (title.contains('<') && title.contains('>')) {
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(title)
            } else {
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacyAmpersand()
                    .deserialize(title)
            }
            val nmsComponent = io.papermc.paper.adventure.PaperAdventure.asVanilla(adventureComponent)
            
            val dummyScoreboard = net.minecraft.world.scores.Scoreboard()
            val objective = dummyScoreboard.addObjective(
                name,
                net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,
                nmsComponent,
                net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER,
                true,
                null
            )
            
            val packet = net.minecraft.network.protocol.game.ClientboundSetObjectivePacket(objective, mode)
            connection.send(packet)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun sendScoreboardDisplaySlot(player: Player, objectiveName: String, slot: Int) {
        try {
            val connection = (player as CraftPlayer).handle.connection
            val displaySlot = when (slot) {
                0 -> net.minecraft.world.scores.DisplaySlot.LIST
                1 -> net.minecraft.world.scores.DisplaySlot.SIDEBAR
                2 -> net.minecraft.world.scores.DisplaySlot.BELOW_NAME
                else -> return
            }
            
            val dummyScoreboard = net.minecraft.world.scores.Scoreboard()
            val objective = dummyScoreboard.addObjective(
                objectiveName,
                net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,
                net.minecraft.network.chat.Component.empty(),
                net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER,
                true,
                null
            )
            
            val packet = net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket(displaySlot, objective)
            connection.send(packet)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun sendScoreboardScore(player: Player, objectiveName: String, entry: String, score: Int, mode: Int) {
        try {
            val connection = (player as CraftPlayer).handle.connection
            
            val packet = when (mode) {
                0 -> net.minecraft.network.protocol.game.ClientboundSetScorePacket(
                    entry,
                    objectiveName,
                    score,
                    null,
                    null
                )
                1 -> net.minecraft.network.protocol.game.ClientboundResetScorePacket(
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
    
    // --- Hologram Implementation (1.20 uses NMS ArmorStands) ---
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
                        
                        val addPacket = ClientboundAddEntityPacket(armorStand)
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
                        itemEntity.setNoGravity(true)
                        itemEntity.setNeverPickUp()
                        
                        val addPacket = ClientboundAddEntityPacket(itemEntity)
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
                val line = lines[i]
                
                when (line) {
                    is HologramLine.Text -> {
                        val armorStand = ArmorStand(world, hologram.location.x, currentY, hologram.location.z)
                        armorStand.id = entityId
                        armorStand.customName = parseText(line.text)
                        armorStand.isCustomNameVisible = true
                        armorStand.isInvisible = true
                        armorStand.isMarker = true
                        
                        val dataValues = armorStand.entityData.packAll()
                        if (dataValues != null) {
                            val metaPacket = ClientboundSetEntityDataPacket(entityId, dataValues)
                            connection.send(metaPacket)
                        }
                        
                        val teleportPacket = ClientboundTeleportEntityPacket(armorStand)
                        connection.send(teleportPacket)
                        currentY -= 0.25
                    }
                    is HologramLine.Item -> {
                        val nmsItem = CraftItemStack.asNMSCopy(line.itemStack)
                        val itemEntity = ItemEntity(world, hologram.location.x, currentY, hologram.location.z, nmsItem)
                        itemEntity.id = entityId
                        itemEntity.setNoGravity(true)
                        
                        val dataValues = itemEntity.entityData.packAll()
                        if (dataValues != null) {
                            val metaPacket = ClientboundSetEntityDataPacket(entityId, dataValues)
                            connection.send(metaPacket)
                        }
                        
                        val teleportPacket = ClientboundTeleportEntityPacket(itemEntity)
                        connection.send(teleportPacket)
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
    
    private fun parseText(text: String): net.minecraft.network.chat.Component {
        val converted = convertLegacyToMiniMessage(text.replace('§', '&'))
        val adventureComponent = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(converted)
        return io.papermc.paper.adventure.PaperAdventure.asVanilla(adventureComponent)
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
            val packet = net.minecraft.network.protocol.game.ClientboundTabListPacket(headerComponent, footerComponent)
            connection.send(packet)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
