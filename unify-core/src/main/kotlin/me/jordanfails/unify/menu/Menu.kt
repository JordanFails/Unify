package me.jordanfails.unify.menu

import com.cryptomorin.xseries.XMaterial
import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.nms.NMSHandlerFactory
import me.jordanfails.unify.utils.XSupport
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

abstract class Menu {

    var buttons: ConcurrentHashMap<Int, Button> = ConcurrentHashMap()
    var autoUpdate: Boolean = false
    var autoUpdateInterval: Long = 500L
    var updateAllButtons: Boolean = false  // When true, auto-update refreshes ALL buttons, not just animated ones
    var animated: Boolean = false
    var updateAfterClick: Boolean = false
    var keepBottomMechanics: Boolean = true
    var placeholder: Boolean = false
    var nonCancelling: Boolean = false
    var async: Boolean = false
    var manualClose: Boolean = true
    var closed: Boolean = false
    private var staticTitle: String
    private var autoUpdateTask: BukkitTask? = null
    var fillBackground: Boolean = false
    var filledMaterial: Material = XSupport.resolve(XMaterial.GRAY_STAINED_GLASS_PANE)


    // Animation manager for this menu
//    val animationManager: MenuAnimationManager by lazy { MenuAnimationManager(this) }

    constructor() {
        staticTitle = "&7Default Menu Title"
    }

    constructor(title: String) {
        staticTitle = title
    }

    abstract fun getButtons(player: Player): Map<Int, Button>

    open fun getTitle(player: Player): String {
        return staticTitle
    }

    open fun onOpen(player: Player) {}

    /**
     * Called when a player clicks on a background/filler slot (not a registered button).
     * Override this to handle clicks on empty spaces in the menu.
     * @return true if the click was handled and should be cancelled, false otherwise
     */
    open fun onBackgroundClick(player: Player, slot: Int, clickType: ClickType): Boolean = false

    open fun onClose(player: Player, manualClose: Boolean) {
        stopAutoUpdate()
//        animationManager.stopAllAnimations()
    }

    internal fun createInventory(player: Player): Inventory {
        var title = ChatColor.translateAlternateColorCodes('&', getTitle(player))
        if (title.length > 32) {
            title = title.take(31)
        }

        val invButtons = getButtons(player)
        var size = size(invButtons)

        val minSize = getMinSize()
        if (minSize != -1) {
            if (minSize > size) {
                size = minSize
            }
        }

        val inv = Bukkit.createInventory(null, size, title)
        // Fill background if enabled
        if (fillBackground) {
            val filler = Button.placeholder(filledMaterial, 15.toByte(), " ")
            for (slot in 0 until size) {
                buttons[slot] = filler
                inv.setItem(slot, filler.getButtonItem(player))
            }
        }

        for (buttonEntry in invButtons.entries) {
            if (buttonEntry.key >= size) {
                continue
            }

            buttons[buttonEntry.key] = buttonEntry.value
            inv.setItem(buttonEntry.key, buttonEntry.value.getButtonItem(player))
        }

        if (placeholder) {
            val placeholder = Button.placeholder(XMaterial.GRAY_STAINED_GLASS_PANE, 15.toByte(), " ")

            for (index in 0 until size(invButtons)) {
                if (invButtons[index] == null) {
                    buttons[index] = placeholder
                    inv.setItem(index, placeholder.getButtonItem(player))
                }
            }
        }

        return inv
    }

    fun updateTitle(player: Player, newTitle: String) {
        val openMenu = currentlyOpenedMenus[player.uniqueId] ?: return
        if (openMenu != this) return
        val title = ChatColor.translateAlternateColorCodes('&', newTitle)
        NMSHandlerFactory.getHandler()?.updateMenuTitle(player, title)
    }


