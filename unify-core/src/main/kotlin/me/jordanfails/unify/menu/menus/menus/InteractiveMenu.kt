package me.jordanfails.unify.menu.menus.menus

import com.cryptomorin.xseries.XMaterial
import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.menu.Menu
import me.jordanfails.unify.menu.buttons.InteractiveSlotButton
import me.jordanfails.unify.menu.menus.SlotBehavior
import me.jordanfails.unify.nms.LegacyColorDataType
import me.jordanfails.unify.nms.LegacyItemColor
import me.jordanfails.unify.nms.NMSHandlerFactory
import me.jordanfails.unify.utils.ItemBuilder
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.concurrent.ConcurrentHashMap

/**
 * A menu with slots players can place items into / take items from.
 *
 * Built for crafting, tinkering, deposit/withdraw, and upgrade UIs.
 *
 * ### Slot roles
 * | Override | Behavior |
 * |---|---|
 * | [getInteractiveSlots] | Place **and** take |
 * | [getInputOnlySlots] | Place only (deposit / ingredients) |
 * | [getOutputOnlySlots] | Take only (results / rewards) |
 *
 * Everything else is FIXED chrome via [getFixedButtons].
 *
 * ### Tinkerer / crafting pattern
 * ```kotlin
 * class TinkererMenu : InteractiveMenu("&8Tinkerer") {
 *     init { returnItemsOnClose = true }
 *
 *     override fun getInputOnlySlots() = setOf(11, 13, 15)   // ingredients
 *     override fun getOutputOnlySlots() = setOf(22)          // result
 *
 *     override fun getEmptyPlaceholder(player: Player, slot: Int) = when (slot) {
 *         11 -> ghost(Material.DIAMOND_SWORD, "&7Weapon")
 *         13 -> ghost(Material.EMERALD, "&7Catalyst")
 *         15 -> ghost(Material.PAPER, "&7Modifier")
 *         else -> null
 *     }
 *
 *     override fun getAllowedItemFilter(slot: Int): ((ItemStack) -> Boolean)? = when (slot) {
 *         11 -> { it -> it.type.name.contains("SWORD") }
 *         else -> null
 *     }
 *
 *     // Live result preview whenever inputs change
 *     override fun computeResults(player: Player): Map<Int, ItemStack?> {
 *         val weapon = getSlotItem(11) ?: return mapOf(22 to null)
 *         val result = weapon.clone() // apply your tinker logic
 *         return mapOf(22 to result)
 *     }
 *
 *     // Taking the result consumes ingredients
 *     override fun onResultTaken(player: Player, slot: Int, taken: ItemStack, amount: Int) {
 *         consumeFromSlot(11, 1)
 *         consumeFromSlot(13, 1)
 *         consumeFromSlot(15, 1)
 *         refreshResults(player)
 *     }
 *
 *     override fun getFixedButtons(player: Player) = mapOf(
 *         49 to button { material(Material.BARRIER); name("&cClose"); onClick { it.close() } }
 *     )
 * }
 * ```
 *
 * ### Lifecycle hooks
 * 1. [onSlotChange] — single slot mutated
 * 2. [onContentsChanged] — any interactive content changed (good for recipes)
 * 3. [computeResults] / [refreshResults] — write output slots automatically
 * 4. [onResultTaken] — player pulled from an output slot
 */
abstract class InteractiveMenu : Menu {

    /** Live contents of interactive / input / output slots. */
    val slotContents: MutableMap<Int, ItemStack?> = hashMapOf()

    private val slotBehaviors: MutableMap<Int, SlotBehavior> = ConcurrentHashMap()

    /**
     * Return player-owned items when the menu closes.
     * Defaults to **true** so ingredients are never deleted by accident.
     * Synthetic result slots are excluded unless listed in [getSlotsToReturnOnClose].
     */
    var returnItemsOnClose: Boolean = true

