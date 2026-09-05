package me.jordanfails.unify.bossbar

/**
 * Cross-version boss bar colors.
 *
 * These map to Bukkit's [org.bukkit.boss.BarColor] on 1.9+; 1.8 wither bars ignore color.
 * `AQUA` / `CYAN` are accepted aliases for [BLUE] (vanilla has no aqua bar color).
 */
enum class BossBarColor {
    PINK,
    BLUE,
    RED,
    GREEN,
    YELLOW,
    PURPLE,
    WHITE;

    companion object {
        @JvmStatic
        fun fromString(name: String): BossBarColor? {
            val key = name.trim().uppercase().replace('-', '_').replace(' ', '_')
            return when (key) {
                "AQUA", "CYAN", "LIGHT_BLUE" -> BLUE
                "GOLD" -> YELLOW
                "MAGENTA" -> PINK
                else -> entries.find { it.name == key }
            }
        }
    }
}
