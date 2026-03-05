package me.jordanfails.unify.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.Default
import co.aikar.commands.annotation.Name
import co.aikar.commands.annotation.Optional
import co.aikar.commands.bukkit.contexts.OnlinePlayer
import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.utils.CC
import org.bukkit.entity.Player

@CommandAlias("ping")
class PingCommand : BaseCommand() {

    @Default
    fun onPing(player: Player, @Name("target") @Optional targetArg: OnlinePlayer?) {
        val target = targetArg?.player ?: player
        val ping = UnifyCore.instance.nms?.getPing(target) ?: -1

        if (target == player) {
            player.sendMessage(CC.translate("&eYour ping: &f${ping}ms"))
        } else {
            val name = target.name
            val possessive = if (name.endsWith("s")) "'" else "'s"
            player.sendMessage(CC.translate("&e${name}${possessive} ping: &f${ping}ms"))
        }
    }
}