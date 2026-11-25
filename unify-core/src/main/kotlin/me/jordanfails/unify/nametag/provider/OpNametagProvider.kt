package me.jordanfails.unify.nametag.provider

import me.jordanfails.unify.nametag.NametagInfo
import me.jordanfails.unify.nametag.NametagProvider
import me.jordanfails.unify.utils.CC
import org.bukkit.entity.Player

class OpNametagProvider : NametagProvider("OP Nametag Provider", 10) {

    override fun fetchNametag(toRefresh: Player, refreshFor: Player): NametagInfo {
        val lines = configuredLines(toRefresh, refreshFor)
        return if (toRefresh.isOp) {
            createNametag(CC.translate("&e✿ &a"), "", lines)
        } else {
            createNametag(CC.translate("&7"), "", lines)
        }
    }
}
