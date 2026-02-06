package me.jordanfails.unify.config

import org.bukkit.plugin.Plugin

class UnifyConfig(plugin: Plugin) : TypeSafeConfig(plugin) {

    @ConfigPath("settings.debug-mode")
    @ConfigNote("Enable debug mode for additional logging")
    var debugMode: Boolean by value(false)

    @ConfigPath("settings.language")
    @ConfigNote("Default language for messages")
    var language: String by value("en_US")

    @ConfigPath("menu.default-size")
    @ConfigNote("Default size for menus (must be multiple of 9, max 54)")
    var defaultMenuSize: Int by value(27)

    @ConfigPath("menu.update-interval")
    @ConfigNote("How often menus should update in ticks (20 ticks = 1 second)")
    var menuUpdateInterval: Int by value(20)

    @ConfigPath("hologram.update-interval")
    @ConfigNote("How often holograms should update in ticks")
    var hologramUpdateInterval: Int by value(20)

    @ConfigPath("hologram.view-distance")
    @ConfigNote("Maximum distance players can see holograms from (in blocks)")
    var hologramViewDistance: Double by value(48.0)

    @ConfigPath("nametag.update-interval")
    @ConfigNote("How often nametags should update in ticks")
    var nametagUpdateInterval: Int by value(40)

    @ConfigPath("features.enabled-modules")
    @ConfigNote("List of enabled modules")
    var enabledModules: List<String> by value(listOf("menus", "holograms", "nametags", "visibility"))
}
