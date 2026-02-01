package me.jordanfails.unify.nms.v1_9_R2

import me.jordanfails.unify.exception.InvalidOutputException
import me.jordanfails.unify.nms.NMSHandler
import net.minecraft.server.v1_9_R2.ChatComponentText
import net.minecraft.server.v1_9_R2.ContainerChest
import net.minecraft.server.v1_9_R2.IInventory
import net.minecraft.server.v1_9_R2.MinecraftServer
import net.minecraft.server.v1_9_R2.PacketPlayOutOpenWindow
import net.minecraft.server.v1_9_R2.PacketPlayOutScoreboardTeam
import net.minecraft.server.v1_9_R2.PacketPlayOutTitle
import org.bukkit.Bukkit
import org.bukkit.craftbukkit.v1_9_R2.entity.CraftPlayer
import org.bukkit.craftbukkit.v1_9_R2.inventory.CraftInventory
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

class NMSHandler_v1_9_R2 : NMSHandler {

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

    override fun getServerVersion(): String {
        return Bukkit.getBukkitVersion().split("-").first()
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
}
