package me.jordanfails.unify.menu.menus.menus

import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.menu.Menu
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class SelectItemStackMenu(
    private val title: String = "Select an item...",
    internal val select: (ItemStack) -> Unit
) : Menu() {

    override fun getTitle(player: Player): String {
        return title
    }

    override fun getButtons(player: Player): Map<Int, Button> {
        val buttons = hashMapOf<Int, Button>()

        buttons[4] = GuideButton()

        return buttons
    }

    private inner class GuideButton : Button() {
        override fun getName(player: Player): String {
            return "${ChatColor.GREEN}${ChatColor.BOLD}Select an item..."
        }

        override fun getDescription(player: Player): MutableList<String> {
            return mutableListOf("${ChatColor.GRAY}Click on an item in your inventory to select it.")
        }

        override fun getMaterial(player: Player): Material {
            return Material.NETHER_STAR
        }
    }

}