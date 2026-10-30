package me.jordanfails.unify.request

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.Default
import co.aikar.commands.annotation.Subcommand
import me.jordanfails.unify.utils.CC
import org.bukkit.entity.Player
import java.util.UUID

@CommandAlias("unifyrequest")
class RequestCommand : BaseCommand() {
    @Default
    fun list(player: Player) {
        val incoming = PlayerRequests.incoming(player)
        if (incoming.isEmpty()) player.sendMessage(CC.translate("&7You have no pending requests."))
        incoming.forEach {
            player.sendMessage(CC.translate("&e${it.title} &7— /unifyrequest accept ${it.id}"))
        }
        PlayerRequests.outgoing(player).forEach {
            player.sendMessage(CC.translate("&7Sent: ${it.title} — /unifyrequest cancel ${it.id}"))
        }
    }

    @Subcommand("accept")
    fun accept(player: Player, id: String) = respond(player, id, true)

    @Subcommand("decline")
    fun decline(player: Player, id: String) = respond(player, id, false)

    @Subcommand("cancel")
    fun cancel(player: Player, id: String) {
        val request = find(id)
        if (request?.senderId != player.uniqueId || !request.cancel()) {
            player.sendMessage(CC.translate("&cThis request is no longer available."))
        }
    }

    private fun respond(player: Player, id: String, accept: Boolean) {
        val request = find(id)
        val handled = request?.let { if (accept) it.accept(player) else it.decline(player) } ?: false
        if (!handled) player.sendMessage(CC.translate("&cThis request is no longer available."))
    }

    private fun find(id: String): PlayerRequest? {
        val uuid = try { UUID.fromString(id) } catch (_: IllegalArgumentException) { return null }
        return PlayerRequests.get(uuid)
    }
}
