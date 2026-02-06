package me.jordanfails.unify.config

import org.bukkit.plugin.Plugin

class ExampleUsage(plugin: Plugin) : TypeSafeConfig(plugin, "example.yml") {

    @ConfigPath("server.name")
    @ConfigNote("The display name of your server")
    var serverName: String by value("My Awesome Server")

    @ConfigPath("server.max-players")
    @ConfigNote("Maximum number of players allowed")
    var maxPlayers: Int by value(100)

    @ConfigPath("server.motd")
    @ConfigNote("Message of the day shown in server list")
    var motd: String by value("Welcome to the server!")

    @ConfigPath("gameplay.pvp-enabled")
    @ConfigNote("Enable or disable PvP combat")
    var pvpEnabled: Boolean by value(true)

    @ConfigPath("gameplay.difficulty")
    @ConfigNote("Server difficulty level")
    var difficulty: String by value("NORMAL")

    @ConfigPath("gameplay.spawn-protection-radius")
    @ConfigNote("Radius in blocks around spawn where building is protected")
    var spawnProtectionRadius: Int by value(16)

    @ConfigPath("economy.starting-balance")
    @ConfigNote("Amount of money new players start with")
    var startingBalance: Double by value(100.0)

    @ConfigPath("economy.currency-symbol")
    @ConfigNote("Symbol to display for currency")
    var currencySymbol: String by value("$")

    @ConfigPath("features.enabled-worlds")
    @ConfigNote("List of worlds where features are enabled")
    var enabledWorlds: List<String> by value(listOf("world", "world_nether", "world_the_end"))

    @ConfigPath("features.disabled-commands")
    @ConfigNote("Commands that should be disabled")
    var disabledCommands: List<String> by value(listOf("/stop", "/reload"))
}
