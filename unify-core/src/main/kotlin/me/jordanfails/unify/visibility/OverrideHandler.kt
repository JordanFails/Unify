package me.jordanfails.unify.visibility

import org.bukkit.entity.Player

interface OverrideHandler {
    fun getAction(toRefresh: Player, refreshFor: Player): OverrideAction
}