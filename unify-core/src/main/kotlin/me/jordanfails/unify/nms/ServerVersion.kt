package me.jordanfails.unify.nms

enum class ServerVersion(val versionString: String) {
    v1_8_R3("1.8.8"),
    v1_9_R2("1.9.4"),
    v1_12_R1("1.12.2"),
    v1_16_R3("1.16.5"),
    v1_20_R4("1.20.4"),
    v1_21_R1("1.21.1"),
    v26_R1("26.2");

    fun isAbove(other: ServerVersion): Boolean = this.ordinal > other.ordinal

    fun isBelow(other: ServerVersion): Boolean = this.ordinal < other.ordinal

    fun isAtOrAbove(other: ServerVersion): Boolean = this.ordinal >= other.ordinal

    fun isAtOrBelow(other: ServerVersion): Boolean = this.ordinal <= other.ordinal

    companion object {
        fun fromString(version: String): ServerVersion? = entries.find { it.versionString == version }
    }
}