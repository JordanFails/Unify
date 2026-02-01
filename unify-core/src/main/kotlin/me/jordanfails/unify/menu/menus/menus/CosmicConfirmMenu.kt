package me.jordanfails.unify.menu.menus.menus

import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.menu.Menu
import me.jordanfails.unify.utils.CC
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack

class CosmicConfirmMenu(
    val title: String = "Are you sure?",
    var confirmItem: ItemStack? = null,
    var cancelItem: ItemStack? = null,
    var middleItem: ItemStack? = null,
    var onCancelMessage: String = "Action cancelled.",
    private val callback: (Boolean) -> Unit
) : Menu() {

    private var called = false

    override fun getTitle(player: Player): String {
        return title
    }

    override fun getButtons(player: Player): Map<Int, Button> {
        val allButtons = mutableMapOf<Int, Button>()
        val confirmSlots = listOf(0, 1, 2, 3)
        val cancelSlots = listOf(5, 6, 7, 8)
        if(cancelItem == null || confirmItem == null) {
            throw IllegalArgumentException("Confirm and Cancel items must be set!")
        }
        confirmSlots.forEach {
            allButtons[it] = object : Button() {
                override fun getButtonItem(player: Player): ItemStack {
                    return confirmItem!!
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
        }

        cancelSlots.forEach {
            allButtons[it] = object : Button() {
                override fun getButtonItem(player: Player): ItemStack {
                    return cancelItem!!
                }

                override fun clicked(player: Player, slot: Int, clickType: ClickType, view: InventoryView) {
                    if (!called) {
                        called = true
                        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1f, 0.6f)
                        player.closeInventory()
                        player.sendMessage(CC.translate(onCancelMessage))
                        callback(false)
                    }
                }
            }
        }

        allButtons[4] = object : Button() {
            override fun getButtonItem(player: Player): ItemStack {
                return middleItem!!
            }
        }

        return buttons
    }

    override fun getMinSize(): Int {
        return 9
    }

    override fun onClose(player: Player, manualClose: Boolean) {
        if (!called) {
            called = true
            callback(false)
            player.sendMessage(CC.translate(onCancelMessage))
        }
    }
}
