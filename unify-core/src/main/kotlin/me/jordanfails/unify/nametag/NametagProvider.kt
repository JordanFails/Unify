package me.jordanfails.unify.nametag

import org.bukkit.entity.Player


abstract class NametagProvider(val name: String, val weight: Int) {

    abstract fun fetchNametag(toRefresh: Player, refreshFor: Player): NametagInfo

    class DefaultNametagProvider : NametagProvider("Default Provider", 0) {
        override fun fetchNametag(toRefresh: Player, refreshFor: Player): NametagInfo {
            return createNametag("", "")
        }
    }

    companion object {
        @JvmStatic
        fun createNametag(prefix: String, suffix: String): NametagInfo {
            return NametagHandler.getOrCreate(prefix, suffix)
        }

        @JvmStatic
        fun createNametag(prefix: String, suffix: String, displayName: String): NametagInfo {
            return NametagHandler.getOrCreate(prefix, suffix, displayName)
        }
    }

}
