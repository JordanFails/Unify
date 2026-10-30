package me.jordanfails.unify.menu

import com.cryptomorin.xseries.XMaterial
import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.menu.anvil.AnvilSession
import me.jordanfails.unify.nms.LegacyColorDataType
import me.jordanfails.unify.nms.LegacyItemColor
import me.jordanfails.unify.nms.NMSHandlerFactory
import me.jordanfails.unify.nms.ServerVersion
import me.jordanfails.unify.utils.XSupport
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil

abstract class Menu {

    var buttons: ConcurrentHashMap<Int, Button> = ConcurrentHashMap()
    var autoUpdate: Boolean = false
    var autoUpdateInterval: Long = 10L
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
    private val autoUpdateTasks = mutableMapOf<UUID, BukkitTask>()
    private val inventories = mutableMapOf<UUID, Inventory>()
    private val closing = mutableSetOf<UUID>()

    fun ownsInventory(inventory: Inventory): Boolean = inventories.containsValue(inventory)

    internal fun beginClose(player: Player): Boolean = closing.add(player.uniqueId)

    internal fun finishClose(player: Player) {
        stopAutoUpdate(player.uniqueId)
        inventories.remove(player.uniqueId)
        closing.remove(player.uniqueId)
        closed = inventories.isEmpty()
    }
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
        stopAutoUpdate(player.uniqueId)
//        animationManager.stopAllAnimations()
    }

    internal fun createInventory(player: Player): Inventory {
        var title = ChatColor.translateAlternateColorCodes('&', getTitle(player))
        // 1.8 can't handle titles longer than 32 characters
        if (title.length > 32 && NMSHandlerFactory.getHandler()?.getServerVersion()?.isBelow(ServerVersion.v1_9_R2) != false) {
            title = title.take(32)
        }

        val invButtons = getButtons(player).toMap()
        buttons.clear()
        var size = size(invButtons)

        val minSize = getMinSize()
        if (minSize != -1) {
            if (minSize > size) {
                size = minSize
            }
        }

        require(size in 9..54 && size % 9 == 0) { "Menu size must be a multiple of nine between 9 and 54: $size" }
        val inv = Bukkit.createInventory(null, size, title)
        // Fill background if enabled
        if (fillBackground) {
            val filler = Button.placeholder(
                filledMaterial,
                NMSHandlerFactory.getHandler()?.getLegacyColorData(LegacyItemColor.GRAY, LegacyColorDataType.BLOCK)
                    ?: LegacyItemColor.GRAY.blockData,
                " "
            )
            for (slot in 0 until size) {
                buttons[slot] = filler
                inv.setItem(slot, filler.getButtonItem(player))
            }
        }

        for ((key, value) in invButtons) {
            if (key !in 0 until size) {
                continue
            }

            buttons[key] = value
            inv.setItem(key, value.getButtonItem(player))
        }

        if (placeholder) {
            val placeholder = Button.placeholder(
                XMaterial.GRAY_STAINED_GLASS_PANE,
                NMSHandlerFactory.getHandler()?.getLegacyColorData(LegacyItemColor.GRAY, LegacyColorDataType.BLOCK)
                    ?: LegacyItemColor.GRAY.blockData,
                " "
            )

            for (index in 0 until size) {
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
        var title = ChatColor.translateAlternateColorCodes('&', newTitle)
        if (title.length > 32 && NMSHandlerFactory.getHandler()?.getServerVersion()?.isBelow(ServerVersion.v1_9_R2) != false) {
            title = title.take(32)
        }
        val view = player.openInventory
        if (view.title == title) return
        try {
            InventoryView::class.java.getMethod("setTitle", String::class.java).invoke(view, title)
        } catch (_: NoSuchMethodException) {
            val nms = NMSHandlerFactory.getHandler()
            if (nms?.getServerVersion()?.isBelow(ServerVersion.v1_16_R3) == true) {
                nms.updateMenuTitle(player, title)
            }
        }
    }


    fun openMenu(player: Player) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(UnifyCore.instance, Runnable { openMenu(player) })
            return
        }
        if (!player.isOnline) return
        val request = openSequence.incrementAndGet()
        pendingOpens[player.uniqueId] = request
        val expectedInventory = player.openInventory.topInventory
        fun complete(success: Boolean) {
            val action = Runnable {
                if (pendingOpens[player.uniqueId] != request || !player.isOnline) return@Runnable
                pendingOpens.remove(player.uniqueId, request)
                if (async && player.openInventory.topInventory != expectedInventory) return@Runnable
                if (!success) {
                    player.sendMessage("${ChatColor.RED}Couldn't load menu...")
                    return@Runnable
                }
                try {
                    openCustomInventory(player, createInventory(player))
                } catch (error: Exception) {
                    UnifyCore.instance.logger.log(java.util.logging.Level.SEVERE, "Could not open menu", error)
                    player.sendMessage("${ChatColor.RED}Couldn't open menu...")
                }
            }
            if (Bukkit.isPrimaryThread()) action.run()
            else Bukkit.getScheduler().runTask(UnifyCore.instance, action)
        }
        if (async) {
            Bukkit.getScheduler().runTaskAsynchronously(UnifyCore.instance, Runnable {
                try {
                    asyncLoadResources(player, ::complete)
                } catch (error: Exception) {
                    UnifyCore.instance.logger.log(java.util.logging.Level.SEVERE, "Could not load menu resources", error)
                    complete(false)
                }
            })
        } else if (player.uniqueId in closing) {
            Bukkit.getScheduler().runTask(UnifyCore.instance, Runnable { complete(true) })
        } else complete(true)
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
        manualClose = true
        inventories[player.uniqueId] = player.openInventory.topInventory
        try {
            onOpen(player)
        } catch (error: Exception) {
            player.closeInventory()
            finishClose(player)
            currentlyOpenedMenus.remove(player.uniqueId, this)
            throw error
        }

        // Start auto-update for animated buttons
        if (autoUpdate && player.uniqueId !in autoUpdateTasks) {
            startAutoUpdate(player)
        }
    }

    private fun startAutoUpdate(player: Player) {
        autoUpdateTasks[player.uniqueId] = Bukkit.getScheduler().runTaskTimer(
            UnifyCore.instance,
            Runnable {
                if (!player.isOnline || currentlyOpenedMenus[player.uniqueId] !== this || !ownsInventory(player.openInventory.topInventory)) {
                    stopAutoUpdate(player.uniqueId)
                    return@Runnable
                }

                // Call preAutoUpdate hook
                preAutoUpdate()
                if (currentlyOpenedMenus[player.uniqueId] !== this || closed) return@Runnable

                val inv = player.openInventory.topInventory

                val now = System.currentTimeMillis()
                for ((slot, button) in buttons) {
                    if (slot !in 0 until inv.size) continue
                    val animate = button.isAnimated() && now - button.lastAnimation >= button.getAnimationInterval() * 50L
                    if (animate) {
                        button.preAnimationUpdate()
                        button.lastAnimation = now
                    }
                    if (updateAllButtons || animate) {
                        val item = button.getButtonItem(player)
                        if (inv.getItem(slot) != item) inv.setItem(slot, item)
                    }
                }

            },
            0L,
            getAutoUpdateTicks().coerceAtLeast(1L)
        )
    }

    private fun stopAutoUpdate(uuid: UUID) {
        autoUpdateTasks.remove(uuid)?.cancel()
    }

    /**
     * Manually refresh all buttons in the inventory without reopening the menu.
     * Call this when you need to update button displays on-demand.
     */
    fun refreshButtons(player: Player) {
        val inv = player.openInventory?.topInventory ?: return
        if (currentlyOpenedMenus[player.uniqueId] !== this || !ownsInventory(inv)) return

        buttons.forEach { (slot, button) ->
            if (slot in 0 until inv.size) {
                val item = button.getButtonItem(player)
                if (inv.getItem(slot) != item) inv.setItem(slot, item)
            }
        }
    }

    /**
     * Calculates the inventory size from the highest button slot.
     *
     * **For bordered menus** ([BorderedMenu], [PaginatedBorderedMenu]) do
     * NOT override this to control the menu size — override [getMinSize] or
     * [BorderedMenu.getMenuSize]/[PaginatedBorderedMenu.getMenuSize] instead.
     * The border calculation reads those methods directly and is unaffected
     * by this one.
     */
    open fun size(buttons: Map<Int, Button>): Int {
        var highest = 0
        for (buttonValue in buttons.keys) {
            if (buttonValue > highest) {
                highest = buttonValue
            }
        }
        return (ceil((highest + 1) / 9.0) * 9.0).toInt()
    }

    /**
     * Minimum inventory size. Return -1 for no minimum.
     *
     * Override this (not [size]) to enforce a minimum menu size in bordered
     * menus — [BorderedMenu.getMenuSize] and [PaginatedBorderedMenu.getMenuSize]
     * both consult this method.
     */
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
        val current = currentlyOpenedMenus[player.uniqueId]
        if (current === this && ownsInventory(player.openInventory.topInventory)) {
            check(inv.size == player.openInventory.topInventory.size) { "Close this menu before changing its size" }
            player.openInventory.topInventory.contents = inv.contents
            updateTitle(player, getTitle(player))
            return
        }
        AnvilSession.closeIfOpen(player, sendClosePacket = true)
        val title = ChatColor.translateAlternateColorCodes('&', getTitle(player))
        NMSHandlerFactory.getHandler()?.openMenuInventory(player, inv, title) ?: player.openInventory(inv)
        // InventoryOpenEvent can reject or redirect an open.
        if (player.openInventory.topInventory != inv) return
        update(player)
    }

    companion object {
        private val openSequence = AtomicLong()
        private val pendingOpens = ConcurrentHashMap<UUID, Long>()

        internal fun cancelPendingOpen(uuid: UUID) { pendingOpens.remove(uuid) }

        @JvmStatic
        var currentlyOpenedMenus: MutableMap<UUID, Menu> = ConcurrentHashMap()
    }

}
