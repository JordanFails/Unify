package me.jordanfails.unify.config

import org.bukkit.plugin.Plugin

class UnifyConfig(plugin: Plugin) : TypeSafeConfig(plugin) {

    // This class uses explicit paths, so no @ConfigPath annotation is required.
    @ConfigNote("Enable debug mode for additional logging")
    var debugMode: Boolean by value("settings.debug-mode", false)

    @ConfigNote("Default language for messages")
    var language: String by value("settings.language", "en_US")

    @ConfigNote("Enable auto-updating scoreboards")
    var scoreboardsEnabled: Boolean by value("scoreboards.enabled", true)

    @ConfigNote("How often scoreboards update in ticks")
    var scoreboardsUpdateIntervalTicks: Int by value("scoreboards.update-interval-ticks", 20)

    @ConfigNote("Scoreboard title shown to players")
    var scoreboardTitle: String by value("scoreboard.title", "&d&lHUB")

    @ConfigNote("Scoreboard lines shown top-to-bottom")
    var scoreboardLines: List<String> by value(
        "scoreboard.lines",
        listOf(
            "&fRank: &d<rank>",
            "&fFriends Online: &d0",
            "&fLevel: &d0",
            "  &fProgress: &d<bar>",
            "",
            "&fLobby: &dGarden",
            "&fPlayers: &d0/1000",
            "",
            "&eplay.etheriamc.net"
        )
    )

    @ConfigNote("Enable custom tab header/footer updates")
    var tabEnabled: Boolean by value("tab.enabled", true)

    @ConfigNote("How often the tab list updates in ticks")
    var tabUpdateIntervalTicks: Int by value("tab.update-interval-ticks", 40)

    @ConfigNote("Tab list header lines")
    var tabHeader: List<String> by value(
        "tab.header",
        listOf(
            "",
            "<gradient:light_purple:gold><bold>EtheriaMC</bold></gradient>",
            "&7Welcome, &f{player}",
            ""
        )
    )

    @ConfigNote("Tab list footer lines")
    var tabFooter: List<String> by value(
        "tab.footer",
        listOf(
            "",
            "&7Online: &d{online}&7/&d{max_players}",
            "&eplay.etheriamc.net",
            ""
        )
    )

    @ConfigNote("Enable auto-updating nametags")
    var nametagsEnabled: Boolean by value("nametags.enabled", true)

    @ConfigNote("Restrict nametag packets by player name prefix")
    var nametagPacketRestrictionEnabled: Boolean by value("nametags.packet-restriction", false)

    @ConfigNote("Prefix that bypasses nametag packet restriction")
    var nametagPacketRestrictionBypassPrefix: String by value(
        "nametags.packet-restriction-bypass-prefix",
        ""
    )
}
