package me.jordanfails.unify.config

import org.bukkit.plugin.Plugin

class ExampleUsage(plugin: Plugin) : TypeSafeConfig(plugin, "example.yml") {

    // Annotation-driven path mapping (value(default) + @ConfigPath)
    @ConfigPath("server.name")
    @ConfigNote("The display name of your server")
    var serverName: String by value("My Awesome Server")

    @ConfigPath("server.max-players")
    @ConfigNote("Maximum number of players allowed")
    var maxPlayers: Int by value(100)

    // Explicit path mapping (value(path, default)) without @ConfigPath
    @ConfigNote("Message of the day shown in server list")
    var motd: String by value("server.motd", "Welcome to the server!")

    @ConfigNote("Enable or disable PvP combat")
    var pvpEnabled: Boolean by value("gameplay.pvp-enabled", true)

    @ConfigNote("Server difficulty level")
    var difficulty: String by value("gameplay.difficulty", "NORMAL")

    @ConfigNote("Radius in blocks around spawn where building is protected")
    var spawnProtectionRadius: Int by value("gameplay.spawn-protection-radius", 16)

    @ConfigNote("Amount of money new players start with")
    var startingBalance: Double by value("economy.starting-balance", 100.0)

    @ConfigNote("Symbol to display for currency")
    var currencySymbol: String by value("economy.currency-symbol", "$")

    @ConfigNote("List of worlds where features are enabled")
    var enabledWorlds: List<String> by value(
        "features.enabled-worlds",
        listOf("world", "world_nether", "world_the_end")
    )

    @ConfigNote("Commands that should be disabled")
    var disabledCommands: List<String> by value(
        "features.disabled-commands",
        listOf("/stop", "/reload")
    )

    fun updateMotd(newMotd: String) {
        motd = newMotd
        save()
    }
}
