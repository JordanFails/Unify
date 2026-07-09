package me.jordanfails.unify.nms.v1_9_R2

import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.bossbar.BossBarColor
import me.jordanfails.unify.bossbar.BossBarStyle
import me.jordanfails.unify.bossbar.UnifyBossBar
import me.jordanfails.unify.exception.InvalidOutputException
import me.jordanfails.unify.hologram.HologramLine
import me.jordanfails.unify.hologram.UnifyHologram
import me.jordanfails.unify.nms.NMSHandler
import me.jordanfails.unify.nms.ServerVersion
import me.jordanfails.unify.npc.UnifyNPC
import net.minecraft.server.v1_9_R2.*
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.craftbukkit.v1_9_R2.CraftWorld
import org.bukkit.craftbukkit.v1_9_R2.entity.CraftPlayer
import org.bukkit.craftbukkit.v1_9_R2.inventory.CraftInventory
import org.bukkit.craftbukkit.v1_9_R2.inventory.CraftItemStack
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import kotlin.math.sin

class NMSHandler_v1_9_R2 : NMSHandler {
    private val npcPlayers = mutableMapOf<UUID, EntityPlayer>()

    override fun sendTitle(player: Player, title: String, subtitle: String?, fadeIn: Int, stay: Int, fadeOut: Int) {
        val connection = (player as CraftPlayer).handle.playerConnection

        // Create NMS Title components
        val titleJSON = ChatComponentText(title)
        var subtitleJson: ChatComponentText? = null
        if(subtitle != null) {
            subtitleJson = ChatComponentText(subtitle)
        }
        connection.sendPacket(PacketPlayOutTitle(PacketPlayOutTitle.EnumTitleAction.TITLE, titleJSON))
        if(subtitleJson != null) {
            connection.sendPacket(PacketPlayOutTitle(PacketPlayOutTitle.EnumTitleAction.SUBTITLE, subtitleJson))
        }
        connection.sendPacket(PacketPlayOutTitle(fadeIn, stay, fadeOut))
    }

    override fun getServerVersion(): ServerVersion {
        return ServerVersion.v1_9_R2
    }

    override fun getPing(player: Player): Int {
        val connection = (player as CraftPlayer).handle.playerConnection
        if(connection.player.ping <= 0) {
            throw InvalidOutputException("${player.name}'s connection is unstable.")
        }else {
            return connection.player.ping
        }
    }

    override fun getTPS(): DoubleArray {
        return MinecraftServer.getServer().recentTps
    }


    /**
     * 1.12 and earlier still uses raw setDurability, since Damageable meta
     * wasn't added until 1.13.
     */
    override fun setItemDurability(item: ItemStack, durability: Int) {
        item.durability = durability.toShort()
    }

    /**
     * Legacy data setter — in 1.12, "data" and "durability" are the same concept.
     * e.g. colored wool, stained glass, etc.
     */
    override fun setItemData(item: ItemStack, data: Short) {
        item.durability = data
    }

    override fun setItemUnbreakable(item: ItemStack, unbreakable: Boolean) {
        val meta = item.itemMeta ?: return
        try {
            val spigotMeta = meta.spigot()
            val method = spigotMeta::class.java.getMethod("setUnbreakable", Boolean::class.javaPrimitiveType)
            method.invoke(spigotMeta, unbreakable)
            item.itemMeta = meta
        } catch (_: Throwable) { }
    }

    override fun applySkullOwner(item: ItemStack, ownerUuid: UUID?, ownerName: String?): Boolean {
        val meta = item.itemMeta as? SkullMeta ?: return false
        val resolvedName = ownerName?.takeIf { it.isNotBlank() }
            ?: ownerUuid?.let { Bukkit.getOfflinePlayer(it).name }?.takeIf { it.isNotBlank() }
            ?: return false
        return try {
            meta.setOwner(resolvedName)
            item.itemMeta = meta
            true
        } catch (_: Throwable) {
            false
        }
    }

    override fun applySkullTexture(item: ItemStack, base64Texture: String): Boolean {
        val meta = item.itemMeta as? SkullMeta ?: return false
        return try {
            val profile = GameProfile(UUID.randomUUID(), "custom")
            profile.properties.removeAll("textures")
            profile.properties.put("textures", Property("textures", base64Texture))
            val field = meta.javaClass.getDeclaredField("profile")
            field.isAccessible = true
            field.set(meta, profile)
            item.itemMeta = meta
            true
        } catch (_: Throwable) {
            false
        }
    }