    /**
     * When true, [computeResults] runs after every content change and writes
     * to output slots (ghost results the player can take).
     */
    var autoRefreshResults: Boolean = true

    constructor() : super()
    constructor(title: String) : super(title)

    // ── Slot roles ───────────────────────────────────────────────────────────

    /** Place + take. */
    open fun getInteractiveSlots(): Set<Int> = emptySet()

    /** Place only (ingredients, deposits). */
    open fun getInputOnlySlots(): Set<Int> = emptySet()

    /** Take only (results, rewards). */
    open fun getOutputOnlySlots(): Set<Int> = emptySet()

    /** All slots that participate in item transfer. */
    fun getAllItemSlots(): Set<Int> =
        getInteractiveSlots() + getInputOnlySlots() + getOutputOnlySlots()

    /**
     * Slots returned to the player on close.
     * Default: interactive + input only (not generated results).
     */
    open fun getSlotsToReturnOnClose(): Set<Int> =
        getInteractiveSlots() + getInputOnlySlots()

    open fun shouldReturnItemsOnClose(): Boolean = returnItemsOnClose

    /**
     * Filter for what a slot accepts. `null` = accept anything.
     */
    open fun getAllowedItemFilter(slot: Int): ((ItemStack) -> Boolean)? = null

    /**
     * Max stack size for a slot. Defaults to the item's max stack size.
     * Return `1` for single-item tinkerer slots.
     */
    open fun getMaxAmount(slot: Int): Int = 64

    /**
     * Ghost item shown when a slot is empty (e.g. "Insert weapon").
     * Return `null` for a truly empty slot.
     */
    open fun getEmptyPlaceholder(player: Player, slot: Int): ItemStack? = null

    // ── Result / recipe system ───────────────────────────────────────────────

    /**
     * Compute what should appear in result/output slots given current inputs.
     *
     * Keys are inventory slots (typically your [getOutputOnlySlots]).
     * Value `null` clears that slot.
     *
     * Called automatically when [autoRefreshResults] is true.
     */
    open fun computeResults(player: Player): Map<Int, ItemStack?> = emptyMap()

    /**
     * Called when the player takes items from an [getOutputOnlySlots] result slot.
     * Use this to consume ingredients.
     *
     * @param amount how many items were taken from the result stack
     */
    open fun onResultTaken(player: Player, slot: Int, taken: ItemStack, amount: Int) {}

    /**
     * Re-run [computeResults] and write outputs without treating it as a
     * player placement (won't re-enter result-taken logic).
     */
    fun refreshResults(player: Player) {
        val results = computeResults(player)
        if (results.isEmpty()) return

        val inv = player.openInventory?.topInventory
        for ((slot, item) in results) {
            if (item == null || item.type == Material.AIR) {
                slotContents.remove(slot)
                inv?.setItem(slot, null)
            } else {
                val clone = item.clone()
                slotContents[slot] = clone
                inv?.setItem(slot, clone.clone())
            }
            // Synthetic result writes do not fire onSlotChange / onContentsChanged.
        }
        // Soft-refresh button views (ghosts / amounts) without reopening
        refreshButtons(player)
    }

    // ── Change hooks ─────────────────────────────────────────────────────────

    /**
     * A single slot changed.
     * Prefer [onContentsChanged] for recipe recomputation.
     */
    open fun onSlotChange(player: Player, slot: Int, oldItem: ItemStack?, newItem: ItemStack?) {}

    /**
     * Any interactive content changed. Default implementation refreshes results.
     */
    open fun onContentsChanged(player: Player) {
        if (autoRefreshResults) {
            refreshResults(player)
        }
    }

    /** Called before buttons are assembled each open/rebuild. */
    open fun prepareMenu(player: Player) {}

    // ── Fixed chrome ─────────────────────────────────────────────────────────

    /**
     * Non-interactive buttons (borders, actions, info).
     * Override this instead of [getButtons].
     */
    open fun getFixedButtons(player: Player): Map<Int, Button> = emptyMap()

