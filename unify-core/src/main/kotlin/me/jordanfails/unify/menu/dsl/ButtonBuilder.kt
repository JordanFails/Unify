package me.jordanfails.unify.menu.dsl

import com.cryptomorin.xseries.XMaterial
import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.menu.Menu
import me.jordanfails.unify.utils.ItemBuilder
import me.jordanfails.unify.utils.XSupport
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack

/**
 * Fluent builder for [Button]s — the everyday replacement for anonymous
 * `object : Button()` blocks.
 *
 * ```kotlin
 * button {
 *     material = Material.DIAMOND_SWORD
 *     name = "&bExcalibur"
 *     lore("&7A legendary blade.", "&eClick to equip")
 *     glow()
 *     onClick { ctx ->
 *         ctx.soundSuccess()
 *         ctx.player.inventory.addItem(buildItem(ctx.player))
 *     }
 * }
 * ```
 *
 * Click resolution: side-specific handlers (`onLeftClick` / `onRightClick` /
 * `onShiftClick`) win when set; otherwise [onClick] runs.
 */
@MenuDslMarker
class ButtonBuilder {
    var material: Material = Material.STONE
    var amount: Int = 1
    var name: String = " "
    private val loreLines = mutableListOf<String>()
    private var glow: Boolean = false
    private var customItem: ItemStack? = null
    private var itemFactory: ((Player) -> ItemStack)? = null
    private var clickHandler: ((ClickContext) -> Unit)? = null
    private var leftClick: ((ClickContext) -> Unit)? = null
    private var rightClick: ((ClickContext) -> Unit)? = null
    private var shiftClick: ((ClickContext) -> Unit)? = null
    private var cancel: Boolean = true
    private var moveable: Boolean = false
    private var removable: Boolean = false
    private var animated: Boolean = false
    private var animationInterval: Long = 500L

    fun material(material: Material) = apply { this.material = material }
    fun material(xMaterial: XMaterial) = apply {
        this.material = XSupport.resolve(xMaterial)
    }

    fun amount(amount: Int) = apply { this.amount = amount.coerceAtLeast(1) }

    fun name(name: String) = apply { this.name = name }

    fun lore(vararg lines: String) = apply {
        loreLines.clear()
        loreLines += lines
    }

    fun lore(lines: Collection<String>) = apply {
        loreLines.clear()
        loreLines += lines
    }

    fun addLore(vararg lines: String) = apply { loreLines += lines }

    fun glow(enabled: Boolean = true) = apply { glow = enabled }

    /** Use a finished [ItemStack] instead of material/name/lore. */
    fun item(stack: ItemStack) = apply { customItem = stack.clone() }

    /** Per-viewer item factory (permissions, dynamic lore, etc.). */
    fun item(factory: (Player) -> ItemStack) = apply { itemFactory = factory }

    fun onClick(handler: (ClickContext) -> Unit) = apply { clickHandler = handler }
    fun onLeftClick(handler: (ClickContext) -> Unit) = apply { leftClick = handler }
    fun onRightClick(handler: (ClickContext) -> Unit) = apply { rightClick = handler }
    fun onShiftClick(handler: (ClickContext) -> Unit) = apply { shiftClick = handler }

    fun cancel(cancel: Boolean = true) = apply { this.cancel = cancel }
    fun moveable(moveable: Boolean = true) = apply { this.moveable = moveable }
    fun removable(removable: Boolean = true) = apply { this.removable = removable }

    fun animated(intervalMs: Long = 500L) = apply {
        animated = true
        animationInterval = intervalMs
    }

    fun buildItem(player: Player): ItemStack {
        itemFactory?.let { return it(player) }
        customItem?.let { return it.clone() }

        val builder = ItemBuilder(material)
            .amount(amount)
            .name(name)
            .lore(loreLines)
        if (glow) builder.glow()
        return builder.build()
    }

    fun build(menu: Menu? = null): Button {
        val self = this
        return object : Button() {
            override fun getButtonItem(player: Player): ItemStack = self.buildItem(player)

            override fun clicked(player: Player, slot: Int, clickType: ClickType, view: InventoryView) {
                val ctx = ClickContext(
                    player = player,
                    slot = slot,
                    click = clickType,
                    view = view,
                    menu = menu ?: Menu.currentlyOpenedMenus[player.uniqueId]
                )
                val handler = when {
                    clickType.isShiftClick && self.shiftClick != null -> self.shiftClick
                    clickType.isLeftClick && self.leftClick != null -> self.leftClick
                    clickType.isRightClick && self.rightClick != null -> self.rightClick
                    else -> self.clickHandler
                }
                handler?.invoke(ctx)
            }

            override fun shouldCancel(player: Player, slot: Int, clickType: ClickType): Boolean = self.cancel
            override fun isMoveable(): Boolean = self.moveable
            override fun isRemovable(): Boolean = self.removable
            override fun isAnimated(): Boolean = self.animated
            override fun getAnimationInterval(): Long = self.animationInterval
        }
    }
}

/**
 * Build a [Button] with a fluent DSL.
 *
 * ```kotlin
 * val close = button {
 *     material(Material.BARRIER)
 *     name("&cClose")
 *     onClick { it.close() }
 * }
 * ```
 */
fun button(block: ButtonBuilder.() -> Unit): Button =
    ButtonBuilder().apply(block).build()

/**
 * Build a [Button] bound to a specific [menu] for richer [ClickContext].
 */
fun button(menu: Menu, block: ButtonBuilder.() -> Unit): Button =
    ButtonBuilder().apply(block).build(menu)

/**
 * Quick one-liner button.
 */
fun button(
    material: Material,
    name: String,
    vararg lore: String,
    onClick: (ClickContext) -> Unit = {}
): Button = button {
    material(material)
    name(name)
    lore(*lore)
    onClick(onClick)
}

/**
 * Display-only button (no click action beyond cancel).
 */
fun displayButton(
    material: Material,
    name: String,
    vararg lore: String
): Button = button {
    material(material)
    name(name)
    lore(*lore)
}

/**
 * Glass filler.
 */
fun filler(material: Material = XSupport.resolve(XMaterial.GRAY_STAINED_GLASS_PANE)): Button =
    Button.placeholder(material, " ")
