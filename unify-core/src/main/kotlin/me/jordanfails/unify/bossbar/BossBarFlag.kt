package me.jordanfails.unify.bossbar

/**
 * Optional client effects applied while a player can see the bar.
 * No-ops on 1.8.
 */
enum class BossBarFlag {
    DARKEN_SKY,
    PLAY_BOSS_MUSIC,
    CREATE_FOG;

    companion object {
        @JvmStatic
        fun fromString(name: String): BossBarFlag? {
            val key = name.trim().uppercase().replace('-', '_').replace(' ', '_')
            return when (key) {
                "DARKEN", "SKY" -> DARKEN_SKY
                "MUSIC", "BOSS_MUSIC", "PLAY_MUSIC" -> PLAY_BOSS_MUSIC
                "FOG", "CREATEFOG" -> CREATE_FOG
                else -> entries.find { it.name == key }
            }
        }
    }
}