    /**
     * Background filler for non-item slots.
     * Controlled by the inherited [fillBackground] property (set in `init`).
     */
    open fun getBackgroundButton(): Button =
        Button.placeholder(
            filledMaterial,
            NMSHandlerFactory.getHandler()?.getLegacyColorData(LegacyItemColor.GRAY, LegacyColorDataType.BLOCK)
                ?: LegacyItemColor.GRAY.blockData,
            " "
        )

    /**
     * Called when a shift-click / deposit cannot place any of [item]
     * into this menu (full slots or filter rejection).
     */
    open fun onDepositRejected(player: Player, item: ItemStack) {}

    // ── Queries ──────────────────────────────────────────────────────────────

    fun getSlotBehavior(slot: Int): SlotBehavior =
        slotBehaviors[slot] ?: SlotBehavior.FIXED

    fun isItemSlot(slot: Int): Boolean =
        getSlotBehavior(slot) != SlotBehavior.FIXED

    fun getSlotItem(slot: Int): ItemStack? = slotContents[slot]?.clone()

    fun hasItem(slot: Int): Boolean {
        val item = slotContents[slot]
        return item != null && item.type != Material.AIR
    }

    fun isItemAllowedInSlot(slot: Int, item: ItemStack): Boolean {
        if (item.type == Material.AIR) return false
        val filter = getAllowedItemFilter(slot) ?: return true
        return filter(item)
    }

    fun canPlaceIn(slot: Int): Boolean {
        val b = getSlotBehavior(slot)
        return b == SlotBehavior.INTERACTIVE || b == SlotBehavior.INPUT_ONLY
    }

    fun canTakeFrom(slot: Int): Boolean {
        val b = getSlotBehavior(slot)
        return b == SlotBehavior.INTERACTIVE || b == SlotBehavior.OUTPUT_ONLY
    }

    // ── Mutation API ─────────────────────────────────────────────────────────

    /**
     * Set a slot without firing hooks. Use for load/init.
     */
    fun setSlotItem(slot: Int, item: ItemStack?) {
        if (item == null || item.type == Material.AIR) {
            slotContents.remove(slot)
        } else {
            slotContents[slot] = item.clone()
        }
    }

    /**
     * Set a slot, update the open inventory, and fire change hooks.
     */
    fun setSlotItem(player: Player, slot: Int, item: ItemStack?, notify: Boolean = true) {
        val old = getSlotItem(slot)
        setSlotItem(slot, item)
        player.openInventory?.topInventory?.setItem(
            slot,
            if (item == null || item.type == Material.AIR) null else item.clone()
        )
        if (notify) {
            notifyChanged(player, slot, old, getSlotItem(slot))
        }
    }

    fun clearSlotContents() {
        slotContents.clear()
    }

    /**
     * Clear multiple slots at once (one [onContentsChanged] at the end).
     * Handy from [onResultTaken] when a craft consumes every ingredient.
     */
    fun clearSlots(player: Player, slots: Collection<Int>) {
        var changed = false
        val inv = player.openInventory?.topInventory
        for (slot in slots) {
            if (!hasItem(slot)) continue
            slotContents.remove(slot)
            inv?.setItem(slot, null)
            changed = true
        }
        if (changed) {
            onContentsChanged(player)
        }
    }

    /** Clear all input + interactive slots (not outputs). */
    fun clearInputs(player: Player) {
        clearSlots(player, getInteractiveSlots() + getInputOnlySlots())
    }

    /**
     * Remove up to [amount] from [slot]. Returns what was removed, or null.
     */
    fun consumeFromSlot(slot: Int, amount: Int): ItemStack? {
        val current = slotContents[slot] ?: return null
        if (current.type == Material.AIR || amount <= 0) return null

        val take = amount.coerceAtMost(current.amount)
        val removed = current.clone().apply { this.amount = take }

        if (take >= current.amount) {
            slotContents.remove(slot)
        } else {
            slotContents[slot] = current.clone().apply { this.amount = current.amount - take }
        }
        return removed
    }

