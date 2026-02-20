package me.jordanfails.unify.nms.v1_16_R3

import me.jordanfails.unify.bossbar.BossBarColor
import me.jordanfails.unify.bossbar.BossBarStyle
import me.jordanfails.unify.bossbar.UnifyBossBar
import me.jordanfails.unify.hologram.HologramLine
import me.jordanfails.unify.hologram.UnifyHologram
import me.jordanfails.unify.nms.NMSHandler
import net.minecraft.server.v1_16_R3.*
import org.bukkit.Bukkit
import org.bukkit.block.BlockState
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.craftbukkit.v1_16_R3.CraftWorld
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer
import org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemStack
import org.bukkit.craftbukkit.v1_16_R3.util.CraftChatMessage
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import java.util.UUID

class NMSHandler_v1_16_R3 : NMSHandler {
    override fun sendTitle(
        player: Player,
        title: String,
        subtitle: String?,
        fadeIn: Int,
        stay: Int,
        fadeOut: Int
    ) {
        player.sendTitle(
            title, subtitle, fadeIn,
            stay, fadeOut
        )
    }

    override fun getServerVersion(): String {
        return Bukkit.getBukkitVersion().split("-").first()
    }

    override fun getPing(player: Player): Int {
        return (player as CraftPlayer).handle.playerConnection.player.ping
    }

    override fun getTPS(): DoubleArray {
        return MinecraftServer.getServer().recentTps
    }


    override fun setItemDurability(item: ItemStack, durability: Int) {
        val meta = item.itemMeta
        if (meta is Damageable) {
            meta.damage = durability
            item.itemMeta = meta
        }
    }

    override fun setItemData(item: ItemStack, data: Short) {
        // Does nothing for modern versions — all submaterials are distinct Materials
    }

    override fun setItemUnbreakable(item: ItemStack, unbreakable: Boolean) {
        val meta = item.itemMeta ?: return
        meta.isUnbreakable = unbreakable
        item.itemMeta = meta
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
        setField(packet, "i", 1) // mode: 1 = remove team
        
        (viewer as CraftPlayer).handle.playerConnection.sendPacket(packet)
    }
    
    override fun sendNametagPacket(viewer: Player, target: Player, teamName: String, prefix: String, suffix: String) {
        val packet = PacketPlayOutScoreboardTeam()
        val safeName = teamName.take(16)
        
        setField(packet, "a", safeName) // team name
        setField(packet, "b", ChatComponentText(safeName)) // display name
        setField(packet, "c", ChatComponentText(prefix.take(16))) // prefix
        setField(packet, "d", ChatComponentText(suffix.take(16))) // suffix
        setField(packet, "e", "always") // nameTagVisibility
        setField(packet, "f", "always") // collisionRule
        setField(packet, "g", 15) // color (15 = white/reset)
        
        @Suppress("UNCHECKED_CAST")
        val players = getField(packet, "h") as MutableCollection<String>
        players.add(target.name)
        
        setField(packet, "i", 0) // mode: 0 = create
        setField(packet, "j", 0) // friendly fire flags
        
        (viewer as CraftPlayer).handle.playerConnection.sendPacket(packet)
    }

