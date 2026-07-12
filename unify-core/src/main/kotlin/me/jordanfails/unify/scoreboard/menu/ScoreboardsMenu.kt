package me.jordanfails.unify.scoreboard.menu

import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.menu.pagination.PaginatedBorderedMenu
import me.jordanfails.unify.scoreboard.ScoreboardHandler
import me.jordanfails.unify.utils.CC
import me.jordanfails.unify.utils.ItemBuilder
import org.bukkit.Material
import org.bukkit.entity.Player

class ScoreboardsMenu : PaginatedBorderedMenu() {
    override fun getPrePaginatedTitle(player: Player): String {
        return "Registered Scoreboards"
    }

    override fun getAllPagesButtons(player: Player): Map<Int, Button> {
        val buttons = mutableMapOf<Int, Button>()

        ScoreboardHandler.providers().forEach {
            buttons[buttons.size] = ItemBuilder(Material.PAPER).name("&a" + it.name).lore(
                CC.translate(
                    listOf(
                        "&aWeight: &f${it.weight}",
                    )
                )
            ).toButton()
        }

        return buttons
    }
}