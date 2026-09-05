package me.jordanfails.unify.nms.v1_20_R4

import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import io.papermc.paper.adventure.PaperAdventure
import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.bossbar.BossBarColor
import me.jordanfails.unify.bossbar.BossBarFlag
import me.jordanfails.unify.bossbar.BossBarStyle
import me.jordanfails.unify.bossbar.UnifyBossBar
import me.jordanfails.unify.hologram.HologramLine
import me.jordanfails.unify.hologram.UnifyHologram
import me.jordanfails.unify.menu.anvil.AnvilHandle
import me.jordanfails.unify.nms.NMSHandler
import me.jordanfails.unify.nms.ServerVersion
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.Connection
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
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
import net.minecraft.world.inventory.AnvilMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.component.ResolvableProfile
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.world.scores.Scoreboard
import net.minecraft.world.scores.Team
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.BlockState
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarFlag
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
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

@Suppress("unused")
class NMSHandler_v1_20_R4 : NMSHandler {
    
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

    override fun getServerVersion(): ServerVersion {
        return ServerVersion.v1_20_R4
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

    override fun applySkullOwner(item: ItemStack, ownerUuid: UUID?, ownerName: String?): Boolean {
        val meta = item.itemMeta as? SkullMeta ?: return false
        return try {
            val owningPlayer = when {
                ownerUuid != null -> Bukkit.getOfflinePlayer(ownerUuid)
                !ownerName.isNullOrBlank() -> Bukkit.getOfflinePlayer(ownerName)
                else -> return false
            }
            meta.owningPlayer = owningPlayer
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
            team.playerPrefix = parseText(prefix)
            team.playerSuffix = parseText(suffix)
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
    
    /**
     * Team color paints the **player name** (not the prefix text).
     * Use the **last** real color in [text] so a trailing `&f` can force a white name
     * while an earlier code still colors the rank prefix via [parseText].
     */
    private fun extractColorCode(text: String): Char {
        val colorChars = "0123456789abcdefABCDEF"
        var last = 'f'
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
                    last = code.lowercaseChar()
                }
            }
            i++
        }
        return last
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
            
            val nmsComponent = parseText(title)
            
            val dummyScoreboard = Scoreboard()
            val objective = dummyScoreboard.addObjective(
                name,
                ObjectiveCriteria.DUMMY,
                nmsComponent,
                ObjectiveCriteria.RenderType.INTEGER,
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

            val dummyScoreboard = Scoreboard()
            val objective = dummyScoreboard.addObjective(
                objectiveName,
                ObjectiveCriteria.DUMMY,
                Component.empty(),
                ObjectiveCriteria.RenderType.INTEGER,
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
        applyFlags(bukkitBar, bossBar)
    }
    
    private fun createBukkitBossBar(bossBar: UnifyBossBar): BossBar {
        return Bukkit.createBossBar(bossBar.title, toBukkitColor(bossBar.color), toBukkitStyle(bossBar.style)).apply {
            progress = bossBar.progress
            applyFlags(this, bossBar)
        }
    }
    
    private fun applyFlags(bukkitBar: BossBar, bossBar: UnifyBossBar) {
        for (flag in BossBarFlag.entries) {
            val bukkitFlag = toBukkitFlag(flag)
            if (bossBar.flags.contains(flag)) bukkitBar.addFlag(bukkitFlag) else bukkitBar.removeFlag(bukkitFlag)
        }
    }
    
    private fun toBukkitFlag(flag: BossBarFlag): BarFlag {
        return when (flag) {
            BossBarFlag.DARKEN_SKY -> BarFlag.DARKEN_SKY
            BossBarFlag.PLAY_BOSS_MUSIC -> BarFlag.PLAY_BOSS_MUSIC
            BossBarFlag.CREATE_FOG -> BarFlag.CREATE_FOG
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
                        itemEntity.isNoGravity = true
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
                when (val line = lines[i]) {
                    is HologramLine.Text -> {
                        val armorStand = ArmorStand(world, hologram.location.x, currentY, hologram.location.z)
                        armorStand.id = entityId
                        armorStand.customName = parseText(line.text)
                        armorStand.isCustomNameVisible = true
                        armorStand.isInvisible = true
                        // Must match spawnHologram exactly: a flag missing here changes the
                        // nameplate offset (small) or lets the client apply gravity, so the line
                        // shifts and drifts on the first update.
                        armorStand.isNoGravity = true
                        armorStand.isSmall = true
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
                        itemEntity.isNoGravity = true
                        
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
    
    private fun parseText(text: String): Component {
        if (text.contains('<') && text.contains('>')) {
            try {
                val converted = convertLegacyToMiniMessage(text.replace('§', '&'))
                val adventureComponent = MiniMessage.miniMessage().deserialize(converted)
                return PaperAdventure.asVanilla(adventureComponent)
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
            val packet = net.minecraft.network.protocol.game.ClientboundTabListPacket(headerComponent, footerComponent)
            connection.send(packet)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
