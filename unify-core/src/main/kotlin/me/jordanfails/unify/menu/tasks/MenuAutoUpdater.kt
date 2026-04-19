package me.jordanfails.unify.menu.tasks

import me.jordanfails.unify.menu.Menu
import me.jordanfails.unify.nms.NMSHandler
import me.jordanfails.unify.nms.NMSHandlerFactory
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.iterator

object MenuAutoUpdater : Runnable, Listener {

    private val updateTimestamps: MutableMap<UUID, Long> = ConcurrentHashMap()
//    private val nms: NMSHandler? = NMSHandlerFactory.getHandler()

    override fun run() {
        for ((uuid, openMenu) in Menu.currentlyOpenedMenus.entries) {
            try {
                val player = Bukkit.getPlayer(uuid)
                if (player == null || !player.isOnline) {
                    Menu.currentlyOpenedMenus.remove(uuid)
                    continue
                }

                if (openMenu.closed) {
                    continue
                }

                if (openMenu.autoUpdate) {
                    updateTimestamps.putIfAbsent(player.uniqueId, System.currentTimeMillis())

                    if (System.currentTimeMillis() - updateTimestamps[player.uniqueId]!! >= openMenu.getAutoUpdateTicks()) {
                        openMenu.preAutoUpdate()
                        updateTimestamps[player.uniqueId] = System.currentTimeMillis()

                        // Update items in-place instead of reopening the menu (prevents flickering)
                        val openInventory = player.openInventory.topInventory ?: continue

                        // Update all registered buttons in-place
                        for ((slot, button) in openMenu.buttons) {
                            if (slot < openInventory.size) {
                                openInventory.setItem(slot, button.getButtonItem(player))
                            }
                        }
                        player.updateInventory()
                    }
                } else if (openMenu.animated) {
                    val openInventory = player.openInventory.topInventory ?: continue
                    val nms = NMSHandlerFactory.getHandler() ?: continue
                    if(!nms.isCustomInventory(openInventory)) continue

                    var updateInventory = false
                    for ((slot, button) in openMenu.buttons) {
                        if (slot >= openInventory.size - 1) {
                            continue
                        }

                        if (button.isAnimated()) {
                            if (System.currentTimeMillis() - button.lastAnimation >= button.getAnimationInterval()) {
                                updateInventory = true
                                button.preAnimationUpdate()
                                button.lastAnimation = System.currentTimeMillis()
                                openInventory.setItem(slot, button.getButtonItem(player))
                            }
                        }
                    }

                    if (updateInventory) {
                        player.updateInventory()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @EventHandler
    fun onPlayerQuitEvent(event: PlayerQuitEvent) {
        updateTimestamps.remove(event.player.uniqueId)
    }

}