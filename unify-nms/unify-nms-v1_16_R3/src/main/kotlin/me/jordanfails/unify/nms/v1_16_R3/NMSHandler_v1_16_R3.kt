package me.jordanfails.unify.nms.v1_16_R3

import me.jordanfails.unify.nms.NMSHandler
import net.minecraft.server.v1_16_R3.MinecraftServer
import org.bukkit.Bukkit
import org.bukkit.block.BlockState
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable

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
        return Bukkit.getServer().javaClass.`package`.name.substringAfterLast(".")
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
}
