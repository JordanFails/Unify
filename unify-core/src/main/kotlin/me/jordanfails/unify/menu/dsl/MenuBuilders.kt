package me.jordanfails.unify.menu.dsl

import com.cryptomorin.xseries.XMaterial
import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.menu.Menu
import me.jordanfails.unify.menu.MenuFillMode
import me.jordanfails.unify.menu.buttons.BackButton
import me.jordanfails.unify.menu.history.MenuHistory
import me.jordanfails.unify.menu.menus.menus.BorderedMenu
import me.jordanfails.unify.menu.menus.menus.InteractiveMenu
import me.jordanfails.unify.menu.pagination.PaginatedBorderedMenu
import me.jordanfails.unify.utils.XSupport
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack

// ─────────────────────────────────────────────────────────────────────────────
// Plain menu DSL
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Build a plain [Menu] with a Kotlin DSL.
 *
 * ```kotlin
 * val m = menu("&8Warps") {
 *     rows(3)
 *     slot(1, 4) {
 *         material(Material.ENDER_PEARL)
 *         name("&aSpawn")
 *         onClick { it.player.teleport(spawn) }
 *     }
 *     fillEmpty()
 * }
 * m.open(player)
 * ```
 */
fun menu(title: String = "&8Menu", block: PlainMenuBuilder.() -> Unit): Menu =
    PlainMenuBuilder(title).apply(block).build()

@MenuDslMarker
class PlainMenuBuilder(private var title: String) {
    private var rows: Int = 3
    private val buttons = linkedMapOf<Int, Button>()
    private var autoUpdate: Boolean = false
    private var updateAfterClick: Boolean = false
    private var fillEmpty: Boolean = false
    private var onOpen: ((Player) -> Unit)? = null
    private var onClose: ((Player, Boolean) -> Unit)? = null
    private var onBackground: ((Player, Int, ClickType) -> Boolean)? = null
    private var mask: LayoutMask? = null

    fun title(title: String) { this.title = title }

    fun rows(rows: Int) {
        require(rows in 1..6) { "rows must be 1..6" }
        this.rows = rows
    }

    fun autoUpdate(enabled: Boolean = true) { autoUpdate = enabled }
    fun updateAfterClick(enabled: Boolean = true) { updateAfterClick = enabled }
    fun fillEmpty(enabled: Boolean = true) { fillEmpty = enabled }

    fun onOpen(handler: (Player) -> Unit) { onOpen = handler }
    fun onClose(handler: (Player, Boolean) -> Unit) { onClose = handler }
    fun onBackgroundClick(handler: (Player, Int, ClickType) -> Boolean) {
        onBackground = handler
    }

    /** Place a button at an absolute inventory slot. */
    fun slot(index: Int, button: Button) {
        buttons[index] = button
    }

    fun slot(index: Int, block: ButtonBuilder.() -> Unit) {
        buttons[index] = ButtonBuilder().apply(block).build()
    }

    fun slot(row: Int, col: Int, block: ButtonBuilder.() -> Unit) {
        slot(Slots.of(row, col), block)
    }

    fun slot(row: Int, col: Int, button: Button) {
        slot(Slots.of(row, col), button)
    }

    /**
     * Apply an ASCII [LayoutMask]. Bindings inside [block] write into this menu.
     */
    fun layout(vararg pattern: String, block: MaskAttachment.() -> Unit) {
        val m = layoutMask(*pattern)
        mask = m
        rows = m.rowCount
        m.attach(buttons, block)
    }