    override fun openMenuInventory(player: Player, inventory: Inventory, title: String) {
        val craft = player as CraftPlayer
        val handle = craft.handle
        val windowId = handle.nextContainerCounter()
        val container = ContainerChest(
            handle.inventory,
            (inventory as CraftInventory).inventory,
            handle
        )
        container.windowId = windowId
        handle.activeContainer = container

        // Titles longer than 32 chars crash old clients
        val safeTitle = ChatComponentText(title.take(32))
        val packet = PacketPlayOutOpenWindow(windowId, "minecraft:container", safeTitle, inventory.size)
        handle.playerConnection.sendPacket(packet)
        handle.updateInventory(container)
    }

    override fun updateMenuTitle(player: Player, title: String) {
        val craft = player as CraftPlayer
        val handle = craft.handle
        val container = handle.activeContainer
        val safeTitle = ChatComponentText(title.take(32))
        val packet = PacketPlayOutOpenWindow(
            container.windowId,
            "minecraft:container",
            safeTitle,
            container.c.size   // ← correct field name in 1.8
        )
        handle.playerConnection.sendPacket(packet)
        handle.updateInventory(container)
    }

    override fun refreshMenuInventory(player: Player) {
        val craft = player as CraftPlayer
        val handle = craft.handle
        handle.updateInventory(handle.activeContainer)
    }

    override fun isCustomInventory(inventory: Inventory): Boolean {
        return try {
            val craft = inventory as? CraftInventory ?: return false
            val nmsInv: IInventory = craft.inventory
            // any inventory that is not tied to a tile entity (like TileEntityChest)
            // or a player container is "custom"
            val packageName = nmsInv.javaClass.name
            !packageName.contains("TileEntity") && !packageName.contains("PlayerInventory")
        } catch (_: Throwable) {
            false
        }
    }

