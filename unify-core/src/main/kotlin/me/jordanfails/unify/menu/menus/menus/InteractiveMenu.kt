package me.jordanfails.unify.menu.menus.menus

import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.menu.Menu
import me.jordanfails.unify.menu.buttons.InteractiveSlotButton
import me.jordanfails.unify.menu.menus.SlotBehavior
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.iterator

/**
 * A menu that supports interactive slots where players can place/take items.
 *
 * Define slot behaviors by overriding:
 * - [getInteractiveSlots] - Slots where players can both place AND take items
 * - [getInputOnlySlots] - Slots where players can only place items (deposit)
 * - [getOutputOnlySlots] - Slots where players can only take items (withdraw)
 *
 * All other slots are treated as FIXED (buttons that cannot be moved).
 *
 * Example usage:
 * ```kotlin
 * class MyStorageMenu : InteractiveMenu("&8Storage") {
 *     override fun getInteractiveSlots() = setOf(10, 11, 12, 13)
 *     override fun getInputOnlySlots() = setOf(14) // Deposit slot
 *     override fun getOutputOnlySlots() = setOf(16) // Reward slot
 *
 *     override fun onSlotChange(player: Player, slot: Int, oldItem: ItemStack?, newItem: ItemStack?) {
 *         // React to item changes (e.g., save to database)
 *     }
 *
 *     override fun getButtons(player: Player): Map<Int, CubedButton> {
 *         return mapOf(
 *             0 to GlassButton(), // Fixed decoration
 *             8 to CloseButton()  // Fixed close button
 *         )
 *     }
 * }
 * ```
 */
abstract class InteractiveMenu : Menu {

    /** Tracks items in interactive slots */
    val slotContents: MutableMap<Int, ItemStack?> = hashMapOf()

    /** Combined slot behaviors computed from the override methods */
    private val slotBehaviors: MutableMap<Int, SlotBehavior> = ConcurrentHashMap()

    /** Whether to return items to the player when the menu closes. Default is false. */
    var returnItemsOnClose: Boolean = false

    constructor() : super()
    constructor(title: String) : super(title)

    /**
     * Returns slots where players can both PLACE and TAKE items.
     * Override to define fully interactive slots.
     */
    open fun getInteractiveSlots(): Set<Int> = emptySet()

    /**
     * Returns slots where players can only PLACE items (cannot take them out).
     * Useful for deposit/input mechanisms.
     */
    open fun getInputOnlySlots(): Set<Int> = emptySet()

    /**
     * Returns slots where players can only TAKE items (cannot place new items).
     * Useful for output/reward mechanisms.
     */
    open fun getOutputOnlySlots(): Set<Int> = emptySet()

    /**
     * Optional filter for what items a slot accepts.
     * Return a predicate function, or null to accept any item.
     *
     * @param slot The slot to get the filter for
     * @return A predicate that returns true if the item is allowed, or null to allow all items
     */
    open fun getAllowedItemFilter(slot: Int): ((ItemStack) -> Boolean)? = null

    /**
     * Called when a slot's contents change (item placed or removed).
     * Override to react to changes (e.g., save to database, update crafting result).
     *
     * @param player The player who made the change
     * @param slot The slot that changed
     * @param oldItem The previous item in the slot (null if was empty)
     * @param newItem The new item in the slot (null if now empty)
     */
    open fun onSlotChange(player: Player, slot: Int, oldItem: ItemStack?, newItem: ItemStack?) {}

    /**
     * Whether items should be returned to the player when the menu closes.
     * Override for conditional logic, or set [returnItemsOnClose] property.
     */
    open fun shouldReturnItemsOnClose(): Boolean = returnItemsOnClose

    /**
     * Gets the behavior for a specific slot.
     */
    fun getSlotBehavior(slot: Int): SlotBehavior {
        return slotBehaviors[slot] ?: SlotBehavior.FIXED
    }

    /**
     * Gets the item currently in a slot.
     */
    fun getSlotItem(slot: Int): ItemStack? = slotContents[slot]?.clone()

    /**
     * Sets the item in a slot programmatically.
     * Does NOT trigger [onSlotChange] - use for initialization.
     */
    fun setSlotItem(slot: Int, item: ItemStack?) {
        if (item == null || item.type == Material.AIR) {
            slotContents.remove(slot)
        } else {
            slotContents[slot] = item.clone()
        }
    }

    /**
     * Clears all slot contents.
     */
    fun clearSlotContents() {
        slotContents.clear()
    }

    /**
     * Checks if the given item is allowed in the slot based on the item filter.
     */
    fun isItemAllowedInSlot(slot: Int, item: ItemStack): Boolean {
        val filter = getAllowedItemFilter(slot)
        return filter?.invoke(item) ?: true
    }

    override fun onOpen(player: Player) {
        super.onOpen(player)
        // Compute slot behaviors from override methods
        computeSlotBehaviors()
    }

