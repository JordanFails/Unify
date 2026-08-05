package me.jordanfails.unify.menu.menus.menus

import com.cryptomorin.xseries.XMaterial
import com.cryptomorin.xseries.XSound
import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.menu.Menu
import me.jordanfails.unify.utils.CC
import me.jordanfails.unify.utils.ItemBuilder
import me.jordanfails.unify.utils.XSupport
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack

class CosmicConfirmMenu(
    private val title: String = "Are you sure?",
    private val confirmName: String = "&a&lCONFIRM",
    private val confirmLore: List<String> = listOf(
        "",
        "&7Click to &aconfirm &7this action.",
        "",
        "&aCLICK TO CONFIRM",
    ),
    private val cancelName: String = "&c&lCANCEL",
    private val cancelLore: List<String> = listOf(
        "",
        "&7Click to &ccancel &7this action.",
        "",
        "&cCLICK TO CANCEL",
    ),
    private val middleName: String = "&e&lAre you sure?",
    private val middleLore: List<String> = emptyList(),
    /** Optional center item (e.g. the product). Defaults to a paper with [middleName]/[middleLore]. */
    private val middleItem: ItemStack? = null,
    private val confirmMaterial: Material = XSupport.resolve(XMaterial.LIME_STAINED_GLASS_PANE),
    private val cancelMaterial: Material = XSupport.resolve(XMaterial.RED_STAINED_GLASS_PANE),
    /**
     * When true (default Cosmic): confirm on left (0–3), cancel on right (5–8).
     * When false: swapped — useful for destructive confirms that Cosmic shows red-on-left.
     */
    private val confirmOnLeft: Boolean = true,
    /** Message sent when cancelled via click or inventory close. Null = silent. */
    private val cancelMessage: String? = null,
    private val closeOnAction: Boolean = true,
    private val callback: (Boolean) -> Unit,
) : Menu() {

    private var resolved = false

    override fun getTitle(player: Player): String = CC.translate(title)

    override fun getMinSize(): Int = 9

    override fun getButtons(player: Player): Map<Int, Button> {
        val buttons = mutableMapOf<Int, Button>()

        val confirmSlots = if (confirmOnLeft) LEFT_SLOTS else RIGHT_SLOTS
        val cancelSlots = if (confirmOnLeft) RIGHT_SLOTS else LEFT_SLOTS

        val confirmStack = pane(
            material = confirmMaterial,
            name = confirmName,
            lore = confirmLore,
        )
        val cancelStack = pane(
            material = cancelMaterial,
            name = cancelName,
            lore = cancelLore,
        )

        confirmSlots.forEach { slot ->
            buttons[slot] = actionButton(confirmStack) { resolve(player, confirmed = true) }
        }
        cancelSlots.forEach { slot ->
            buttons[slot] = actionButton(cancelStack) { resolve(player, confirmed = false) }
        }

        buttons[CENTER_SLOT] = object : Button() {
            override fun getButtonItem(player: Player): ItemStack = buildMiddleItem()
        }

        return buttons
    }

    override fun onClose(player: Player, manualClose: Boolean) {
        super.onClose(player, manualClose)
        // ESC / disconnect = cancel (only once).
        if (!resolved) {
            resolve(player, confirmed = false, fromClose = true)
        }
    }

    private fun resolve(player: Player, confirmed: Boolean, fromClose: Boolean = false) {
        if (resolved) return
        resolved = true

        if (confirmed) {
            player.playSound(
                player.location,
                XSupport.resolveSound(XSound.UI_BUTTON_CLICK),
                1f,
                1.2f,
            )
        } else {
            player.playSound(
                player.location,
                XSupport.resolveSound(XSound.BLOCK_NOTE_BLOCK_BASEDRUM),
                1f,
                0.6f,
            )
            cancelMessage?.let { player.sendMessage(CC.translate(it)) }
        }

        // Don't closeInventory() when already inside InventoryCloseEvent.
        if (closeOnAction && !fromClose) {
            player.closeInventory()
        }

        callback(confirmed)
    }

    private fun buildMiddleItem(): ItemStack {
        val base = middleItem?.clone()
        if (base != null) {
            // Apply name/lore only when provided; keep custom stacks intact if names empty-ish.
            val builder = ItemBuilder(base)
            if (middleName.isNotBlank()) builder.name(CC.translate(middleName))
            if (middleLore.isNotEmpty()) builder.lore(middleLore.map { CC.translate(it) })
            return builder.hideAllFlags().build()
        }
        return ItemBuilder(XMaterial.PAPER)
            .name(CC.translate(middleName))
            .lore(middleLore.map { CC.translate(it) })
            .hideAllFlags()
            .build()
    }

    private fun pane(material: Material, name: String, lore: List<String>): ItemStack =
        ItemBuilder(material)
            .name(CC.translate(name))
            .lore(lore.map { CC.translate(it) })
            .hideAllFlags()
            .build()

    private fun actionButton(stack: ItemStack, onClick: () -> Unit): Button =
        object : Button() {
            override fun getButtonItem(player: Player): ItemStack = stack.clone()

            override fun clicked(player: Player, slot: Int, clickType: ClickType, view: InventoryView) {
                onClick()
            }
        }

    companion object {
        private val LEFT_SLOTS = listOf(0, 1, 2, 3)
        private val RIGHT_SLOTS = listOf(5, 6, 7, 8)
        private const val CENTER_SLOT = 4


        @JvmStatic
        fun destructive(
            title: String,
            confirmName: String = "&c&lCONFIRM",
            confirmLore: List<String> = listOf("", "&7Click to confirm.", "", "&cCLICK TO CONFIRM"),
            cancelName: String = "&a&lRETURN",
            cancelLore: List<String> = listOf("", "&7Go back.", "", "&aCLICK TO RETURN"),
            middleName: String = "&e&lAre you sure?",
            middleLore: List<String> = emptyList(),
            middleItem: ItemStack? = null,
            cancelMessage: String? = null,
            callback: (Boolean) -> Unit,
        ): CosmicConfirmMenu = CosmicConfirmMenu(
            title = title,
            confirmName = confirmName,
            confirmLore = confirmLore,
            cancelName = cancelName,
            cancelLore = cancelLore,
            middleName = middleName,
            middleLore = middleLore,
            middleItem = middleItem,
            confirmMaterial = XSupport.resolve(XMaterial.RED_STAINED_GLASS_PANE),
            cancelMaterial = XSupport.resolve(XMaterial.LIME_STAINED_GLASS_PANE),
            confirmOnLeft = true,
            cancelMessage = cancelMessage,
            callback = callback,
        )
    }
}
