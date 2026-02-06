package me.jordanfails.unify.visibility

import me.jordanfails.unify.UnifyCore
import org.bukkit.entity.Player
import org.bukkit.metadata.FixedMetadataValue

object VanishHandler {

    const val METADATA_KEY = "invisible"
    const val PERMISSION_USE = "unify.vanish"
    const val PERMISSION_OTHER = "unify.vanish.other"
    const val PERMISSION_SEE = "unify.vanish.see"
    const val PERMISSION_SEE_LEGACY = "minexd.staff"

    fun isVanished(player: Player): Boolean {
        return player.getMetadata(METADATA_KEY)
            .any { it.owningPlugin == UnifyCore.instance && it.asBoolean() }
    }

    fun canSeeVanished(viewer: Player): Boolean {
        return viewer.hasPermission(PERMISSION_SEE) || viewer.hasPermission(PERMISSION_SEE_LEGACY)
    }

    fun setVanished(player: Player, vanished: Boolean) {
        if (vanished) {
            player.setMetadata(METADATA_KEY, FixedMetadataValue(UnifyCore.instance, true))
        } else {
            player.removeMetadata(METADATA_KEY, UnifyCore.instance)
        }
    }

    fun toggle(player: Player): Boolean {
        val nowVanished = !isVanished(player)
        setVanished(player, nowVanished)
        return nowVanished
    }
}
