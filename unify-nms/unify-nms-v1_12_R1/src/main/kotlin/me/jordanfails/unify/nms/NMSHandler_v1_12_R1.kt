package me.jordanfails.unify.nms

import net.minecraft.server.v1_12_R1.ChatComponentText
import net.minecraft.server.v1_12_R1.ContainerChest
import net.minecraft.server.v1_12_R1.MinecraftServer
import net.minecraft.server.v1_12_R1.PacketPlayOutOpenWindow
import org.bukkit.Bukkit
import org.bukkit.block.BlockState
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftInventory
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

class NMSHandler_v1_12_R1 : NMSHandler {
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
        return Bukkit.getServer().javaClass.`package`.name.substringAfterLast(".")
    }

    override fun getPing(player: Player): Int {
        return (player as CraftPlayer).handle.playerConnection.player.ping
    }

    override fun getTPS(): DoubleArray {
        return MinecraftServer.getServer().recentTps
    }

    override fun setItemDurability(item: ItemStack, durability: Int) {
        item.durability = durability.toShort()
    }

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
        val container = ContainerChest(handle.inventory, (inventory as CraftInventory).inventory, handle)
        container.windowId = windowId
        handle.activeContainer = container
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
        val packet = PacketPlayOutOpenWindow(container.windowId, "minecraft:container", safeTitle, container.slots.size)
        handle.playerConnection.sendPacket(packet)
        handle.updateInventory(container)
    }

    override fun refreshMenuInventory(player: Player) {
        (player as CraftPlayer).handle.updateInventory(player.handle.activeContainer)
    }

    override fun isCustomInventory(inventory: Inventory): Boolean {
        val holder = inventory.holder
        return holder == null || holder is Player || holder !is BlockState
    }
}