    fun openMenu(player: Player) {
        if (async) {
            Bukkit.getScheduler().runTaskAsynchronously(UnifyCore.instance, Runnable {
                try {
                    asyncLoadResources(player) { successfulLoad ->
                        if (successfulLoad) {
                            val inv = createInventory(player)

                            try {
                                openCustomInventory(player, inv)
                            } catch (ex: Exception) {
                                ex.printStackTrace()
                            }
                        } else {
                            player.sendMessage("${ChatColor.RED}Couldn't load menu...")
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    player.sendMessage("${ChatColor.RED}Couldn't load menu...")
                }
            })
        } else {
            val inv = createInventory(player)

            try {
                openCustomInventory(player, inv)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    fun open(player: Player) {
        openMenu(player)
    }

    private fun getWindowType(size: Int): String {
        return "minecraft:chest"
    }

    fun update(player: Player) {
        // set open menu reference to this menu
        currentlyOpenedMenus[player.uniqueId] = this

        // call abstract onOpen
        closed = false
        onOpen(player)

        // Start auto-update for animated buttons
        if (autoUpdate && autoUpdateTask == null) {
            startAutoUpdate(player)
        }
    }

    private fun startAutoUpdate(player: Player) {
        autoUpdateTask = Bukkit.getScheduler().runTaskTimer(
            UnifyCore.instance,
            Runnable {
                if (currentlyOpenedMenus[player.uniqueId] != this) {
                    stopAutoUpdate()
                    return@Runnable
                }

                // Call preAutoUpdate hook
                preAutoUpdate()

                val inv = player.openInventory.topInventory

                if (updateAllButtons) {
                    // Update ALL registered buttons (for menus that need full refresh)
                    buttons.forEach { (slot, button) ->
                        if (slot < inv.size) {
                            inv.setItem(slot, button.getButtonItem(player))
                        }
                    }
                } else {
                    // Only update animated buttons
                    buttons.forEach { (slot, button) ->
                        if (button.isAnimated()) {
                            val currentTime = System.currentTimeMillis()
                            val interval = button.getAnimationInterval() * 50 // Convert ticks to ms

                            if (currentTime - button.lastAnimation >= interval) {
                                button.preAnimationUpdate()
                                button.lastAnimation = currentTime

                                // Update the item in inventory
                                if (slot < inv.size) {
                                    inv.setItem(slot, button.getButtonItem(player))
                                }
                            }
                        }
                    }
                }

                player.updateInventory()
            },
            0L,
            getAutoUpdateTicks()
        )
    }

    private fun stopAutoUpdate() {
        autoUpdateTask?.cancel()
        autoUpdateTask = null
    }

    /**
     * Manually refresh all buttons in the inventory without reopening the menu.
     * Call this when you need to update button displays on-demand.
     */
    fun refreshButtons(player: Player) {
        val inv = player.openInventory?.topInventory ?: return
        if (currentlyOpenedMenus[player.uniqueId] != this) return
        
        buttons.forEach { (slot, button) ->
            if (slot < inv.size) {
                inv.setItem(slot, button.getButtonItem(player))
            }
        }
        player.updateInventory()
    }

    open fun size(buttons: Map<Int, Button>): Int {
        var highest = 0
        for (buttonValue in buttons.keys) {
            if (buttonValue > highest) {
                highest = buttonValue
            }
        }
        return (ceil((highest + 1) / 9.0) * 9.0).toInt()
    }

    open fun getMinSize(): Int {
        return -1
    }

    fun getSlot(x: Int, y: Int): Int {
        return 9 * y + x
    }

    open fun asyncLoadResources(player: Player, callback: (Boolean) -> Unit) {}

    open fun acceptsInsertedItem(player: Player, itemStack: ItemStack, slot: Int): Boolean {
        return false
    }

    open fun acceptsShiftClickedItem(player: Player, itemStack: ItemStack): Boolean {
        return false
    }

    open fun acceptsDraggedItems(player: Player, items: Map<Int, ItemStack>): Boolean {
        return false
    }

    open fun getAutoUpdateTicks(): Long {
        return autoUpdateInterval
    }

    // Add this method to check if a slot contains a moveable item
    fun isSlotMoveable(slot: Int): Boolean {
        return buttons[slot]?.isMoveable() ?: false
    }

    // Add this method to check if a slot contains a removable item
    fun isSlotRemovable(slot: Int): Boolean {
        return buttons[slot]?.isRemovable() ?: false
    }


    open fun preAutoUpdate() {}

    private fun openCustomInventory(player: Player, inv: Inventory) {
        val title = ChatColor.translateAlternateColorCodes('&', getTitle(player))
        val nms = NMSHandlerFactory.getHandler()

        // Version-safe inventory open
        nms?.openMenuInventory(player, inv, title) ?: player.openInventory(inv)

        update(player)
    }

    companion object {
        @JvmStatic
        var currentlyOpenedMenus: MutableMap<UUID, Menu> = ConcurrentHashMap()
    }

}