    fun build(): Menu {
        val self = this
        return object : Menu(self.title) {
            init {
                this.autoUpdate = self.autoUpdate
                this.updateAfterClick = self.updateAfterClick
                this.placeholder = self.fillEmpty
            }

            override fun getMinSize(): Int = self.rows * 9

            override fun getTitle(player: Player): String = self.title

            override fun getButtons(player: Player): Map<Int, Button> = self.buttons.toMap()

            override fun onOpen(player: Player) {
                self.onOpen?.invoke(player)
            }

            override fun onClose(player: Player, manualClose: Boolean) {
                super.onClose(player, manualClose)
                self.onClose?.invoke(player, manualClose)
            }

            override fun onBackgroundClick(player: Player, slot: Int, clickType: ClickType): Boolean {
                return self.onBackground?.invoke(player, slot, clickType) ?: false
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bordered menu DSL
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Build a [BorderedMenu] with a Kotlin DSL.
 *
 * Content indexes auto-place into the inner region (same as
 * [BorderedMenu.autoPlaceContentButtons]).
 *
 * ```kotlin
 * borderedMenu("&8Skills") {
 *     rows(6)
 *
 *     content {
 *         for (skill in skills) {
 *             item {
 *                 material(skill.icon)
 *                 name("&a${skill.name}")
 *                 onClick { ctx -> ctx.open(SkillDetailMenu(skill)) }
 *             }
 *         }
 *     }
 *
 *     border {
 *         back(49)
 *         close(53)
 *         button(45) {
 *             material(Material.BOOK)
 *             name("&eHelp")
 *         }
 *     }
 * }
 * ```
 */
fun borderedMenu(title: String = "&8Menu", block: BorderedMenuBuilder.() -> Unit): BorderedMenu =
    BorderedMenuBuilder(title).apply(block).build()

@MenuDslMarker
class BorderedMenuBuilder(private var title: String) {
    private var rows: Int = 6
    private val contentButtons = linkedMapOf<Int, Button>()
    private val borderButtons = linkedMapOf<Int, Button>()
    private var nextContentIndex = 0
    private var autoUpdate: Boolean = true
    private var updateAfterClick: Boolean = false
    private var hasBorder: Boolean = true
    private var autoPlace: Boolean = true
    private var borderMaterial: XMaterial = XMaterial.GRAY_STAINED_GLASS_PANE
    private var fillMaterial: XMaterial = XMaterial.BLACK_STAINED_GLASS_PANE
    private var fillMode: MenuFillMode = MenuFillMode.BORDER
    private val exemptSlots = mutableSetOf<Int>()
    private var onOpen: ((Player) -> Unit)? = null
    private var onClose: ((Player, Boolean) -> Unit)? = null

    fun title(title: String) { this.title = title }

    fun rows(rows: Int) {
        require(rows in 3..6) { "bordered menus need 3..6 rows" }
        this.rows = rows
    }

    fun autoUpdate(enabled: Boolean = true) { autoUpdate = enabled }
    fun updateAfterClick(enabled: Boolean = true) { updateAfterClick = enabled }
    fun bordered(enabled: Boolean = true) { hasBorder = enabled }

    /** Enable/disable packing order-index content into free inner slots. */
    fun autoPlaceContent(enabled: Boolean = true) { autoPlace = enabled }

    /** Material of the decorative border frame. */
    fun borderMaterial(material: XMaterial) { borderMaterial = material }

    /**
     * Which regions get decorative filler: [MenuFillMode.BORDER] (default, frame only),
     * [MenuFillMode.INNER] (non-border slots only), [MenuFillMode.BOTH], or [MenuFillMode.NONE].
     */
    fun fillMode(mode: MenuFillMode) { fillMode = mode }

    /** Material used to fill the non-border slots. */
    fun fillMaterial(material: XMaterial) { fillMaterial = material }

    /**
     * Fill the non-border slots with [material], leaving the frame as-is.
     *
     * ```kotlin
     * borderedMenu("&8Shop") {
     *     borderMaterial(XMaterial.BLUE_STAINED_GLASS_PANE)
     *     fillInner(XMaterial.BLACK_STAINED_GLASS_PANE)
     * }
     * ```
     */
    fun fillInner(
        material: XMaterial = XMaterial.BLACK_STAINED_GLASS_PANE,
        keepBorder: Boolean = true
    ) {
        fillMaterial = material
        fillMode = if (keepBorder) MenuFillMode.BOTH else MenuFillMode.INNER
    }

    /**
     * Inventory slots that keep absolute positions (never auto-packed).
     * Useful when mixing order indexes with fixed content slots.
     */
    fun autoPlaceExceptions(vararg slots: Int) {
        exemptSlots.addAll(slots.toList())
    }

    fun onOpen(handler: (Player) -> Unit) { onOpen = handler }
    fun onClose(handler: (Player, Boolean) -> Unit) { onClose = handler }

    /**
     * Declare content buttons. Each [ContentScope.item] / [ContentScope.button]
     * is ordered into the inner content region (index 0 = first inner slot).
     */
    fun content(block: ContentScope.() -> Unit) {
        ContentScope().apply(block)
    }

    /** Declare absolute-slot buttons on the glass frame. */
    fun border(block: BorderScope.() -> Unit) {
        BorderScope().apply(block)
    }

    @MenuDslMarker
    inner class ContentScope {
        private val parent get() = this@BorderedMenuBuilder

        /** Append a content button (next free content index). */
        fun item(block: ButtonBuilder.() -> Unit) {
            parent.contentButtons[parent.nextContentIndex++] = ButtonBuilder().apply(block).build()
        }

        fun item(button: Button) {
            parent.contentButtons[parent.nextContentIndex++] = button
        }

        fun button(block: ButtonBuilder.() -> Unit) = item(block)
        fun button(button: Button) = item(button)

        /** Place at a specific content **order index** (not inventory slot). */
        fun at(index: Int, block: ButtonBuilder.() -> Unit) {
            parent.contentButtons[index] = ButtonBuilder().apply(block).build()
            if (index >= parent.nextContentIndex) parent.nextContentIndex = index + 1
        }

        fun at(index: Int, button: Button) {
            parent.contentButtons[index] = button
            if (index >= parent.nextContentIndex) parent.nextContentIndex = index + 1
        }

        /**
         * Place at an absolute inventory slot (exempt from auto-pack).
         * The slot is added to [BorderedMenu.autoPlaceExceptions].
         */
        fun slot(absoluteSlot: Int, block: ButtonBuilder.() -> Unit) {
            parent.exemptSlots += absoluteSlot
            parent.contentButtons[absoluteSlot] = ButtonBuilder().apply(block).build()
        }

        fun slot(absoluteSlot: Int, button: Button) {
            parent.exemptSlots += absoluteSlot
            parent.contentButtons[absoluteSlot] = button
        }

        /** Add many buttons in order. */
        fun items(buttons: Iterable<Button>) {
            buttons.forEach { item(it) }
        }
    }

    @MenuDslMarker
    inner class BorderScope {
        private val parent get() = this@BorderedMenuBuilder

        fun button(slot: Int, block: ButtonBuilder.() -> Unit) {
            parent.borderButtons[slot] = ButtonBuilder().apply(block).build()
        }

        fun button(slot: Int, button: Button) {
            parent.borderButtons[slot] = button
        }

        fun button(row: Int, col: Int, block: ButtonBuilder.() -> Unit) {
            button(Slots.of(row, col), block)
        }

        /** History-aware back button. */
        fun back(slot: Int = Slots.bottomCenter(parent.rows), destination: String? = null) {
            parent.borderButtons[slot] = BackButton.history(destination)
        }

        fun close(slot: Int = Slots.of(parent.rows - 1, 8)) {
            parent.borderButtons[slot] = me.jordanfails.unify.menu.dsl.button {
                material(XMaterial.BARRIER)
                name("&cClose")
                lore("&7Click to close this menu.")
                onClick { it.close() }
            }
        }

        fun filler(slot: Int, material: Material = XSupport.resolve(XMaterial.GRAY_STAINED_GLASS_PANE)) {
            parent.borderButtons[slot] = me.jordanfails.unify.menu.dsl.filler(material)
        }
    }

    fun build(): BorderedMenu {
        val self = this
        return object : BorderedMenu() {
            init {
                this.autoUpdate = self.autoUpdate
                this.updateAfterClick = self.updateAfterClick
                this.autoPlaceContentButtons = self.autoPlace
                this.autoPlaceExceptions = self.exemptSlots.toSet()
                this.borderMaterial = self.borderMaterial
                this.fillMaterial = self.fillMaterial
                this.fillMode = self.fillMode
            }

            override fun getMinSize(): Int = self.rows * 9

            override fun getTitle(player: Player): String = self.title

            override fun hasBorder(): Boolean = self.hasBorder

            override fun getContentButtons(player: Player): MutableMap<Int, Button> =
                self.contentButtons.toMutableMap()

            override fun getBorderButtons(player: Player): Map<Int, Button> =
                self.borderButtons.toMap()

            override fun onOpen(player: Player) {
                self.onOpen?.invoke(player)
            }

            override fun onClose(player: Player, manualClose: Boolean) {
                super.onClose(player, manualClose)
                self.onClose?.invoke(player, manualClose)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Paginated bordered menu DSL
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Build a [PaginatedBorderedMenu] with a Kotlin DSL.
 *
 * ```kotlin
 * paginatedMenu("&8Players") {
 *     rows(6)
 *     entries {
 *         for (p in online) {
 *             item {
 *                 material(Material.PLAYER_HEAD)
 *                 name("&a${p.name}")
 *                 onClick { /* ... */ }
 *             }
 *         }
 *     }
 *     border {
 *         back(49)
 *         close(53)
 *     }
 * }
 * ```
 */
fun paginatedMenu(title: String = "&8Menu", block: PaginatedMenuBuilder.() -> Unit): PaginatedBorderedMenu =
    PaginatedMenuBuilder(title).apply(block).build()

@MenuDslMarker
class PaginatedMenuBuilder(private var title: String) {
    private var rows: Int = 6
    private val entries = mutableListOf<Button>()
    private val fixedEntries = linkedMapOf<Int, Button>()
    private val borderButtons = linkedMapOf<Int, Button>()
    private var autoUpdate: Boolean = false
    private var updateAfterClick: Boolean = false
    private var hasBorder: Boolean = true
    private var borderMaterial: XMaterial = XMaterial.BLACK_STAINED_GLASS_PANE
    private var fillMaterial: XMaterial = XMaterial.BLACK_STAINED_GLASS_PANE
    private var fillMode: MenuFillMode = MenuFillMode.BORDER
    private val exemptSlots = mutableSetOf<Int>()
    private var onOpen: ((Player) -> Unit)? = null
    private var onClose: ((Player, Boolean) -> Unit)? = null

    fun title(title: String) { this.title = title }

    fun rows(rows: Int) {
        require(rows in 3..6)
        this.rows = rows
    }

    fun autoUpdate(enabled: Boolean = true) { autoUpdate = enabled }
    fun updateAfterClick(enabled: Boolean = true) { updateAfterClick = enabled }
    fun bordered(enabled: Boolean = true) { hasBorder = enabled }

    /**
     * Absolute inventory slots that never paginate (shown every page).
     */
    fun autoPlaceExceptions(vararg slots: Int) {
        exemptSlots.addAll(slots.toList())
    }

    /** Material of the decorative border frame. */
    fun borderMaterial(material: XMaterial) { borderMaterial = material }

    /**
     * Which regions get decorative filler: [MenuFillMode.BORDER] (default, frame only),
     * [MenuFillMode.INNER] (non-border slots only), [MenuFillMode.BOTH], or [MenuFillMode.NONE].
     */
    fun fillMode(mode: MenuFillMode) { fillMode = mode }

    /** Material used to fill the non-border slots. */
    fun fillMaterial(material: XMaterial) { fillMaterial = material }

    /**
     * Fill the non-border slots with [material] — including the empty page slots of a partially
     * filled last page — leaving the frame as-is.
     */
    fun fillInner(
        material: XMaterial = XMaterial.BLACK_STAINED_GLASS_PANE,
        keepBorder: Boolean = true
    ) {
        fillMaterial = material
        fillMode = if (keepBorder) MenuFillMode.BOTH else MenuFillMode.INNER
    }

    fun onOpen(handler: (Player) -> Unit) { onOpen = handler }
    fun onClose(handler: (Player, Boolean) -> Unit) { onClose = handler }

    fun entries(block: EntryScope.() -> Unit) {
        EntryScope().apply(block)
    }

    /** Alias for [entries]. */
    fun content(block: EntryScope.() -> Unit) = entries(block)

    fun border(block: PaginatedBorderScope.() -> Unit) {
        PaginatedBorderScope().apply(block)
    }

    @MenuDslMarker
    inner class EntryScope {
        private val parent get() = this@PaginatedMenuBuilder

        fun item(block: ButtonBuilder.() -> Unit) {
            parent.entries += ButtonBuilder().apply(block).build()
        }

        fun item(button: Button) {
            parent.entries += button
        }

        fun button(block: ButtonBuilder.() -> Unit) = item(block)
        fun button(button: Button) = item(button)

        fun items(buttons: Iterable<Button>) {
            parent.entries.addAll(buttons)
        }

        /**
         * Fixed absolute inventory slot — shown on every page, never paginated.
         */
        fun slot(absoluteSlot: Int, block: ButtonBuilder.() -> Unit) {
            parent.exemptSlots += absoluteSlot
            parent.fixedEntries[absoluteSlot] = ButtonBuilder().apply(block).build()
        }

        fun slot(absoluteSlot: Int, button: Button) {
            parent.exemptSlots += absoluteSlot
            parent.fixedEntries[absoluteSlot] = button
        }
    }

    @MenuDslMarker
    inner class PaginatedBorderScope {
        private val parent get() = this@PaginatedMenuBuilder

        fun button(slot: Int, block: ButtonBuilder.() -> Unit) {
            parent.borderButtons[slot] = ButtonBuilder().apply(block).build()
        }

        fun button(slot: Int, button: Button) {
            parent.borderButtons[slot] = button
        }

        fun back(slot: Int = Slots.bottomCenter(parent.rows), destination: String? = null) {
            parent.borderButtons[slot] = BackButton.history(destination)
        }

        fun close(slot: Int = Slots.of(parent.rows - 1, 8)) {
            parent.borderButtons[slot] = me.jordanfails.unify.menu.dsl.button {
                material(XMaterial.BARRIER)
                name("&cClose")
                lore("&7Click to close this menu.")
                onClick { it.close() }
            }
        }
    }

    fun build(): PaginatedBorderedMenu {
        val self = this
        return object : PaginatedBorderedMenu() {
            init {
                this.autoUpdate = self.autoUpdate
                this.updateAfterClick = self.updateAfterClick
                this.autoPlaceExceptions = self.exemptSlots.toSet()
                this.borderMaterial = self.borderMaterial
                this.fillMaterial = self.fillMaterial
                this.fillMode = self.fillMode
            }

            override fun getMinSize(): Int = self.rows * 9

            override fun getMenuSize(): Int = self.rows * 9

            override fun getPrePaginatedTitle(player: Player): String = self.title

            override fun hasBorder(): Boolean = self.hasBorder

            override fun getAllPagesButtons(player: Player): Map<Int, Button> {
                val map = linkedMapOf<Int, Button>()
                // Paginated entries use order indexes; fixed slots overlay and stay exempt.
                self.entries.forEachIndexed { index, button -> map[index] = button }
                self.fixedEntries.forEach { (slot, button) -> map[slot] = button }
                return map
            }

            override fun getBorderButtons(player: Player): Map<Int, Button> =
                self.borderButtons.toMap()

            override fun onOpen(player: Player) {
                self.onOpen?.invoke(player)
            }

            override fun onClose(player: Player, manualClose: Boolean) {
                super.onClose(player, manualClose)
                self.onClose?.invoke(player, manualClose)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Interactive (tinkerer / crafting) menu DSL
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Build an [InteractiveMenu] with a Kotlin DSL.
 *
 * ```kotlin
 * interactiveMenu("&8Tinkerer") {
 *     rows(6)
 *     fillBackground()
 *
 *     input(11) { ghost(Material.DIAMOND_SWORD, "&7Weapon") }
 *     input(13) { ghost(Material.EMERALD, "&7Catalyst") }
 *     output(22)
 *
 *     results { player ->
 *         val weapon = getSlotItem(11) ?: return@results mapOf(22 to null)
 *         mapOf(22 to weapon.clone())
 *     }
 *
 *     onResultTaken { player, slot, taken, amount ->
 *         consumeFromSlot(player, 11, 1)
 *         consumeFromSlot(player, 13, 1)
 *     }
 *
 *     fixed(49) {
 *         material(Material.BARRIER)
 *         name("&cClose")
 *         onClick { it.close() }
 *     }
 * }
 * ```
 */
fun interactiveMenu(title: String = "&8Menu", block: InteractiveMenuBuilder.() -> Unit): InteractiveMenu =
    InteractiveMenuBuilder(title).apply(block).build()

@MenuDslMarker
class InteractiveMenuBuilder(private var title: String) {
    private var rows: Int = 6
    private val interactive = linkedSetOf<Int>()
    private val inputs = linkedSetOf<Int>()
    private val outputs = linkedSetOf<Int>()
    private val fixed = linkedMapOf<Int, Button>()
    private val placeholders = mutableMapOf<Int, (Player) -> ItemStack?>()
    private val filters = mutableMapOf<Int, (ItemStack) -> Boolean>()
    private val maxAmounts = mutableMapOf<Int, Int>()
    private var fillBg: Boolean = false
    private var returnOnClose: Boolean = true
    private var autoResults: Boolean = true
    private var resultsComputer: (InteractiveMenu.(Player) -> Map<Int, ItemStack?>)? = null
    private var resultTaken: (InteractiveMenu.(Player, Int, ItemStack, Int) -> Unit)? = null
    private var slotChange: (InteractiveMenu.(Player, Int, ItemStack?, ItemStack?) -> Unit)? = null
    private var contentsChanged: (InteractiveMenu.(Player) -> Unit)? = null

    fun title(title: String) { this.title = title }
    fun rows(rows: Int) {
        require(rows in 1..6)
        this.rows = rows
    }

    fun fillBackground(enabled: Boolean = true) { fillBg = enabled }
    fun returnItemsOnClose(enabled: Boolean = true) { returnOnClose = enabled }
    fun autoRefreshResults(enabled: Boolean = true) { autoResults = enabled }

    /** Fully interactive slot (place + take). */
    fun slot(index: Int, block: ItemSlotScope.() -> Unit = {}) {
        interactive += index
        ItemSlotScope(index).apply(block)
    }

    /** Input-only slot (ingredients). */
    fun input(index: Int, block: ItemSlotScope.() -> Unit = {}) {
        inputs += index
        ItemSlotScope(index).apply(block)
    }

    /** Output-only slot (results). */
    fun output(index: Int, block: ItemSlotScope.() -> Unit = {}) {
        outputs += index
        ItemSlotScope(index).apply(block)
    }

    fun fixed(index: Int, button: Button) {
        fixed[index] = button
    }

    fun fixed(index: Int, block: ButtonBuilder.() -> Unit) {
        fixed[index] = ButtonBuilder().apply(block).build()
    }

    fun results(computer: InteractiveMenu.(Player) -> Map<Int, ItemStack?>) {
        resultsComputer = computer
    }

    fun onResultTaken(handler: InteractiveMenu.(Player, Int, ItemStack, Int) -> Unit) {
        resultTaken = handler
    }

    fun onSlotChange(handler: InteractiveMenu.(Player, Int, ItemStack?, ItemStack?) -> Unit) {
        slotChange = handler
    }

    fun onContentsChanged(handler: InteractiveMenu.(Player) -> Unit) {
        contentsChanged = handler
    }

    @MenuDslMarker
    inner class ItemSlotScope(private val index: Int) {
        private val parent get() = this@InteractiveMenuBuilder

        fun filter(predicate: (ItemStack) -> Boolean) {
            parent.filters[index] = predicate
        }

        fun maxAmount(amount: Int) {
            parent.maxAmounts[index] = amount
        }

        fun placeholder(stack: ItemStack) {
            parent.placeholders[index] = { stack.clone() }
        }

        fun placeholder(factory: (Player) -> ItemStack?) {
            parent.placeholders[index] = factory
        }

        fun ghost(material: Material, name: String, vararg lore: String) {
            parent.placeholders[index] = {
                InteractiveMenu.ghost(material, name, *lore)
            }
        }
    }

    fun build(): InteractiveMenu {
        val self = this
        return object : InteractiveMenu(self.title) {
            init {
                returnItemsOnClose = self.returnOnClose
                autoRefreshResults = self.autoResults
                fillBackground = self.fillBg
            }

            override fun getMinSize(): Int = self.rows * 9
            override fun getTitle(player: Player): String = self.title
            override fun getInteractiveSlots(): Set<Int> = self.interactive
            override fun getInputOnlySlots(): Set<Int> = self.inputs
            override fun getOutputOnlySlots(): Set<Int> = self.outputs

            override fun getFixedButtons(player: Player): Map<Int, Button> = self.fixed.toMap()

            override fun getEmptyPlaceholder(player: Player, slot: Int): ItemStack? =
                self.placeholders[slot]?.invoke(player)

            override fun getAllowedItemFilter(slot: Int): ((ItemStack) -> Boolean)? =
                self.filters[slot]

            override fun getMaxAmount(slot: Int): Int =
                self.maxAmounts[slot] ?: super.getMaxAmount(slot)

            override fun computeResults(player: Player): Map<Int, ItemStack?> =
                self.resultsComputer?.invoke(this, player) ?: emptyMap()

            override fun onResultTaken(player: Player, slot: Int, taken: ItemStack, amount: Int) {
                self.resultTaken?.invoke(this, player, slot, taken, amount)
            }

            override fun onSlotChange(player: Player, slot: Int, oldItem: ItemStack?, newItem: ItemStack?) {
                self.slotChange?.invoke(this, player, slot, oldItem, newItem)
            }

            override fun onContentsChanged(player: Player) {
                if (self.contentsChanged != null) {
                    self.contentsChanged?.invoke(this, player)
                } else {
                    super.onContentsChanged(player)
                }
            }
        }
    }
}
