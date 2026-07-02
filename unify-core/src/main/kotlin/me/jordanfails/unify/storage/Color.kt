package me.jordanfails.unify.storage

import org.bukkit.Material
import org.bukkit.inventory.ItemStack

enum class Color(val code: String, val hex: String, val format: Boolean = false) {
    BLACK("0", "#000000"),
    DARK_BLUE("1", "#0000AA"),
    DARK_GREEN("2", "#00AA00"),
    DARK_AQUA("3", "#00AAAA"),
    DARK_RED("4", "#AA0000"),
    DARK_PURPLE("5", "#AA00AA"),
    GOLD("6", "#FFAA00"),
    GRAY("7", "#AAAAAA"),
    DARK_GRAY("8", "#555555"),
    BLUE("9", "#5555FF"),
    GREEN("a", "#55FF55"),
    AQUA("b", "#55FFFF"),
    RED("c", "#FF5555"),
    LIGHT_PURPLE("d", "#FF55FF"),
    YELLOW("e", "#FFFF55"),
    WHITE("f", "#FFFFFF"),
    RAINBOW("r", "#RAINBOW"), // Special case for rainbow gradient
    BOLD("l", "", true),
    ITALIC("o", "", true),
    RESET("r", "", true),
    UNDERLINE("n", "", true),
    MAGIC("k", "", true),
    STRIKETHROUGH("m", "", true);

