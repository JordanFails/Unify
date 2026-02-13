package me.jordanfails.unify.visibility

import org.bukkit.entity.Player

object VanishVisibilityAdapter : VisibilityAdapter("Vanish", 100) {
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
