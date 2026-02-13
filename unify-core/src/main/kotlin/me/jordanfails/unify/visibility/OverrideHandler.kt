package me.jordanfails.unify.visibility

import org.bukkit.entity.Player

abstract class OverrideHandler(val name: String, val weight: Int) {
    abstract fun getAction(toRefresh: Player, refreshFor: Player): OverrideAction
}