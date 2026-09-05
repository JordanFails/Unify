package me.jordanfails.unify.menu.menus.menus

import com.cryptomorin.xseries.XMaterial
import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.menu.Menu
import me.jordanfails.unify.menu.MenuFillMode
import me.jordanfails.unify.menu.MenuFiller
import org.bukkit.entity.Player
import kotlin.math.ceil

/**
 * A menu with an automatic border around its edges (top row, bottom row,
 * left column, right column).
 *
 * Split content into two maps:
 * - **[getContentButtons]** — inner area only (items, entries, nodes)
 * - **[getBorderButtons]** — edge slots where glass would otherwise go
 *   (back, close, tabs, navigation, etc.)
 *
 * ### Content placement (important)
 *
 * By default ([autoPlaceContentButtons] = `true`), keys in [getContentButtons]
 * that look like **order indexes** (`0, 1, 2, …`) are packed into the next free
 * **inner** content slots — so a `forEachIndexed` fills the content region, not
 * the border at inventory slot `0`.
 *
 * **Absolute slots stay absolute.** If keys are sparse inventory positions
 * (e.g. `11`, `13`, `15`), they are **not** remapped — they render at those
 * inventory slots. Only order-index key sets (`0..n-1`, optionally with holes
 * that are exactly [autoPlaceExceptions]) are auto-placed.
 *
 * The decorative border frame is sealed **after** content, so content keys
 * cannot punch holes in edge glass. Only [getBorderButtons], exemptions, and
 * [emptyBorderSlots] may occupy border slots.
 *
 * ```kotlin
 * // Order indexes → auto-pack into content area
 * skills.forEachIndexed { index, skill -> this[index] = SkillButton(skill) }
 *
 * // Absolute slots → stay put (no remapping)
 * this[11] = RankKitsButton()
 * this[13] = GKitsButton()
 * this[15] = VKitsButton()
 * ```
 *
 * Keys listed in [autoPlaceExceptions] (or [getAutoPlaceExceptions]) always
 * keep absolute inventory slots, even when mixed with order indexes:
 * ```kotlin
 * init { autoPlaceExceptions = setOf(22) } // slot 22 never auto-packs
 * this[0] = A(); this[1] = B(); this[22] = Fixed()
 * ```
 *
 * ### Example
 * ```kotlin
 * class SkillsMenu : BorderedMenu() {
 *     override fun getMinSize() = 54
 *
 *     override fun getContentButtons(player: Player) = mutableMapOf<Int, Button>().apply {
 *         listOf("Strength", "Agility", "Endurance").forEachIndexed { i, name ->
 *             this[i] = SkillButton(name)
 *         }
 *     }
 *
 *     override fun getBorderButtons(player: Player) = mapOf(
 *         49 to BackButton { /* ... */ },
 *         53 to CloseButton(),
 *     )
 * }
 * ```
 *
 * ### Layout layers (low → high priority)
 * 1. Border glass filler
 * 2. Content buttons (auto-placed when keys are order indexes)
 * 3. Border buttons (always on top of glass / content on those slots)
 * 4. Inner filler (only on slots still empty — never covers anything above)
 *
 * ### Filler material and fill mode
 *
 * [borderMaterial] sets the frame material, [fillMaterial] the background material, and [fillMode]
 * decides which of the two regions actually get painted:
 *
 * ```kotlin
 * class ShopMenu : BorderedMenu() {
 *     init {
 *         borderMaterial = XMaterial.BLUE_STAINED_GLASS_PANE
 *         fillMode = MenuFillMode.INNER              // only the non-border slots
 *         fillMaterial = XMaterial.BLACK_STAINED_GLASS_PANE
 *     }
 * }
 * ```
 *
 * [MenuFillMode.BORDER] is the default (frame only), [MenuFillMode.INNER] paints the background and
 * leaves the frame bare, [MenuFillMode.BOTH] does both, and [MenuFillMode.NONE] disables filler
 * while keeping the bordered *layout* (content still stays out of the edge slots).
 *
 * ### ⚠️ Controlling the menu size
 *
 * Override **[getMenuSize]** (or [getMinSize]) to set a specific inventory
 * size — **do NOT override [size][me.jordanfails.unify.menu.Menu.size]**.
 *
 * The border calculation in [fillBorder] reads [getMenuSize] directly.
 * Overriding [size][me.jordanfails.unify.menu.Menu.size] only affects the
 * inventory creation in [Menu.createInventory] but **not** which slots
 * are considered border. If [getMenuSize] returns a size smaller than your
 * intended row count, [fillBorder] may treat every row as "first or last",
 * filling the entire menu with border panes instead of just the edges.
 *
 * Correct:
 * ```
 * override fun getMinSize(): Int = 27    // 3 rows
 * // or
 * override fun getMenuSize(player: Player): Int = 36  // 4 rows
 * ```
 *
 * Wrong (has no effect on the border):
 * ```
 * override fun size(buttons: Map<Int, Button>): Int = 9 * 3
 * ```
 */
abstract class BorderedMenu(
    /**
     * Border slots that should stay empty (no glass). Border buttons still
     * render on these slots if provided via [getBorderButtons].
     */
    private val emptyBorderSlots: Set<Int> = emptySet(),
    /**
     * When enabled (default), dense order-index keys (`0, 1, 2, …`) from
     * [getContentButtons] are packed into free **inner** content slots.
     *
     * Sparse / absolute keys (e.g. `11`, `13`, `15`) are never remapped.
     * Keys in [autoPlaceExceptions] / [getAutoPlaceExceptions] are always
     * absolute, so you can mix order indexes with fixed slots.
     *
     * Set to `false` to force raw absolute inventory slots for every entry.
     * Prefer [getBorderButtons] for frame controls (back, close, tabs).
     */
    var autoPlaceContentButtons: Boolean = true,
    /**
     * Map keys from [getContentButtons] that always keep absolute inventory
     * slots when [autoPlaceContentButtons] is enabled. Exempt keys are never
     * auto-packed; remaining order-index buttons fill free inner slots.
     *
     * Alias of the same idea as “selected fixed slots”.
     */
    var autoPlaceExceptions: Set<Int> = emptySet()
) : Menu() {

    init {
        autoUpdate = true
    }

    /**
     * Material used for the decorative border frame.
     * Override [getPlaceholderButton] when the frame item depends on the viewing player.
     */
    open var borderMaterial: XMaterial = XMaterial.GRAY_STAINED_GLASS_PANE

    /** Display name of the border filler item. Blank by default. */
    open var borderName: String = " "

    /**
     * Which regions get decorative filler. Defaults to [MenuFillMode.BORDER] (frame only) —
     * set [MenuFillMode.INNER] to fill only the non-border slots, or [MenuFillMode.BOTH] for both.
     */
    open var fillMode: MenuFillMode = MenuFillMode.BORDER

    /**
     * Material used to fill leftover **inner** slots when [fillMode] fills the inner region.
     * Kept separate from [borderMaterial] so a frame and a background can differ.
     */
    open var fillMaterial: XMaterial = XMaterial.BLACK_STAINED_GLASS_PANE

    /** Display name of the inner filler item. Blank by default. */
    open var fillName: String = " "

    /**
     * Content-button map keys that keep absolute inventory positions when
     * [autoPlaceContentButtons] is enabled. Defaults to [autoPlaceExceptions];
     * override for player-specific fixed / exempt slots.
     */
    open fun getAutoPlaceExceptions(player: Player): Set<Int> = autoPlaceExceptions

    /**
     * Buttons for the **inner** (non-border) area of the menu.
     *
     * **Default behavior** ([autoPlaceContentButtons] = `true`):
     * - Dense keys `0, 1, 2, …` → order indexes, packed into content slots
     * - Sparse keys like `11, 13, 15` → absolute inventory slots (no remap)
     * - Keys in [getAutoPlaceExceptions] → always absolute
     *
     * With auto-place off, every key is an absolute inventory slot.
     * Use [getBorderButtons] for anything that should sit on the glass frame.
     */
    abstract fun getContentButtons(player: Player): MutableMap<Int, Button>

    /**
     * Buttons placed on the **border frame** (where glass filler would go).
     *
     * Absolute inventory slots only. Typical uses: back, close, category tabs,
     * page arrows, info items. These always render above the glass filler.
     *
     * Default: no border buttons.
     */
    open fun getBorderButtons(player: Player): Map<Int, Button> = emptyMap()

    /**
     * Whether to render decorative glass around the menu edges.
     * Override to `false` for a full-screen layout (content can use every slot).
     */
    open fun hasBorder(): Boolean = true

    /**
     * The total menu size. Override this to set a specific size,
     * otherwise it's calculated from content / border buttons + [getMinSize].
     *
     * This is the method [fillBorder] reads to determine which slots are
     * edge slots. Prefer this over overriding [size][me.jordanfails.unify.menu.Menu.size].
     *
     * @see BorderedMenu class-level docs for the full explanation.
     */
    open fun getMenuSize(player: Player): Int {
        val contentButtons = getContentButtons(player)
        val borderButtons = getBorderButtons(player)
        val minSize = getMinSize().takeIf { it > 0 } ?: 27

        if (contentButtons.isEmpty() && borderButtons.isEmpty()) {
            return minSize
        }

        val exceptions = getAutoPlaceExceptions(player)
        if (autoPlaceContentButtons && shouldAutoPlaceKeys(contentButtons.keys, exceptions)) {
            val autoPlaceCount = contentButtons.keys.count { it !in exceptions }
            val highestFixed = sequenceOf(
                contentButtons.keys.asSequence().filter { it in exceptions },
                borderButtons.keys.asSequence()
            ).flatten().maxOrNull() ?: -1

            val contentRows = if (hasBorder()) {
                (autoPlaceCount + 6) / 7
            } else {
                (autoPlaceCount + 8) / 9
            }
            val requiredRows = if (hasBorder()) {
                (contentRows + 2).coerceAtLeast(3)
            } else {
                contentRows.coerceAtLeast(1)
            }
            var requiredSize = requiredRows * 9
            requiredSize = maxOf(requiredSize, minSize)

            if (highestFixed >= 0) {
                val fixedSize = ((highestFixed / 9) + 1) * 9
                requiredSize = maxOf(requiredSize, fixedSize)
            }

            // Grow until enough free content slots remain after fixed positions
            val fixedSlots = buildSet {
                contentButtons.keys.filterTo(this) { it in exceptions }
                addAll(borderButtons.keys)
            }
            while (requiredSize <= 54) {
                val freeSlots = getAvailableContentSlots(requiredSize).count { it !in fixedSlots }
                if (freeSlots >= autoPlaceCount) break
                requiredSize += 9
            }

            return requiredSize.coerceAtMost(54)
        }

        var highest = -1
        for (slot in contentButtons.keys) {
            if (slot > highest) highest = slot
        }
        for (slot in borderButtons.keys) {
            if (slot > highest) highest = slot
        }

        if (highest < 0) return minSize

        val calculatedSize = (ceil((highest + 1) / 9.0) * 9.0).toInt()
        return if (minSize > calculatedSize) minSize else calculatedSize.coerceAtMost(54)
    }

    /**
     * The placeholder (visual filler) button used for empty border slots.
     * Built from [borderMaterial] / [borderName]; override to supply a custom item.
     */
    open fun getPlaceholderButton(): Button = MenuFiller.button(borderMaterial, borderName)

    /**
     * The filler button used for leftover **inner** slots when [fillMode] fills the inner
     * region. Built from [fillMaterial] / [fillName]; override to supply a custom item.
     */
    open fun getFillButton(): Button = MenuFiller.button(fillMaterial, fillName)

    /**
     * Composes the menu layout:
     * content → border glass (seals the frame) → border buttons (highest priority).
     *
     * Border glass is applied **after** content so absolute / mis-keyed content
     * cannot punch holes in the decorative frame. Only [getBorderButtons],
     * [autoPlaceExceptions], and [emptyBorderSlots] may occupy edge slots.
     */
    final override fun getButtons(player: Player): MutableMap<Int, Button> {
        val buttons = mutableMapOf<Int, Button>()
        val borderButtons = getBorderButtons(player)
        val menuSize = getMenuSize(player)

        val suppliedContentButtons = getContentButtons(player)
        val exceptions = getAutoPlaceExceptions(player)
        val contentButtons = if (
            autoPlaceContentButtons &&
            shouldAutoPlaceKeys(suppliedContentButtons.keys, exceptions)
        ) {
            placeContentButtonsInNextAvailableSlots(
                suppliedContentButtons,
                menuSize,
                exceptions,
                borderButtons.keys
            )
        } else {
            // Absolute inventory slots (auto-place off, or sparse keys like 11/13/15)
            suppliedContentButtons
        }

        // ── ① Content first (may briefly sit on edge slots when absolute)
        buttons.putAll(contentButtons)


        // ── ② Border glass seals the frame (overwrites non-exempt edge content).
        //     Exceptions keep absolute content on the frame; border buttons skip glass.
        if (hasBorder() && fillMode.fillsBorder) {
            fillBorder(
                buttons,
                menuSize,
                occupiedByBorderButtons = borderButtons.keys + exceptions
            )
        }

        // ── ③ Border buttons (highest priority — sit on the frame)
        for ((slot, button) in borderButtons) {
            if (slot in 0 until menuSize) {
                buttons[slot] = button
            }
        }

        // ── ④ Inner filler, last and only on slots that are still empty, so it can never
        //     cover content or frame controls.
        if (fillMode.fillsInner) {
            fillInner(buttons, menuSize)
        }

        return buttons
    }

    /**
     * Whether non-exempt keys should be treated as order indexes and packed into
     * free **inner** slots.
     *
     * True when keys form `0, 1, …, n-1`, **or** that same sequence with holes
     * that are exactly [exceptions] (so fixed mid-range slots don't force the
     * whole map into absolute mode and dump indexes onto the border).
     *
     * Sparse absolute layouts (e.g. `11, 13, 15`) return false.
     */
    private fun shouldAutoPlaceKeys(keys: Collection<Int>, exceptions: Set<Int>): Boolean {
        val orderKeys = keys.filter { it !in exceptions }
        if (orderKeys.isEmpty()) return false

        val sorted = orderKeys.sorted()
        if (sorted.first() != 0) return false

        val orderKeySet = orderKeys.toSet()
        val maxKey = sorted.last()

        // Every index in 0..maxKey must be either an order key or an exception hole.
        // Reject layouts with gaps that aren't claimed exceptions (true absolute/sparse maps).
        for (i in 0..maxKey) {
            val isOrder = i in orderKeySet
            val isExceptionHole = i in exceptions
            if (!isOrder && !isExceptionHole) return false
        }

        // Order keys themselves must be unique positions in that range (no dupes).
        return orderKeys.size == orderKeySet.size
    }

    /**
     * Places content sequentially into non-border slots. Keys in [exceptions]
     * keep their absolute inventory slots; remaining buttons fill free content
     * slots in key order (skipping slots taken by exceptions or [borderReserved]).
     */
    private fun placeContentButtonsInNextAvailableSlots(
        contentButtons: Map<Int, Button>,
        totalSlots: Int,
        exceptions: Set<Int>,
        borderReserved: Set<Int>
    ): MutableMap<Int, Button> {
        val placedButtons = linkedMapOf<Int, Button>()

        // Fixed / exempt positions first — absolute inventory slots
        contentButtons.forEach { (key, button) ->
            if (key in exceptions && key in 0 until totalSlots) {
                placedButtons[key] = button
            }
        }

        val reservedSlots = placedButtons.keys + borderReserved
        val availableSlots = getAvailableContentSlots(totalSlots)
            .filter { it !in reservedSlots }

        contentButtons.entries
            .filter { it.key !in exceptions }
            .sortedBy { it.key }
            .zip(availableSlots)
            .forEach { (entry, slot) ->
                placedButtons[slot] = entry.value
            }

        return placedButtons
    }

    private fun getAvailableContentSlots(totalSlots: Int): List<Int> {
        if (!hasBorder()) return (0 until totalSlots).toList()

        val rows = totalSlots / 9
        return (0 until totalSlots).filter { slot ->
            val row = slot / 9
            val col = slot % 9
            val isFirstOrLastRow = rows > 1 && (row == 0 || row == rows - 1)

            !isFirstOrLastRow && col != 0 && col != 8
        }
    }

    /**
     * Fills outer edge slots with glass. Skips [emptyBorderSlots] and any
     * [occupiedByBorderButtons] slots (those get real buttons instead).
     */
    protected fun fillBorder(
        buttons: MutableMap<Int, Button>,
        totalSlots: Int,
        occupiedByBorderButtons: Set<Int> = emptySet()
    ) {
        val placeholder = getPlaceholderButton()
        val rows = totalSlots / 9

        for (slot in 0 until totalSlots) {
            if (slot in emptyBorderSlots) continue
            if (slot in occupiedByBorderButtons) continue

            val row = slot / 9
            val col = slot % 9

            val isFirstOrLastRow = if (rows > 1) {
                row == 0 || row == rows - 1
            } else {
                false
            }

            if (isFirstOrLastRow || col == 0 || col == 8) {
                buttons[slot] = placeholder
            }
        }
    }

    /**
     * Fills every **non-border** slot that is still empty with [getFillButton]. Called after content
     * and frame controls, so it only paints leftovers — it never overwrites a real button.
     *
     * With [hasBorder] off there are no edge slots, so this fills the whole menu's leftovers.
     */
    protected fun fillInner(buttons: MutableMap<Int, Button>, totalSlots: Int) {
        val filler = getFillButton()

        for (slot in getAvailableContentSlots(totalSlots)) {
            if (slot !in buttons) {
                buttons[slot] = filler
            }
        }
    }

    /**
     * All border (edge) slot indexes for the current menu size.
     */
    protected fun getBorderSlots(player: Player): List<Int> {
        val totalSlots = getMenuSize(player)
        val rows = totalSlots / 9
        return (0 until totalSlots).filter { slot ->
            val row = slot / 9
            val col = slot % 9
            val isFirstOrLastRow = rows > 1 && (row == 0 || row == rows - 1)
            isFirstOrLastRow || col == 0 || col == 8
        }
    }

    /**
     * Returns all *inner* slot indexes (excluding the border and any slots
     * already used by border buttons). Handy for placing dynamic items.
     *
     * Does **not** exclude content-button slots — use this to know the full
     * content region, not remaining free slots after content is placed.
     */
    protected fun getInnerSlots(player: Player): List<Int> {
        val totalSlots = getMenuSize(player)
        val borderOccupied = getBorderButtons(player).keys
        return getAvailableContentSlots(totalSlots).filter { it !in borderOccupied }
    }

    /**
     * Inner slots that do not already have a content button assigned
     * (after auto-place resolution when enabled).
     */
    protected fun getFreeInnerSlots(player: Player): List<Int> {
        val supplied = getContentButtons(player)
        val exceptions = getAutoPlaceExceptions(player)
        val contentKeys = if (
            autoPlaceContentButtons &&
            shouldAutoPlaceKeys(supplied.keys, exceptions)
        ) {
            placeContentButtonsInNextAvailableSlots(
                supplied,
                getMenuSize(player),
                exceptions,
                getBorderButtons(player).keys
            ).keys
        } else {
            supplied.keys
        }
        return getInnerSlots(player).filter { it !in contentKeys }
    }
}
