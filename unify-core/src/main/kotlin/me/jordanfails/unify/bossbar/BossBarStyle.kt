package me.jordanfails.unify.bossbar

/**
 * Cross-version boss bar styles (notched segments).
 *
 * Ignored on 1.8, where the wither health bar is always solid.
 */
enum class BossBarStyle {
    SOLID,
    SEGMENTED_6,
    SEGMENTED_10,
    SEGMENTED_12,
    SEGMENTED_20;

    companion object {
        @JvmStatic
        fun fromString(name: String): BossBarStyle? {
            val key = name.trim().uppercase().replace('-', '_').replace(' ', '_')
            return when (key) {
                "NOTCHED_6", "SEGMENTED6", "6" -> SEGMENTED_6
                "NOTCHED_10", "SEGMENTED10", "10" -> SEGMENTED_10
                "NOTCHED_12", "SEGMENTED12", "12" -> SEGMENTED_12
                "NOTCHED_20", "SEGMENTED20", "20" -> SEGMENTED_20
                "FLAT", "SOLID" -> SOLID
                else -> entries.find { it.name == key }
            }
        }
    }
}
