package me.jordanfails.unify.nametag

import me.jordanfails.unify.UnifyCore
import org.bukkit.entity.Player


/**
 * Supplies display data for each player on every refresh; the highest-weight answer wins.
 *
 * Threading: called on Unify's nametag thread (the work grows with players x viewers), so only read
 * thread-safe state such as cached values or the player's name/UUID. Don't touch worlds, entities or inventories.
 * A provider that throws is skipped and logged once. Providers are removed when their plugin disables.
 */
abstract class NametagProvider(val name: String, val weight: Int) {

    abstract fun fetchNametag(toRefresh: Player, refreshFor: Player): NametagInfo

    class DefaultNametagProvider : NametagProvider("Default Provider", 0) {
        override fun fetchNametag(toRefresh: Player, refreshFor: Player): NametagInfo {
            return createNametag("", "", configuredLines(toRefresh, refreshFor))
        }
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun createNametag(prefix: String, suffix: String, lines: List<String> = emptyList()): NametagInfo {
            return NametagInfo("", prefix, suffix).also { it.lines = lines }
        }

        internal fun configuredLines(target: Player, viewer: Player): List<String> {
            return UnifyCore.instance.config.getStringList("nametags.lines").map {
                it.replace("{player}", target.name).replace("{viewer}", viewer.name)
            }
        }
    }

}
