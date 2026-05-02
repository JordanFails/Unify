package me.jordanfails.unify.config

import me.jordanfails.unify.menu.Button
import me.jordanfails.unify.nms.LegacyColorDataType
import me.jordanfails.unify.nms.LegacyItemColor
import me.jordanfails.unify.utils.CC
import me.jordanfails.unify.utils.LocationUtils
import me.jordanfails.unify.utils.StringUtil
import me.jordanfails.unify.utils.TimeDuration
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.InventoryView

abstract class AbstractConfig(
    private val path: String,
    private val defaultValue: Any
) {

    abstract fun cfg(): Config

    fun getString(): String {
        if (cfg().contains(path)) return cfg().getString(path)
        loadDefault()
        return CC.translate(defaultValue.toString())
    }

    fun getInt(): Int {
        if (cfg().contains(path)) return cfg().getInt(path)
        loadDefault()
        return defaultValue.toString().toInt()
    }

    fun getBoolean(): Boolean {
        if (cfg().contains(path)) return cfg().getBoolean(path)
        loadDefault()
        return defaultValue.toString().toBoolean()
    }

    fun getDouble(): Double {
        if (cfg().contains(path)) return cfg().getDouble(path)
        loadDefault()
        return defaultValue.toString().toDouble()
    }

    fun getStringList(): List<String> {
        if (cfg().contains(path)) return cfg().getStringList(path)
        loadDefault()
        return defaultValue as? List<String> ?: emptyList()
    }

    fun getMaterial(): Material? = Material.getMaterial(getString())

    fun getLocation(): Location? = LocationUtils.deserializeString(getString())

    fun getTimeDuration(): TimeDuration = TimeDuration(getString())

    fun update(value: Any) {
        cfg()[path] = value
        cfg().save()
    }

    fun loadDefault() {
        if (cfg().contains(path)) return
        cfg()[path] = defaultValue
        cfg().save()
    }

    fun sendMessage(player: Player) {
        player.sendMessage(CC.translate(getString()))
    }

    fun sendMessage(player: Player, vararg replacements: Any) {
        player.sendMessage(
            StringUtil.colorFormat(applyReplacements(getString(), replacements))
        )
    }

    fun getButton(player: Player): Button {
        val basePath = path

        return object : Button() {

            override fun getName(player: Player): String {
                return CC.translate(cfg().getString("$basePath.name") ?: defaultValue.toString())
            }

            override fun getDescription(player: Player): MutableList<String> {
                return cfg().getStringList("$basePath.lore")
                    ?.map { CC.translate(it) }
                    ?.toMutableList()
                    ?: mutableListOf()
            }

            override fun getMaterial(player: Player): Material {
                val materialName = cfg().getString("$basePath.material") ?: "STONE"
                return Material.getMaterial(materialName.uppercase()) ?: Material.STONE
            }

            override fun getDamageValue(player: Player): Byte {
                // 1. Explicit data always wins
                if (cfg().contains("$basePath.data")) {
                    return cfg().getInt("$basePath.data").toByte()
                }

                // 2. Legacy color support
                val colorName = cfg().getString("$basePath.color") ?: return 0
                val typeName = cfg().getString("$basePath.color-type") ?: "BLOCK"

                return try {
                    val color = LegacyItemColor.valueOf(colorName.uppercase())
                    val type = LegacyColorDataType.valueOf(typeName.uppercase())

                    when (type) {
                        LegacyColorDataType.BLOCK -> color.blockData
                        LegacyColorDataType.DYE -> color.dyeData
                    }
                } catch (_: Exception) {
                    0
                }
            }

            override fun getAmount(player: Player): Int {
                return cfg().getInt("$basePath.amount").takeIf { it > 0 } ?: 1
            }

            override fun clicked(player: Player, slot: Int, clickType: ClickType, view: InventoryView) {
                super.clicked(player, slot, clickType, view)
            }
        }
    }

    private fun applyReplacements(input: String, replacements: Array<out Any>): String {
        var text = input
        for (i in replacements.indices step 2) {
            if (i + 1 < replacements.size) {
                text = text.replace(
                    replacements[i].toString(),
                    replacements[i + 1].toString()
                )
            }
        }
        return text
    }
}