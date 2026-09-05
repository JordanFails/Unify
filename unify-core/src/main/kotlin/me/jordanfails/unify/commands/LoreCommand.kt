package me.jordanfails.unify.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import co.aikar.commands.annotation.Description
import me.jordanfails.unify.menu.menus.menus.LoreMenu
import me.jordanfails.unify.utils.CC
import org.bukkit.Material
import org.bukkit.entity.Player

@CommandAlias("lore|lores")
@CommandPermission("unify.command.lore")
@Description("Edit the lore of the item in your hand, line by line")
class LoreCommand : BaseCommand() {

    @Default
    @Suppress("DEPRECATION")
    fun onLore(player: Player) {
        val item = player.inventory.itemInHand
        if (item.type == Material.AIR || item.amount <= 0) {
            player.sendMessage(CC.translate("&cHold an item to edit its lore."))
            return
        }

        LoreMenu.edit(player, item)
    }
}