    /**
     * Consume from [slot] and sync the open inventory + hooks.
     */
    fun consumeFromSlot(player: Player, slot: Int, amount: Int, notify: Boolean = true): ItemStack? {
        val old = getSlotItem(slot)
        val removed = consumeFromSlot(slot, amount) ?: return null
        val inv = player.openInventory?.topInventory
        inv?.setItem(slot, slotContents[slot]?.clone())
        if (notify) {
            notifyChanged(player, slot, old, getSlotItem(slot))
        }
        return removed
    }

    /**
     * Core place logic with stacking + max-amount respect.
     *
     * @return leftover that did not fit (null if everything placed)
     */
    fun placeIntoSlot(
        player: Player,
        slot: Int,
        incoming: ItemStack,
        maxToPlace: Int = incoming.amount
    ): ItemStack? {
        if (!canPlaceIn(slot)) return incoming.clone()
        if (!isItemAllowedInSlot(slot, incoming)) return incoming.clone()

        val toPlace = maxToPlace.coerceAtMost(incoming.amount).coerceAtLeast(0)
        if (toPlace <= 0) return incoming.clone()

        val current = slotContents[slot]
        val slotMax = getMaxAmount(slot).coerceAtLeast(1)
        val itemMax = incoming.maxStackSize.coerceAtLeast(1)
        val hardMax = minOf(slotMax, itemMax)

        val old = getSlotItem(slot)
        val placed: ItemStack
        val leftoverAmount: Int

        if (current == null || current.type == Material.AIR) {
            val amount = toPlace.coerceAtMost(hardMax)
            placed = incoming.clone().apply { this.amount = amount }
            leftoverAmount = incoming.amount - amount
            slotContents[slot] = placed
        } else if (current.isSimilar(incoming)) {
            val room = (hardMax - current.amount).coerceAtLeast(0)
            val amount = toPlace.coerceAtMost(room)
            if (amount <= 0) return incoming.clone()
            placed = current.clone().apply { this.amount = current.amount + amount }
            leftoverAmount = incoming.amount - amount
            slotContents[slot] = placed
        } else {
            // Different item — no place (swap is handled by the listener)
            return incoming.clone()
        }

        player.openInventory?.topInventory?.setItem(slot, placed.clone())
        notifyChanged(player, slot, old, placed.clone())

        return if (leftoverAmount > 0) {
            incoming.clone().apply { amount = leftoverAmount }
        } else {
            null
        }
    }

    /**
     * Core take logic.
     *
     * @return the taken stack, or null if nothing was taken
     */
    fun takeFromSlot(player: Player, slot: Int, amount: Int): ItemStack? {
        if (!canTakeFrom(slot)) return null
        val current = slotContents[slot] ?: return null
        if (current.type == Material.AIR || amount <= 0) return null

        val take = amount.coerceAtMost(current.amount)
        val old = current.clone()
        val taken = current.clone().apply { this.amount = take }
        val isResult = getSlotBehavior(slot) == SlotBehavior.OUTPUT_ONLY

        val newItem: ItemStack? = if (take >= current.amount) {
            slotContents.remove(slot)
            player.openInventory?.topInventory?.setItem(slot, null)
            null
        } else {
            val remaining = current.clone().apply { this.amount = current.amount - take }
            slotContents[slot] = remaining
            player.openInventory?.topInventory?.setItem(slot, remaining.clone())
            remaining.clone()
        }

        // Fire slot hook first
        onSlotChange(player, slot, old, newItem)

        // Consume ingredients BEFORE recomputing the result preview
        if (isResult) {
            onResultTaken(player, slot, taken, take)
        }

        onContentsChanged(player)
        return taken
    }

