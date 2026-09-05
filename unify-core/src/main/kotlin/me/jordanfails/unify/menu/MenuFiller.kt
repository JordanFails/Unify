package me.jordanfails.unify.menu

import com.cryptomorin.xseries.XMaterial
import me.jordanfails.unify.nms.LegacyColorDataType
import me.jordanfails.unify.nms.LegacyItemColor
import me.jordanfails.unify.nms.NMSHandlerFactory

/**
 * Builds decorative filler buttons for any material, resolving the legacy (1.8–1.12) damage value
 * for colored items so `GRAY_STAINED_GLASS_PANE` and friends render correctly on old servers.
 */
object MenuFiller {

    /** A nameless placeholder of [material], legacy color data included. */
    @JvmStatic
    @JvmOverloads
    fun button(material: XMaterial, name: String = " "): Button =
        Button.placeholder(material, legacyData(material), name)

    /**
     * Legacy damage value for [material] — the color prefix (`GRAY_`, `LIGHT_BLUE_`, …) mapped
     * through [LegacyItemColor], or `0` for materials that aren't a colored variant.
     */
    @JvmStatic
    fun legacyData(material: XMaterial): Byte {
        val color = legacyColor(material) ?: return 0
        val type = if (material.name.endsWith("_DYE")) LegacyColorDataType.DYE else LegacyColorDataType.BLOCK
        val fallback = if (type == LegacyColorDataType.DYE) color.dyeData else color.blockData
        return NMSHandlerFactory.getHandler()?.getLegacyColorData(color, type) ?: fallback
    }

    /**
     * The [LegacyItemColor] a material name starts with, longest prefix first so `LIGHT_GRAY_…`
     * doesn't resolve to [LegacyItemColor.GRAY] and `LIGHT_BLUE_…` doesn't resolve to BLUE.
     */
    private fun legacyColor(material: XMaterial): LegacyItemColor? =
        LegacyItemColor.entries
            .filter { material.name.startsWith("${it.name}_") }
            .maxByOrNull { it.name.length }
}
