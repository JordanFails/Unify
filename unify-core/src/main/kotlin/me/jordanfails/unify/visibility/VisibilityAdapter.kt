package me.jordanfails.unify.visibility

import org.bukkit.entity.Player

abstract class VisibilityAdapter(val name: String, val weight: Int) {
    abstract fun getAction(toRefresh: Player, refreshFor: Player): VisibilityAction
}