    companion object {
        const val COLOR_CHAR = '&'

        private val isBukkitAvailable: Boolean by lazy {
            try {
                Class.forName("org.bukkit.Bukkit")
                true
            } catch (_: ClassNotFoundException) {
                false
            }
        }

        private val glassMaterialMap: Map<Color, ItemStack> by lazy {
            if (!isBukkitAvailable) return@lazy emptyMap()

            mapOf(
                WHITE to ItemStack(Material.WHITE_STAINED_GLASS),
                GRAY to ItemStack(Material.LIGHT_GRAY_STAINED_GLASS),
                DARK_GRAY to ItemStack(Material.GRAY_STAINED_GLASS),
                BLACK to ItemStack(Material.BLACK_STAINED_GLASS),
                RED to ItemStack(Material.RED_STAINED_GLASS),
                DARK_RED to ItemStack(Material.RED_STAINED_GLASS),
                GOLD to ItemStack(Material.ORANGE_STAINED_GLASS),
                YELLOW to ItemStack(Material.YELLOW_STAINED_GLASS),
                GREEN to ItemStack(Material.LIME_STAINED_GLASS),
                DARK_GREEN to ItemStack(Material.GREEN_STAINED_GLASS),
                AQUA to ItemStack(Material.CYAN_STAINED_GLASS),
                DARK_AQUA to ItemStack(Material.CYAN_STAINED_GLASS),
                BLUE to ItemStack(Material.LIGHT_BLUE_STAINED_GLASS),
                DARK_BLUE to ItemStack(Material.BLUE_STAINED_GLASS),
                LIGHT_PURPLE to ItemStack(Material.MAGENTA_STAINED_GLASS),
                DARK_PURPLE to ItemStack(Material.PURPLE_STAINED_GLASS)
            )
        }

        private val glassPaneMaterialMap: Map<Color, ItemStack> by lazy {
            if (!isBukkitAvailable) return@lazy emptyMap()

            mapOf(
                WHITE to ItemStack(Material.WHITE_STAINED_GLASS_PANE),
                GRAY to ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE),
                DARK_GRAY to ItemStack(Material.GRAY_STAINED_GLASS_PANE),
                BLACK to ItemStack(Material.BLACK_STAINED_GLASS_PANE),
                RED to ItemStack(Material.RED_STAINED_GLASS_PANE),
                DARK_RED to ItemStack(Material.RED_STAINED_GLASS_PANE),
                GOLD to ItemStack(Material.ORANGE_STAINED_GLASS_PANE),
                YELLOW to ItemStack(Material.YELLOW_STAINED_GLASS_PANE),
                GREEN to ItemStack(Material.LIME_STAINED_GLASS_PANE),
                DARK_GREEN to ItemStack(Material.GREEN_STAINED_GLASS_PANE),
                AQUA to ItemStack(Material.CYAN_STAINED_GLASS_PANE),
                DARK_AQUA to ItemStack(Material.CYAN_STAINED_GLASS_PANE),
                BLUE to ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE),
                DARK_BLUE to ItemStack(Material.BLUE_STAINED_GLASS_PANE),
                LIGHT_PURPLE to ItemStack(Material.MAGENTA_STAINED_GLASS_PANE),
                DARK_PURPLE to ItemStack(Material.PURPLE_STAINED_GLASS_PANE)
            )
        }

        private val woolMaterialMap: Map<Color, ItemStack> by lazy {
            if (!isBukkitAvailable) return@lazy emptyMap()

            mapOf(
                WHITE to ItemStack(Material.WHITE_WOOL),
                GRAY to ItemStack(Material.LIGHT_GRAY_WOOL),
                DARK_GRAY to ItemStack(Material.GRAY_WOOL),
                BLACK to ItemStack(Material.BLACK_WOOL),
                RED to ItemStack(Material.RED_WOOL),
                DARK_RED to ItemStack(Material.RED_WOOL),
                GOLD to ItemStack(Material.ORANGE_WOOL),
                YELLOW to ItemStack(Material.YELLOW_WOOL),
                GREEN to ItemStack(Material.LIME_WOOL),
                DARK_GREEN to ItemStack(Material.GREEN_WOOL),
                AQUA to ItemStack(Material.CYAN_WOOL),
                DARK_AQUA to ItemStack(Material.CYAN_WOOL),
                BLUE to ItemStack(Material.LIGHT_BLUE_WOOL),
                DARK_BLUE to ItemStack(Material.BLUE_WOOL),
                LIGHT_PURPLE to ItemStack(Material.MAGENTA_WOOL),
                DARK_PURPLE to ItemStack(Material.PURPLE_WOOL)
            )
        }

        private val dyeMaterialMap: Map<Color, ItemStack> by lazy {
            if (!isBukkitAvailable) return@lazy emptyMap()

            mapOf(
                WHITE to ItemStack(Material.WHITE_DYE),
                GRAY to ItemStack(Material.LIGHT_GRAY_DYE),
                DARK_GRAY to ItemStack(Material.GRAY_DYE),
                BLACK to ItemStack(Material.BLACK_DYE),
                RED to ItemStack(Material.RED_DYE),
                DARK_RED to ItemStack(Material.RED_DYE),
                GOLD to ItemStack(Material.ORANGE_DYE),
                YELLOW to ItemStack(Material.YELLOW_DYE),
                GREEN to ItemStack(Material.LIME_DYE),
                DARK_GREEN to ItemStack(Material.GREEN_DYE),
                AQUA to ItemStack(Material.CYAN_DYE),
                DARK_AQUA to ItemStack(Material.CYAN_DYE),
                BLUE to ItemStack(Material.LIGHT_BLUE_DYE),
                DARK_BLUE to ItemStack(Material.BLUE_DYE),
                LIGHT_PURPLE to ItemStack(Material.MAGENTA_DYE),
                DARK_PURPLE to ItemStack(Material.PURPLE_DYE)
            )
        }

        @JvmStatic
        fun from(code: String): Color? {
            return entries.find { it.code.equals(code, ignoreCase = true) }
        }

        @JvmStatic
        fun from(code: Char): Color? {
            return from(code.toString())
        }

        @JvmStatic
        fun getGlassMaterial(color: Color): ItemStack? {
            return glassMaterialMap[color]
        }

        @JvmStatic
        fun getGlassPaneMaterial(color: Color): ItemStack? {
            return glassPaneMaterialMap[color]
        }

        @JvmStatic
        fun getWoolMaterial(color: Color): ItemStack? {
            return woolMaterialMap[color]
        }

        @JvmStatic
        fun getDyeMaterial(color: Color): ItemStack? {
            return dyeMaterialMap[color]
        }

        @JvmStatic
        fun last(s: String): Color? {
            var lastColor: Color? = null

            var i = 0
            while (i < s.length - 1) {
                if (s[i] == '§' || s[i] == COLOR_CHAR) {
                    val colorCode = s[i + 1]
                    val color = from(colorCode)
                    if (color != null && !color.format) {
                        lastColor = color
                    }
                    i += 2
                } else {
                    i++
                }
            }

            return lastColor
        }
    }

    fun color(text: String, bold: Boolean, italic: Boolean): String {
        // If this color is a formatting code (not an actual color), just apply it directly
        if (format) {
            return "$code$text"
        }

        val builder = StringBuilder(code).append(text)

        if (bold) builder.insert(0, "&l") // Bold
        if (italic) builder.insert(0, "&o") // Italic

        return builder.toString()
    }



    override fun toString(): String {
        return "§$code"
    }
}