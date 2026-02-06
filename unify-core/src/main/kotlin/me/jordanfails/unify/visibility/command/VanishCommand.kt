package me.jordanfails.unify.visibility.command

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import co.aikar.commands.annotation.Optional
import me.jordanfails.unify.utils.CC
import me.jordanfails.unify.visibility.VanishHandler
import me.jordanfails.unify.visibility.VisibilityHandler
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

@CommandAlias("vanish|v")
class VanishCommand : BaseCommand() {

    @Default
    @CommandPermission(VanishHandler.PERMISSION_USE)
    @CommandCompletion("@players")
    fun onVanish(sender: CommandSender, @Optional target: Player?) {
        val actualTarget = when {
            target != null -> target
            sender is Player -> sender
            else -> {
                sender.sendMessage(CC.translate("&cOnly players can use this command without a target."))
                return
            }
        }

        val isSelf = sender is Player && sender.uniqueId == actualTarget.uniqueId
        if (!isSelf && !sender.hasPermission(VanishHandler.PERMISSION_OTHER)) {
            sender.sendMessage(CC.translate("&cYou don't have permission to vanish other players."))
            return
        }

        val nowVanished = VanishHandler.toggle(actualTarget)
        VisibilityHandler.update(actualTarget)

        val status = if (nowVanished) "&aVanished" else "&cVisible"
        if (isSelf) {
            sender.sendMessage(CC.translate("$status&7."))
        } else {
            sender.sendMessage(CC.translate("&aUpdated &e${actualTarget.name}&a: $status&7."))
            actualTarget.sendMessage(CC.translate("&eYour visibility was updated by &6${sender.name}&e: $status&7."))
        }
    }
}