    /**
     * Swap cursor item with slot contents. Returns the new cursor item.
     */
    fun swapWithSlot(player: Player, slot: Int, cursor: ItemStack): ItemStack? {
        val behavior = getSlotBehavior(slot)
        val current = getSlotItem(slot)

        // Output-only: only take, never place
        if (behavior == SlotBehavior.OUTPUT_ONLY) {
            return if (current != null) {
                takeFromSlot(player, slot, current.amount) ?: cursor
            } else cursor
        }

        // Input-only: can place, and can only "swap" if empty or similar stack merge
        if (behavior == SlotBehavior.INPUT_ONLY) {
            if (!isItemAllowedInSlot(slot, cursor)) return cursor
            if (current == null || current.type == Material.AIR) {
                val leftover = placeIntoSlot(player, slot, cursor)
                return leftover
            }
            // Input-only cannot take, so no full swap
            if (current.isSimilar(cursor)) {
                return placeIntoSlot(player, slot, cursor)
            }
            return cursor
        }

        // Fully interactive: true swap
        if (behavior != SlotBehavior.INTERACTIVE) return cursor
        if (!isItemAllowedInSlot(slot, cursor)) return cursor

        val old = current
        setSlotItem(slot, cursor)
        player.openInventory?.topInventory?.setItem(slot, cursor.clone())
        notifyChanged(player, slot, old, cursor.clone())
        return old
    }

    // ── Menu assembly ────────────────────────────────────────────────────────

    override fun onOpen(player: Player) {
        super.onOpen(player)
        computeSlotBehaviors()
        if (autoRefreshResults) {
            refreshResults(player)
        }
    }

    override fun onClose(player: Player, manualClose: Boolean) {
        super.onClose(player, manualClose)

        if (!shouldReturnItemsOnClose()) return

        for (slot in getSlotsToReturnOnClose()) {
            val item = slotContents[slot] ?: continue
            if (item.type == Material.AIR) continue
            val leftover = player.inventory.addItem(item)
            leftover.values.forEach { drop ->
                player.world.dropItemNaturally(player.location, drop)
            }
            slotContents.remove(slot)
        }
    }

    private fun computeSlotBehaviors() {
        slotBehaviors.clear()
        getInteractiveSlots().forEach { slotBehaviors[it] = SlotBehavior.INTERACTIVE }
        getInputOnlySlots().forEach { slotBehaviors[it] = SlotBehavior.INPUT_ONLY }
        getOutputOnlySlots().forEach { slotBehaviors[it] = SlotBehavior.OUTPUT_ONLY }
    }

    final override fun getButtons(player: Player): Map<Int, Button> {
        prepareMenu(player)
        computeSlotBehaviors()

        val buttons = linkedMapOf<Int, Button>()
        val size = resolveSize(player)

        // Use Menu.fillBackground property (not a conflicting method).
        // Skip item slots so InteractiveSlotButtons own those positions.
        if (fillBackground) {
            val bg = getBackgroundButton()
            for (slot in 0 until size) {
                if (slot !in slotBehaviors) {
                    buttons[slot] = bg
                }
            }
        }

        // Interactive item slots
        for ((slot, behavior) in slotBehaviors) {
            buttons[slot] = InteractiveSlotButton(
                menu = this,
                slot = slot,
                behavior = behavior
            )
        }

        // Fixed chrome wins on conflicts
        buttons.putAll(getFixedButtons(player))

        return buttons
    }

    private fun resolveSize(player: Player): Int {
        val min = getMinSize().takeIf { it > 0 } ?: 27
        var highest = min - 1
        getAllItemSlots().forEach { highest = maxOf(highest, it) }
        getFixedButtons(player).keys.forEach { highest = maxOf(highest, it) }
        val resolved = (((highest + 1) + 8) / 9) * 9
        return resolved.coerceIn(9, 54)
    }

    // ── Legacy Menu accept hooks (shift-click / drag entry points) ───────────