    override fun spawnPlayerNpc(id: String, location: Location, skinType: UnifyNPC.SkinType?, skinValue: String?): UUID? {
        return try {
            val world = (location.world as? CraftWorld)?.handle ?: return null
            val server = MinecraftServer.getServer()
            val profileUuid = UUID.randomUUID()
            val profile = GameProfile(profileUuid, sanitizeNpcName(if (skinType == UnifyNPC.SkinType.NAME) skinValue else id))
            applySkin(profile, skinType, skinValue)

            val npc = EntityPlayer(server, world, profile, PlayerInteractManager(world))
            npc.setLocation(location.x, location.y, location.z, location.yaw, location.pitch)
            npc.noclip = true
            world.addEntity(npc)

            npcPlayers[profileUuid] = npc
            Bukkit.getOnlinePlayers().forEach { viewer ->
                sendPlayerNpcSpawnPackets(viewer, npc)
                Bukkit.getScheduler().runTaskLater(UnifyCore.instance, Runnable {
                    hidePlayerNpcFromTab(viewer, profileUuid)
                }, 20L)
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
            npc.world.removeEntity(npc)
            val destroy = PacketPlayOutEntityDestroy(npc.id)
            Bukkit.getOnlinePlayers().forEach { viewer ->
                val connection = (viewer as CraftPlayer).handle.playerConnection
                connection.sendPacket(PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER, npc))
                connection.sendPacket(destroy)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun teleportPlayerNpc(uuid: UUID, location: Location): Boolean {
        val npc = npcPlayers[uuid] ?: return false
        return try {
            npc.setLocation(location.x, location.y, location.z, location.yaw, location.pitch)
            npc.noclip = true
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun hidePlayerNpcFromTab(viewer: Player, npcUuid: UUID) {
        val npc = npcPlayers[npcUuid] ?: return
        try {
            (viewer as CraftPlayer).handle.playerConnection.sendPacket(
                PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER, npc)
            )
        } catch (_: Exception) {
        }
    }

    override fun showPlayerNpcToViewer(viewer: Player, npcUuid: UUID) {
        val npc = npcPlayers[npcUuid] ?: return
        sendPlayerNpcSpawnPackets(viewer, npc)
        Bukkit.getScheduler().runTaskLater(UnifyCore.instance, Runnable {
            hidePlayerNpcFromTab(viewer, npcUuid)
        }, 20L)
    }

    private fun sendPlayerNpcSpawnPackets(viewer: Player, npc: EntityPlayer) {
        try {
            val connection = (viewer as CraftPlayer).handle.playerConnection
            connection.sendPacket(
                PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, npc)
            )
            connection.sendPacket(PacketPlayOutNamedEntitySpawn(npc))
            connection.sendPacket(PacketPlayOutEntityMetadata(npc.id, npc.dataWatcher, true))
            sendHideNpcNametag(connection, npc)
        } catch (_: Exception) {
        }
    }

    private fun sendHideNpcNametag(connection: PlayerConnection, npc: EntityPlayer) {
        val teamName = "npc_nt_${npc.uniqueID.toString().take(8)}"
        val packet = PacketPlayOutScoreboardTeam()
        setField(packet, "a", teamName)
        setField(packet, "b", teamName)
        setField(packet, "c", "")
        setField(packet, "d", "")
        setField(packet, "e", "never")
        setField(packet, "f", 0)
        setField(packet, "h", 0)
        setField(packet, "i", 0)
        connection.sendPacket(packet)
        val addPacket = PacketPlayOutScoreboardTeam()
        setField(addPacket, "a", teamName)
        @Suppress("UNCHECKED_CAST")
        val members = getField(addPacket, "g") as? MutableCollection<String> ?: return
        members.add(npc.getName())
        setField(addPacket, "h", 3)
        connection.sendPacket(addPacket)
    }

    private fun sanitizeNpcName(source: String?): String {
        val raw = (source ?: "npc").trim().ifEmpty { "npc" }
        return raw.replace(Regex("[^A-Za-z0-9_]"), "_").take(16).ifEmpty { "npc" }
    }

    private fun applySkin(profile: GameProfile, skinType: UnifyNPC.SkinType?, skinValue: String?) {
        if (skinType == null || skinValue.isNullOrBlank()) return
        when (skinType) {
            UnifyNPC.SkinType.NAME -> {
                val source = Bukkit.getPlayerExact(skinValue) as? CraftPlayer ?: return
                val sourceProfile = source.handle.profile
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
        sendNametagPacket(viewer, target, "never", 0)
    }

    override fun sendShowNametagPacket(viewer: Player, target: Player) {
        sendNametagPacket(viewer, target, "always", 0)
    }

    override fun sendRemoveNametagTeamPacket(viewer: Player, target: Player) {
        val packet = PacketPlayOutScoreboardTeam()
        val teamName = getTeamName(target)
        
        setField(packet, "a", teamName) // team name
        setField(packet, "h", 1) // mode: 1 = remove team
        
        (viewer as CraftPlayer).handle.playerConnection.sendPacket(packet)
    }
    
    override fun sendNametagPacket(viewer: Player, target: Player, teamName: String, prefix: String, suffix: String) {
        val packet = PacketPlayOutScoreboardTeam()
        val safeName = teamName.take(16)
        
        setField(packet, "a", safeName) // team name
        setField(packet, "b", safeName) // display name
        setField(packet, "c", prefix.take(16)) // prefix
        setField(packet, "d", suffix.take(16)) // suffix
        setField(packet, "e", "always") // nameTagVisibility
        setField(packet, "f", 0) // color (0 = no color)
        
        @Suppress("UNCHECKED_CAST")
        val players = getField(packet, "g") as MutableCollection<String>
        players.add(target.name)
        
        setField(packet, "h", 0) // mode: 0 = create
        setField(packet, "i", 0) // friendly fire flags
        
        (viewer as CraftPlayer).handle.playerConnection.sendPacket(packet)
    }

    private fun sendNametagPacket(viewer: Player, target: Player, visibility: String, mode: Int) {
        val packet = PacketPlayOutScoreboardTeam()
        val teamName = getTeamName(target)
        
        setField(packet, "a", teamName) // team name
        setField(packet, "b", teamName) // display name
        setField(packet, "c", "") // prefix
        setField(packet, "d", "") // suffix
        setField(packet, "e", visibility) // nameTagVisibility: "always", "never", "hideForOtherTeams", "hideForOwnTeam"
        setField(packet, "f", 0) // color (0 = no color)
        
        @Suppress("UNCHECKED_CAST")
        val players = getField(packet, "g") as MutableCollection<String>
        players.add(target.name)
        
        setField(packet, "h", mode) // mode: 0 = create, 1 = remove, 2 = update, 3 = add players, 4 = remove players
        setField(packet, "i", 0) // friendly fire flags
        
        (viewer as CraftPlayer).handle.playerConnection.sendPacket(packet)
    }

    private fun setField(obj: Any, fieldName: String, value: Any) {
        try {
            val field = obj.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(obj, value)
        } catch (_: Throwable) { }
    }

    private fun getField(obj: Any, fieldName: String): Any? {
        return try {
            val field = obj.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.get(obj)
        } catch (_: Throwable) { null }
    }
    
    // 1.9 has legacy limits
    override fun getScoreboardLineLimit(): Int = 32
    override fun getTeamPrefixLimit(): Int = 16
    
    override fun sendScoreboardObjective(player: Player, name: String, title: String, mode: Int) {
        try {
            val connection = (player as CraftPlayer).handle.playerConnection
            val packet = PacketPlayOutScoreboardObjective()
            
            setField(packet, "a", name)
            setField(packet, "b", title)
            setField(packet, "c", IScoreboardCriteria.EnumScoreboardHealthDisplay.INTEGER)
            setField(packet, "d", mode)
            
            connection.sendPacket(packet)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun sendScoreboardDisplaySlot(player: Player, objectiveName: String, slot: Int) {
        try {
            val connection = (player as CraftPlayer).handle.playerConnection
            val packet = PacketPlayOutScoreboardDisplayObjective()
            
            setField(packet, "a", slot)
            setField(packet, "b", objectiveName)
            
            connection.sendPacket(packet)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun sendScoreboardScore(player: Player, objectiveName: String, entry: String, score: Int, mode: Int) {
        try {
            val connection = (player as CraftPlayer).handle.playerConnection
            val packet = PacketPlayOutScoreboardScore()
            
            setField(packet, "a", entry)
            setField(packet, "b", objectiveName)
            setField(packet, "c", score)
            setField(packet, "d", if (mode == 0) PacketPlayOutScoreboardScore.EnumScoreboardAction.CHANGE else PacketPlayOutScoreboardScore.EnumScoreboardAction.REMOVE)
            
            connection.sendPacket(packet)
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
            val connection = (player as CraftPlayer).handle.playerConnection
            val packet = PacketPlayOutScoreboardTeam()
            val safeTeam = teamName.take(16)
            setField(packet, "a", safeTeam)
            setField(packet, "b", safeTeam)
            setField(packet, "c", prefix.take(16))
            setField(packet, "d", suffix.take(16))
            setField(packet, "e", "always")
            setField(packet, "f", 0)
            @Suppress("UNCHECKED_CAST")
            val players = getField(packet, "g") as MutableCollection<String>
            players.clear()
            players.add(scoreboardEntry)
            setField(packet, "h", if (create) 0 else 2)
            setField(packet, "i", 0)
            connection.sendPacket(packet)
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
    
    // --- Hologram Implementation (1.9 uses ArmorStands) ---
    private val playerHologramEntities = mutableMapOf<UUID, MutableMap<UUID, List<Int>>>()
    private val playerAnimatedHologramEntities = mutableMapOf<UUID, MutableMap<UUID, List<AnimatedArmorStand>>>()
    private var hologramAnimatorTaskId: Int? = null
    private var hologramAnimatorTick: Long = 0L
    private var entityIdCounter = 1000000

    private data class AnimatedArmorStand(
        val entityId: Int,
        val x: Double,
        val baseY: Double,
        val z: Double,
        val tickOffset: Int
    )

    private fun ensureHologramAnimatorRunning() {
        if (hologramAnimatorTaskId != null) return
        hologramAnimatorTaskId = Bukkit.getScheduler().runTaskTimer(
            UnifyCore.instance,
            Runnable { tickAnimatedHologramEntities() },
            1L,
            1L
        ).taskId
    }

    private fun stopHologramAnimatorIfIdle() {
        if (playerAnimatedHologramEntities.isNotEmpty()) return
        val taskId = hologramAnimatorTaskId ?: return
        Bukkit.getScheduler().cancelTask(taskId)
        hologramAnimatorTaskId = null
        hologramAnimatorTick = 0L
    }

    private fun tickAnimatedHologramEntities() {
        hologramAnimatorTick++

        val iterator = playerAnimatedHologramEntities.entries.iterator()
        while (iterator.hasNext()) {
            val (playerId, holograms) = iterator.next()
            val player = Bukkit.getPlayer(playerId)
            if (player == null || !player.isOnline) {
                iterator.remove()
                continue
            }

            if (holograms.isEmpty()) continue

            val world = (player.world as CraftWorld).handle
            val connection = (player as CraftPlayer).handle.playerConnection

            for (animated in holograms.values) {
                for (entry in animated) {
                    val t = hologramAnimatorTick + entry.tickOffset
                    val yaw = ((t * 10L) % 360L).toFloat()
                    val y = entry.baseY + sin(t / 10.0) * 0.08

                    val armorStand = EntityArmorStand(world)
                    setEntityId(armorStand, entry.entityId)
                    armorStand.isInvisible = true
                    armorStand.setGravity(false)
                    armorStand.setSmall(true)
                    armorStand.setLocation(entry.x, y, entry.z, yaw, 0f)

                    connection.sendPacket(PacketPlayOutEntityTeleport(armorStand))
                }
            }
        }

        // Clean up empty player entries
        playerAnimatedHologramEntities.entries.removeIf { it.value.isEmpty() }
        stopHologramAnimatorIfIdle()
    }
    
    override fun showHologram(player: Player, hologram: UnifyHologram) {
        spawnHologram(player, hologram)
    }
    
    override fun hideHologram(player: Player, hologram: UnifyHologram) {
        val entityIds = playerHologramEntities[player.uniqueId]?.remove(hologram.uuid) ?: return
        playerAnimatedHologramEntities[player.uniqueId]?.remove(hologram.uuid)
        playerAnimatedHologramEntities[player.uniqueId]?.let { if (it.isEmpty()) playerAnimatedHologramEntities.remove(player.uniqueId) }
        stopHologramAnimatorIfIdle()
        if (entityIds.isNotEmpty()) {
            val destroyPacket = PacketPlayOutEntityDestroy(*entityIds.toIntArray())
            (player as CraftPlayer).handle.playerConnection.sendPacket(destroyPacket)
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
            val animated = mutableListOf<AnimatedArmorStand>()
            var currentY = hologram.location.y
            
            val world = (player.world as CraftWorld).handle
            val connection = (player as CraftPlayer).handle.playerConnection
            
            for (line in lines) {
                val entityId = entityIdCounter++
                entityIds.add(entityId)
                
                val armorStand = EntityArmorStand(world)
                setEntityId(armorStand, entityId)
                armorStand.setGravity(false)
                armorStand.isInvisible = true
                
                when (line) {
                    is HologramLine.Text -> {
                        armorStand.setLocation(hologram.location.x, currentY, hologram.location.z, 0f, 0f)
                        armorStand.customName = me.jordanfails.unify.utils.CC.translate(line.text)
                        armorStand.customNameVisible = true
                        armorStand.setSmall(true)
                        armorStand.setMarker(true)
                        
                        val spawnPacket = PacketPlayOutSpawnEntityLiving(armorStand)
                        connection.sendPacket(spawnPacket)
                        
                        val metaPacket = PacketPlayOutEntityMetadata(entityId, armorStand.dataWatcher, true)
                        connection.sendPacket(metaPacket)
                        currentY -= 0.25
                    }
                    is HologramLine.Item -> {
                        // Small armor stand with item on head
                        armorStand.setLocation(hologram.location.x, currentY - 0.6, hologram.location.z, 0f, 0f)
                        armorStand.customNameVisible = false
                        armorStand.setSmall(true)
                        armorStand.setMarker(true)
                        
                        val spawnPacket = PacketPlayOutSpawnEntityLiving(armorStand)
                        connection.sendPacket(spawnPacket)
                        
                        val metaPacket = PacketPlayOutEntityMetadata(entityId, armorStand.dataWatcher, true)
                        connection.sendPacket(metaPacket)
                        
                        // Send equipment packet for helmet slot (EnumItemSlot.HEAD in 1.9)
                        val nmsItem = CraftItemStack.asNMSCopy(line.itemStack)
                        val equipPacket = PacketPlayOutEntityEquipment(entityId, EnumItemSlot.HEAD, nmsItem)
                        connection.sendPacket(equipPacket)

                        if (line.spin) {
                            animated.add(
                                AnimatedArmorStand(
                                    entityId = entityId,
                                    x = hologram.location.x,
                                    baseY = currentY - 0.6,
                                    z = hologram.location.z,
                                    tickOffset = entityId and 0xFF
                                )
                            )
                        }
                        
                        currentY -= 0.4
                    }
                }
            }
            playerHologramEntities.getOrPut(player.uniqueId) { mutableMapOf() }[hologram.uuid] = entityIds
            if (animated.isNotEmpty()) {
                playerAnimatedHologramEntities.getOrPut(player.uniqueId) { mutableMapOf() }[hologram.uuid] = animated
                ensureHologramAnimatorRunning()
            } else {
                playerAnimatedHologramEntities[player.uniqueId]?.remove(hologram.uuid)
                playerAnimatedHologramEntities[player.uniqueId]?.let { if (it.isEmpty()) playerAnimatedHologramEntities.remove(player.uniqueId) }
                stopHologramAnimatorIfIdle()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun updateHologramLines(player: Player, hologram: UnifyHologram, entityIds: List<Int>) {
        try {
            val lines = hologram.lines
            val animated = mutableListOf<AnimatedArmorStand>()
            var currentY = hologram.location.y
            val world = (player.world as CraftWorld).handle
            val connection = (player as CraftPlayer).handle.playerConnection
            
            for (i in lines.indices) {
                val entityId = entityIds[i]
                val line = lines[i]
                
                val armorStand = EntityArmorStand(world)
                setEntityId(armorStand, entityId)
                armorStand.isInvisible = true
                armorStand.setGravity(false)
                
                when (line) {
                    is HologramLine.Text -> {
                        armorStand.setLocation(hologram.location.x, currentY, hologram.location.z, 0f, 0f)
                        armorStand.customName = me.jordanfails.unify.utils.CC.translate(line.text)
                        armorStand.customNameVisible = true
                        armorStand.setSmall(true)
                        armorStand.isMarker = true
                        
                        val metaPacket = PacketPlayOutEntityMetadata(entityId, armorStand.dataWatcher, true)
                        connection.sendPacket(metaPacket)
                        
                        val teleportPacket = PacketPlayOutEntityTeleport(armorStand)
                        connection.sendPacket(teleportPacket)
                        currentY -= 0.25
                    }
                    is HologramLine.Item -> {
                        armorStand.setLocation(hologram.location.x, currentY - 0.6, hologram.location.z, 0f, 0f)
                        armorStand.customNameVisible = false
                        armorStand.setSmall(true)
                        armorStand.isMarker = true
                        
                        val metaPacket = PacketPlayOutEntityMetadata(entityId, armorStand.dataWatcher, true)
                        connection.sendPacket(metaPacket)
                        
                        val teleportPacket = PacketPlayOutEntityTeleport(armorStand)
                        connection.sendPacket(teleportPacket)
                        
                        // Send equipment packet for helmet slot
                        val nmsItem = CraftItemStack.asNMSCopy(line.itemStack)
                        val equipPacket = PacketPlayOutEntityEquipment(entityId, EnumItemSlot.HEAD, nmsItem)
                        connection.sendPacket(equipPacket)

                        if (line.spin) {
                            animated.add(
                                AnimatedArmorStand(
                                    entityId = entityId,
                                    x = hologram.location.x,
                                    baseY = currentY - 0.6,
                                    z = hologram.location.z,
                                    tickOffset = entityId and 0xFF
                                )
                            )
                        }
                        
                        currentY -= 0.4
                    }
                }
            }

            if (animated.isNotEmpty()) {
                playerAnimatedHologramEntities.getOrPut(player.uniqueId) { mutableMapOf() }[hologram.uuid] = animated
                ensureHologramAnimatorRunning()
            } else {
                playerAnimatedHologramEntities[player.uniqueId]?.remove(hologram.uuid)
                playerAnimatedHologramEntities[player.uniqueId]?.let { if (it.isEmpty()) playerAnimatedHologramEntities.remove(player.uniqueId) }
                stopHologramAnimatorIfIdle()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            hideHologram(player, hologram)
            spawnHologram(player, hologram)
        }
    }
    
    private fun setEntityId(entity: Entity, id: Int) {
        try {
            val field = Entity::class.java.getDeclaredField("id")
            field.isAccessible = true
            field.set(entity, id)
        } catch (_: Throwable) {}
    }

    override fun sendTabHeaderFooter(player: Player, header: String, footer: String) {
        try {
            val connection = (player as CraftPlayer).handle.playerConnection
            val headerComponent = IChatBaseComponent.ChatSerializer.a("{\"text\":\"${me.jordanfails.unify.utils.CC.translate(header)}\"}")!!
            val footerComponent = IChatBaseComponent.ChatSerializer.a("{\"text\":\"${me.jordanfails.unify.utils.CC.translate(footer)}\"}")!!
            val packet = PacketPlayOutPlayerListHeaderFooter()
            setField(packet, "a", headerComponent)
            setField(packet, "b", footerComponent)
            connection.sendPacket(packet)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
