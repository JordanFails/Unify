package me.jordanfails.unify.menu.tasks

import me.jordanfails.unify.menu.Menu
import org.bukkit.Bukkit
import java.util.logging.Level
import me.jordanfails.unify.UnifyCore

/** Auto-updating menus own their task; this loop only animates the remaining menus. */
object MenuAutoUpdater : Runnable {
    override fun run() {
        for ((uuid, menu) in Menu.currentlyOpenedMenus.entries) {
            if (menu.closed || menu.autoUpdate || !menu.animated) continue
            val player = Bukkit.getPlayer(uuid) ?: continue
            val inventory = player.openInventory.topInventory
            if (!menu.ownsInventory(inventory)) continue
            try {
                val now = System.currentTimeMillis()
                for ((slot, button) in menu.buttons) {
                    if (slot !in 0 until inventory.size || !button.isAnimated()) continue
                    if (now - button.lastAnimation < button.getAnimationInterval() * 50L) continue
                    button.preAnimationUpdate()
                    button.lastAnimation = now
                    val item = button.getButtonItem(player)
                    if (inventory.getItem(slot) != item) inventory.setItem(slot, item)
                }
            } catch (error: Exception) {
                UnifyCore.instance.logger.log(Level.SEVERE, "Could not animate menu", error)
            }
        }
    }
}
