package me.jordanfails.unify.visibility

import org.bukkit.entity.Player

object VanishVisibilityAdapter : VisibilityAdapter {
    override fun getAction(toRefresh: Player, refreshFor: Player): VisibilityAction {
        if (!VanishHandler.isVanished(toRefresh)) {
            return VisibilityAction.NEUTRAL
        }

        return if (VanishHandler.canSeeVanished(refreshFor)) {
            VisibilityAction.NEUTRAL
        } else {
            VisibilityAction.HIDE
        }
    }
}
