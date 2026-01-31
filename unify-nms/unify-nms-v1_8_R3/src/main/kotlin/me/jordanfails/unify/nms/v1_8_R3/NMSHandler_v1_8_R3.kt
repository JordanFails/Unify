package me.jordanfails.unify.nms.v1_8_R3

import me.jordanfails.unify.exception.InvalidOutputException
import me.jordanfails.unify.nms.NMSHandler
import net.minecraft.server.v1_8_R3.ChatComponentText
import net.minecraft.server.v1_8_R3.ContainerChest
import net.minecraft.server.v1_8_R3.IInventory
import net.minecraft.server.v1_8_R3.MinecraftServer
import net.minecraft.server.v1_8_R3.PacketPlayOutOpenWindow
import net.minecraft.server.v1_8_R3.PacketPlayOutTitle
import org.bukkit.Bukkit
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftInventory
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

@Suppress("unused")
class NMSHandler_v1_8_R3 : NMSHandler {

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
        val pkg = Bukkit.getServer().javaClass.`package`.name
        return pkg.substringAfterLast('.') // "v1_8_R3"
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

    override fun setItemDurability(item: ItemStack, durability: Int) {
        item.durability = durability.toShort()
    }

    override fun setItemData(item: ItemStack, data: Short) {
        // Same field in 1.8
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
        val container = ContainerChest(handle.inventory, (inventory as CraftInventory).inventory, handle)
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

}