    override fun onClose(player: Player, manualClose: Boolean) {
        super.onClose(player, manualClose)

        // Return items to player if configured
        if (shouldReturnItemsOnClose()) {
            slotContents.values.filterNotNull().forEach { item ->
                if (item.type != Material.AIR) {
                    val leftover = player.inventory.addItem(item)
                    // Drop any items that don't fit
                    leftover.values.forEach { drop ->
                        player.world.dropItemNaturally(player.location, drop)
                    }
                }
            }
            slotContents.clear()
        }
    }

    private fun computeSlotBehaviors() {
        slotBehaviors.clear()

        getInteractiveSlots().forEach { slot ->
            slotBehaviors[slot] = SlotBehavior.INTERACTIVE
        }
        getInputOnlySlots().forEach { slot ->
            slotBehaviors[slot] = SlotBehavior.INPUT_ONLY
        }
        getOutputOnlySlots().forEach { slot ->
            slotBehaviors[slot] = SlotBehavior.OUTPUT_ONLY
        }
    }

    override fun getButtons(player: Player): Map<Int, Button> {
        computeSlotBehaviors()
        val buttons = mutableMapOf<Int, Button>()

        // Create buttons for all non-FIXED slots
        for ((slot, behavior) in slotBehaviors) {
            if (behavior != SlotBehavior.FIXED) {
                buttons[slot] = InteractiveSlotButton(
                    menu = this,
                    slot = slot,
                    behavior = behavior
                )
            }
        }

        // Add any fixed buttons defined in subclass (these override interactive slots if same position)
        buttons.putAll(getFixedButtons(player))

        return buttons
    }

    /**
     * Returns the fixed buttons (non-moveable action buttons).
     * Override this instead of getButtons() to add your fixed buttons.
     */
    open fun getFixedButtons(player: Player): Map<Int, Button> = emptyMap()

    override fun acceptsInsertedItem(player: Player, itemStack: ItemStack, slot: Int): Boolean {
        val behavior = getSlotBehavior(slot)

        // Only INTERACTIVE and INPUT_ONLY accept items
        if (behavior != SlotBehavior.INTERACTIVE && behavior != SlotBehavior.INPUT_ONLY) {
            return false
        }

        // Check item filter
        if (!isItemAllowedInSlot(slot, itemStack)) {
            return false
        }

        // Check if slot is empty
        val currentItem = getSlotItem(slot)
        if (currentItem != null && currentItem.type != Material.AIR) {
            return false
        }

        // Store the item and trigger callback
        val oldItem = getSlotItem(slot)
        setSlotItem(slot, itemStack)
        
        // Actually place the item in the inventory visually
        player.openInventory.topInventory.setItem(slot, itemStack.clone())
        
        onSlotChange(player, slot, oldItem, itemStack)
        return true
    }

    override fun acceptsShiftClickedItem(player: Player, itemStack: ItemStack): Boolean {
        // Find first empty slot that accepts input
        val inputSlots = getInteractiveSlots() + getInputOnlySlots()

        for (slot in inputSlots.sorted()) {
            val behavior = getSlotBehavior(slot)
            if (behavior != SlotBehavior.INTERACTIVE && behavior != SlotBehavior.INPUT_ONLY) {
                continue
            }

            // Check item filter
            if (!isItemAllowedInSlot(slot, itemStack)) {
                continue
            }

            // Check if slot is empty
            val currentItem = getSlotItem(slot)
            if (currentItem == null || currentItem.type == Material.AIR) {
                val oldItem = getSlotItem(slot)
                setSlotItem(slot, itemStack)
                
                // Actually place the item in the inventory visually
                player.openInventory.topInventory.setItem(slot, itemStack.clone())
                
                onSlotChange(player, slot, oldItem, itemStack)
                return true
            }
        }

        return false
    }

    override fun acceptsDraggedItems(player: Player, items: Map<Int, ItemStack>): Boolean {
        // Check if all dragged slots accept input
        for ((slot, item) in items) {
            val behavior = getSlotBehavior(slot)
            if (behavior != SlotBehavior.INTERACTIVE && behavior != SlotBehavior.INPUT_ONLY) {
                return false
            }

            if (!isItemAllowedInSlot(slot, item)) {
                return false
            }

            val currentItem = getSlotItem(slot)
            if (currentItem != null && currentItem.type != Material.AIR) {
                return false
            }
        }

        // Store all items and trigger callbacks
        for ((slot, item) in items) {
            val oldItem = getSlotItem(slot)
            setSlotItem(slot, item)
            
            // Actually place the item in the inventory visually
            player.openInventory.topInventory.setItem(slot, item.clone())
            
            onSlotChange(player, slot, oldItem, item)
        }

        return true
    }

    /**
     * Called by ButtonListeners when an item is removed from a slot.
     */
    internal fun handleItemRemoval(player: Player, slot: Int, removedItem: ItemStack) {
        val oldItem = getSlotItem(slot)
        setSlotItem(slot, null)
        onSlotChange(player, slot, oldItem, null)
    }

    /**
     * Called by ButtonListeners when an item is placed into a slot.
     */
    internal fun handleItemPlacement(player: Player, slot: Int, placedItem: ItemStack) {
        val oldItem = getSlotItem(slot)
        setSlotItem(slot, placedItem)
        onSlotChange(player, slot, oldItem, placedItem)
    }
}
