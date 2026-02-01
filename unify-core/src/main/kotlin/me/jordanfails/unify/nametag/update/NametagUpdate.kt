package me.jordanfails.unify.nametag.update

import org.bukkit.entity.Player

internal class NametagUpdate(var toRefresh: String, var refreshFor: String?) {

    constructor(toRefresh: Player): this(toRefresh.name, null)
    constructor(toRefresh: Player, refreshFor: Player): this(toRefresh.name, refreshFor.name)

}