    override fun acceptsInsertedItem(player: Player, itemStack: ItemStack, slot: Int): Boolean {
        // Prefer the richer placeIntoSlot path. Returns true only if something was placed.
        if (!canPlaceIn(slot)) return false
        val before = itemStack.amount
        val leftover = placeIntoSlot(player, slot, itemStack)
        return leftover == null || leftover.amount < before
    }

    /**
     * Deposit used by generic listeners that clear the stack when this returns true.
     *
     * Returns true only when the **entire** stack was placed. Prefer [shiftClickInto]
     * when partial stacks must leave a leftover on the player slot.
     */
    override fun acceptsShiftClickedItem(player: Player, itemStack: ItemStack): Boolean {
        val leftover = shiftClickInto(player, itemStack)
        return leftover == null
    }

    /**
     * Shift-click / deposit from player inventory into this menu.
     *
     * @return leftover that didn't fit (`null` if everything was placed).
     * If nothing was placed, [onDepositRejected] is invoked.
     */
    fun shiftClickInto(player: Player, item: ItemStack, notifyReject: Boolean = true): ItemStack? {
        val originalAmount = item.amount
        var remaining: ItemStack? = item.clone()
        val inputSlots = (getInteractiveSlots() + getInputOnlySlots()).sorted()
        for (slot in inputSlots) {
            if (remaining == null) break
            remaining = placeIntoSlot(player, slot, remaining)
        }
        if (notifyReject && remaining != null && remaining.amount >= originalAmount) {
            onDepositRejected(player, item)
        }
        return remaining
    }

    override fun acceptsDraggedItems(player: Player, items: Map<Int, ItemStack>): Boolean {
        // Validate all first
        for ((slot, item) in items) {
            if (!canPlaceIn(slot)) return false
            if (!isItemAllowedInSlot(slot, item)) return false
            val current = slotContents[slot]
            if (current != null && current.type != Material.AIR && !current.isSimilar(item)) {
                return false
            }
        }
        for ((slot, item) in items) {
            placeIntoSlot(player, slot, item)
        }
        return true
    }

    // ── Internal notifications ───────────────────────────────────────────────

    internal fun handleItemRemoval(player: Player, slot: Int, removedItem: ItemStack) {
        // Used by older listener paths; prefer takeFromSlot
        val old = getSlotItem(slot)
        setSlotItem(slot, null)
        notifyChanged(player, slot, old, null)
        if (getSlotBehavior(slot) == SlotBehavior.OUTPUT_ONLY) {
            onResultTaken(player, slot, removedItem, removedItem.amount)
            if (autoRefreshResults) refreshResults(player)
        }
    }

    internal fun handleItemPlacement(player: Player, slot: Int, placedItem: ItemStack) {
        val old = getSlotItem(slot)
        setSlotItem(slot, placedItem)
        notifyChanged(player, slot, old, placedItem)
    }

    private fun notifyChanged(player: Player, slot: Int, oldItem: ItemStack?, newItem: ItemStack?) {
        onSlotChange(player, slot, oldItem, newItem)
        // Skip onContentsChanged for pure result-slot rewrites from refreshResults
        // (refreshResults doesn't call notifyChanged).
        onContentsChanged(player)
        // Rebuild fixed chrome (previews / confirm / result) without reopening.
        refreshButtons(player)
    }

    companion object {
        /**
         * Build a translucent-looking ghost placeholder for empty input slots.
         */
        @JvmStatic
        fun ghost(material: Material, name: String, vararg lore: String): ItemStack {
            return ItemBuilder(material)
                .name(name)
                .lore(lore.toList().ifEmpty {
                    listOf("&8Place an item here")
                })
                .build()
        }

        @JvmStatic
        fun ghost(xMaterial: XMaterial, name: String, vararg lore: String): ItemStack {
            return ItemBuilder(xMaterial)
                .name(name)
                .lore(lore.toList().ifEmpty {
                    listOf("&8Place an item here")
                })
                .build()
        }
    }
}
