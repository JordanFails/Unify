package me.jordanfails.unify.nms

enum class LegacyItemColor(
    val blockData: Byte,
    val dyeData: Byte
) {
    WHITE(0, 15),
    ORANGE(1, 14),
    MAGENTA(2, 13),
    LIGHT_BLUE(3, 12),
    YELLOW(4, 11),
    LIME(5, 10),
    PINK(6, 9),
    GRAY(7, 8),
    LIGHT_GRAY(8, 7),
    CYAN(9, 6),
    PURPLE(10, 5),
    BLUE(11, 4),
    BROWN(12, 3),
    GREEN(13, 2),
    RED(14, 1),
    BLACK(15, 0)
}

enum class LegacyColorDataType {
    BLOCK,
    DYE
}