    private fun sendNametagPacket(viewer: Player, target: Player, visibility: String, mode: Int) {
        val packet = PacketPlayOutScoreboardTeam()
        val teamName = getTeamName(target)
        
        setField(packet, "a", teamName) // team name
        setField(packet, "b", ChatComponentText(teamName)) // display name (IChatBaseComponent in 1.16)
        setField(packet, "c", ChatComponentText("")) // prefix
        setField(packet, "d", ChatComponentText("")) // suffix
        setField(packet, "e", visibility) // nameTagVisibility
        setField(packet, "f", "always") // collisionRule
        setField(packet, "g", 15) // color (15 = white/reset)
        
        @Suppress("UNCHECKED_CAST")
        val players = getField(packet, "h") as MutableCollection<String>
        players.add(target.name)
        
        setField(packet, "i", mode) // mode: 0 = create, 1 = remove, 2 = update, 3 = add players, 4 = remove players
        setField(packet, "j", 0) // friendly fire flags
        
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
    
    // 1.16+ uses components - effectively unlimited (32767 is protocol max)
    override fun getScoreboardLineLimit(): Int = 32767
    override fun getTeamPrefixLimit(): Int = 32767
    
    override fun sendScoreboardObjective(player: Player, name: String, title: String, mode: Int) {
        try {
            val connection = (player as CraftPlayer).handle.playerConnection
            val titleComponent = IChatBaseComponent.ChatSerializer.a("{\"text\":\"$title\"}")
            val dummyScoreboard = Scoreboard()
            val objective = dummyScoreboard.registerObjective(
                name,
                IScoreboardCriteria.DUMMY,
                titleComponent,
                IScoreboardCriteria.EnumScoreboardHealthDisplay.INTEGER
            )
            
            val packet = PacketPlayOutScoreboardObjective(objective, mode)
            connection.sendPacket(packet)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun sendScoreboardDisplaySlot(player: Player, objectiveName: String, slot: Int) {
        try {
            val connection = (player as CraftPlayer).handle.playerConnection
            val packet = PacketPlayOutScoreboardDisplayObjective(slot, null)
            
            setField(packet, "b", objectiveName)
            
            connection.sendPacket(packet)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun sendScoreboardScore(player: Player, objectiveName: String, entry: String, score: Int, mode: Int) {
        try {
            val connection = (player as CraftPlayer).handle.playerConnection
            val action = if (mode == 0) ScoreboardServer.Action.CHANGE else ScoreboardServer.Action.REMOVE
            val packet = PacketPlayOutScoreboardScore(action, objectiveName, entry, score)
            
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
    
    // --- Hologram Implementation (1.16 uses NMS ArmorStands) ---
    private val playerHologramEntities = mutableMapOf<UUID, MutableMap<UUID, List<Int>>>()
    private var entityIdCounter = 1000000
    
    override fun showHologram(player: Player, hologram: UnifyHologram) {
        spawnHologram(player, hologram)
    }
    
    override fun hideHologram(player: Player, hologram: UnifyHologram) {
        val entityIds = playerHologramEntities[player.uniqueId]?.remove(hologram.uuid) ?: return
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
            var currentY = hologram.location.y
            
            val world = (player.world as CraftWorld).handle
            val connection = (player as CraftPlayer).handle.playerConnection
            
            for (line in lines) {
                val entityId = entityIdCounter++
                entityIds.add(entityId)
                
                when (line) {
                    is HologramLine.Text -> {
                        val armorStand = EntityArmorStand(world, hologram.location.x, currentY, hologram.location.z)
                        armorStand.e(entityId)
                        armorStand.customName = CraftChatMessage.fromStringOrNull(me.jordanfails.unify.utils.CC.translate(line.text))
                        armorStand.customNameVisible = true
                        armorStand.isInvisible = true
                        armorStand.isNoGravity = true
                        armorStand.isSmall = true
                        armorStand.isMarker = true
                        
                        val spawnPacket = PacketPlayOutSpawnEntityLiving(armorStand)
                        connection.sendPacket(spawnPacket)
                        
                        val metaPacket = PacketPlayOutEntityMetadata(entityId, armorStand.dataWatcher, true)
                        connection.sendPacket(metaPacket)
                        currentY -= 0.25
                    }
                    is HologramLine.Item -> {
                        val nmsItem = CraftItemStack.asNMSCopy(line.itemStack)
                        val itemEntity = EntityItem(world, hologram.location.x, currentY, hologram.location.z, nmsItem)
                        itemEntity.e(entityId)
                        itemEntity.setNoGravity(true)
                        
                        val spawnPacket = PacketPlayOutSpawnEntity(itemEntity)
                        connection.sendPacket(spawnPacket)
                        
                        val metaPacket = PacketPlayOutEntityMetadata(entityId, itemEntity.dataWatcher, true)
                        connection.sendPacket(metaPacket)
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
            val connection = (player as CraftPlayer).handle.playerConnection
            
            for (i in lines.indices) {
                val entityId = entityIds[i]
                val line = lines[i]
                
                when (line) {
                    is HologramLine.Text -> {
                        val armorStand = EntityArmorStand(world, hologram.location.x, currentY, hologram.location.z)
                        armorStand.e(entityId)
                        armorStand.customName = CraftChatMessage.fromStringOrNull(me.jordanfails.unify.utils.CC.translate(line.text))
                        armorStand.customNameVisible = true
                        armorStand.isInvisible = true
                        armorStand.isMarker = true
                        
                        val metaPacket = PacketPlayOutEntityMetadata(entityId, armorStand.dataWatcher, true)
                        connection.sendPacket(metaPacket)
                        
                        val teleportPacket = PacketPlayOutEntityTeleport(armorStand)
                        connection.sendPacket(teleportPacket)
                        currentY -= 0.25
                    }
                    is HologramLine.Item -> {
                        val nmsItem = CraftItemStack.asNMSCopy(line.itemStack)
                        val itemEntity = EntityItem(world, hologram.location.x, currentY, hologram.location.z, nmsItem)
                        itemEntity.e(entityId)
                        itemEntity.setNoGravity(true)
                        
                        val metaPacket = PacketPlayOutEntityMetadata(entityId, itemEntity.dataWatcher, true)
                        connection.sendPacket(metaPacket)
                        
                        val teleportPacket = PacketPlayOutEntityTeleport(itemEntity)
                        connection.sendPacket(teleportPacket)
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
    
    override fun sendTabHeaderFooter(player: Player, header: String, footer: String) {
        try {
            val connection = (player as CraftPlayer).handle.playerConnection
            val headerComponent = IChatBaseComponent.ChatSerializer.a("{\"text\":\"${me.jordanfails.unify.utils.CC.translate(header)}\"}")!!
            val footerComponent = IChatBaseComponent.ChatSerializer.a("{\"text\":\"${me.jordanfails.unify.utils.CC.translate(footer)}\"}")!!
            val packet = PacketPlayOutPlayerListHeaderFooter()
            setField(packet, "header", headerComponent)
            setField(packet, "footer", footerComponent)
            connection.sendPacket(packet)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
