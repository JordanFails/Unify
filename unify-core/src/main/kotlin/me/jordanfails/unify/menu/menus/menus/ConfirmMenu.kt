package me.jordanfails.unify.menu.menus.menus

import com.cryptomorin.xseries.XMaterial
import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.menu.Menu
import me.jordanfails.unify.utils.CC
import me.jordanfails.unify.utils.ItemBuilder
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack

class ConfirmMenu(
    val titleText: String = "Are you sure?",
    val extraInfo: MutableList<String> = mutableListOf(),
    val confirmButtonOnLeft: Boolean = true,
    val callback: (Boolean) -> Unit
) : Menu() {

    var called = false

    override fun getTitle(player: Player): String {
        return CC.translate(titleText)
    }

    override fun getButtons(player: Player): MutableMap<Int, Button> {
        val buttons = mutableMapOf<Int, Button>()

        // Decorative glass background
        for (i in 0 until 9) {
            buttons[i] = object : Button() {
                override fun getButtonItem(player: Player): ItemStack {
                    return ItemBuilder(XMaterial.BLACK_STAINED_GLASS_PANE)
                        .data(15.toShort())
                        .name(CC.translate("&r"))
                        .build()
                }
            }
        }

        // Confirm and deny buttons
        val confirmSlot = if (confirmButtonOnLeft) 2 else 6
        val denySlot = if (confirmButtonOnLeft) 6 else 2

        buttons[confirmSlot] = object : Button() {
            override fun getButtonItem(player: Player): ItemStack {
                return ItemBuilder(Material.GREEN_DYE)
                    .name(CC.translate("&a&lConfirm"))
                    .lore(
                        mutableListOf(
                            CC.translate("&7Click to confirm this action."),
                            CC.translate("&7 ")
                        ).apply { addAll(extraInfo.map { CC.translate(it) }) }
                    )
                    .data(10)
                    .build()
            }

            override fun clicked(player: Player, slot: Int, clickType: ClickType, view: InventoryView) {
                if (!called) {
                    called = true
                    player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1.2f)
                    player.closeInventory()
                    callback(true)
                }
            }
        }

        buttons[denySlot] = object : Button() {
            override fun getButtonItem(player: Player): ItemStack {
                return ItemBuilder(Material.RED_DYE)
                    .name(CC.translate("&c&lCancel"))
                    .lore(
                        listOf(
                            CC.translate("&7Click to cancel."),
                            CC.translate("&7No changes will be made.")
                        )
                    )
                    .data(1)
                    .build()
            }

            override fun clicked(player: Player, slot: Int, clickType: ClickType, view: InventoryView) {
                if (!called) {
                    called = true
                    player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1f, 0.6f)
                    player.closeInventory()
                    callback(false)
                }
            }
        }

        // Optional: center info display if there’s extra info
        if (extraInfo.isNotEmpty()) {
            buttons[4] = object : Button() {
                override fun getButtonItem(player: Player): ItemStack {
                    return ItemBuilder(Material.OAK_SIGN)
                        .name(CC.translate("&e&lInfo"))
                        .lore(extraInfo.map { CC.translate(it) })
                        .build()
                }
            }
        }

        return buttons
    }

    override fun onClose(player: Player, manualClose: Boolean) {
        if (!called) {
            called = true
            callback(false)
        }
    }

    override fun getMinSize(): Int {
        return 9
    }
}