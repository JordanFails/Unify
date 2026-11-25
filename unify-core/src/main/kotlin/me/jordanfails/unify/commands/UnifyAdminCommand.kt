package me.jordanfails.unify.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.HelpCommand
import co.aikar.commands.annotation.Name
import co.aikar.commands.annotation.Optional
import co.aikar.commands.annotation.Subcommand
import co.aikar.commands.annotation.Syntax
import co.aikar.commands.bukkit.contexts.OnlinePlayer
import me.jordanfails.unify.config.UnifyConfig
import me.jordanfails.unify.module.UnifyModule
import me.jordanfails.unify.plugin.UnifyPlugins
import me.jordanfails.unify.bossbar.BossBarHandler
import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.hologram.HologramManager
import me.jordanfails.unify.nametag.NametagHandler
import me.jordanfails.unify.nms.ServerVersion
import me.jordanfails.unify.npc.NPCRegistry
import me.jordanfails.unify.scoreboard.ScoreboardHandler
import me.jordanfails.unify.scoreboard.menu.ScoreboardsMenu
import me.jordanfails.unify.tab.TabHandler
import me.jordanfails.unify.utils.CC
import me.jordanfails.unify.visibility.VanishHandler
import me.jordanfails.unify.visibility.VisibilityHandler
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

@CommandAlias("unify")
@CommandPermission("unify.admin")
class UnifyAdminCommand : BaseCommand() {

    @Default
    @HelpCommand
    fun onHelp(sender: CommandSender) {
        sender.sendMessage(CC.translate("&6&lUnify Admin Commands:"))
        sender.sendMessage(CC.translate("&e/unify reload [all|config|scoreboard|tab|nametag|bossbar|hologram|npc]"))
        sender.sendMessage(CC.translate("&e/unify modules"))
        sender.sendMessage(CC.translate("&e/unify plugins [reload <plugin>]"))
        sender.sendMessage(CC.translate("&e/unify debug [player]"))
        sender.sendMessage(CC.translate("&e/unify version"))
    }

    @Subcommand("version")
    @Description("Show plugin and NMS version info")
    fun onVersion(sender: CommandSender) {
        val plugin = UnifyCore.instance
        val nmsVersion = plugin.nms?.getServerVersion()?.versionString ?: "unavailable"

        sender.sendMessage(CC.translate("&6&lUnify Version"))
        sender.sendMessage(CC.translate("&7Plugin: &f${plugin.description.name} v${plugin.description.version}"))
        sender.sendMessage(CC.translate("&7Bukkit: &f${Bukkit.getBukkitVersion()}"))
        sender.sendMessage(CC.translate("&7NMS Handler: &f$nmsVersion"))
    }

    @Subcommand("reload")
    @Syntax("[component]")
    @CommandCompletion("all|config|scoreboard|tab|nametag|bossbar|hologram|npc")
    @Description("Reload all or specific Unify systems")
    fun onReload(sender: CommandSender, @Name("component") @Optional component: String?) {
        val target = component?.lowercase() ?: "all"
        val plugin = UnifyCore.instance

        val module = when (target) {
            "scoreboard", "scoreboards" -> UnifyModule.SCOREBOARD
            "tab" -> UnifyModule.TAB
            "nametag", "nametags" -> UnifyModule.NAMETAGS
            "bossbar", "bossbars" -> UnifyModule.BOSSBAR
            "hologram", "holograms", "holo" -> UnifyModule.HOLOGRAMS
            "npc", "npcs" -> UnifyModule.NPCS
            else -> null
        }
        if (module != null && !plugin.modules.isEnabled(module)) {
            sender.sendMessage(CC.translate("&cThe ${module.key} module is disabled. Enable it in config.yml and restart the server."))
            return
        }

        when (target) {
            "all" -> {
                plugin.reloadConfig()
                UnifyConfig.reload()
                plugin.modules.ifEnabled(UnifyModule.SCOREBOARD) { ScoreboardHandler.reloadAll() }
                plugin.modules.ifEnabled(UnifyModule.TAB) { TabHandler.reloadAll() }
                plugin.modules.ifEnabled(UnifyModule.NAMETAGS) { NametagHandler.reloadAll() }
                plugin.modules.ifEnabled(UnifyModule.BOSSBAR) { BossBarHandler.reloadAll() }
                plugin.modules.ifEnabled(UnifyModule.HOLOGRAMS) { HologramManager.load() }
                plugin.modules.ifEnabled(UnifyModule.NPCS) { NPCRegistry.load() }

                plugin.modules.ifEnabled(UnifyModule.VISIBILITY) {
                    for (player in Bukkit.getOnlinePlayers()) {
                        VisibilityHandler.update(player)
                    }
                }

                sender.sendMessage(CC.translate("&aReloaded all enabled Unify systems. Module changes require a server restart."))
            }
            "config" -> {
                plugin.reloadConfig()
                UnifyConfig.reload()
                sender.sendMessage(CC.translate("&aReloaded config.yml. Module changes require a server restart."))
            }
            "scoreboard", "scoreboards" -> {
                ScoreboardHandler.reloadAll()
                sender.sendMessage(CC.translate("&aReloaded scoreboard system."))
            }
            "tab" -> {
                TabHandler.reloadAll()
                sender.sendMessage(CC.translate("&aReloaded tab system."))
            }
            "nametag", "nametags" -> {
                NametagHandler.reloadAll()
                sender.sendMessage(CC.translate("&aReloaded nametag system."))
            }
            "bossbar", "bossbars" -> {
                BossBarHandler.reloadAll()
                sender.sendMessage(CC.translate("&aReloaded boss bar system."))
            }
            "hologram", "holograms", "holo" -> {
                HologramManager.load()
                sender.sendMessage(CC.translate("&aReloaded holograms from disk."))
            }
            "npc", "npcs" -> {
                NPCRegistry.load()
                sender.sendMessage(CC.translate("&aReloaded NPCs from disk."))
            }
            else -> {
                sender.sendMessage(CC.translate("&cUnknown reload target '&e$target&c'."))
                sender.sendMessage(CC.translate("&7Use: &f/unify reload [all|config|scoreboard|tab|nametag|bossbar|hologram|npc]"))
            }
        }
    }

    @Subcommand("modules")
    @Description("List active modules and changes awaiting a restart")
    fun onModules(sender: CommandSender) {
        val plugin = UnifyCore.instance
        sender.sendMessage(CC.translate("&6&lUnify Modules:"))
        for (module in UnifyModule.entries) {
            val active = plugin.modules.isEnabled(module)
            val configured = plugin.config.getBoolean("modules.${module.key}", true)
            val pending = if (active != configured) " &e(restart pending)" else ""
            sender.sendMessage(CC.translate("&7${module.key}: ${if (active) "&aenabled" else "&cdisabled"}$pending"))
        }
    }

    @Subcommand("plugins")
    @Description("List plugins hooked through @UnifyPlugin")
    fun onPlugins(sender: CommandSender) {
        val hooked = UnifyPlugins.all
        sender.sendMessage(CC.translate("&6&lUnify Plugins &7(${hooked.size})"))
        for (entry in hooked) {
            val plugin = entry.plugin
            sender.sendMessage(CC.translate(
                "&e${plugin.name} &7v${plugin.description.version}: &f${entry.commands} &7commands, " +
                    "&f${entry.listeners} &7listeners, &f${entry.tasks} &7tasks, &f${entry.configCount} &7configs"
            ))
        }
    }

    @Subcommand("plugins reload")
    @Syntax("<plugin>")
    @CommandCompletion("@unifyPlugins")
    @Description("Reload a hooked plugin's @ConfigFile objects")
    fun onPluginsReload(sender: CommandSender, @Name("plugin") name: String) {
        val entry = UnifyPlugins.get(name)
        if (entry == null) {
            sender.sendMessage(CC.translate("&c'$name' isn't a plugin hooked through @UnifyPlugin."))
            return
        }
        UnifyPlugins.reloadConfigs(entry.plugin)
        sender.sendMessage(CC.translate("&aReloaded ${entry.configCount} config file(s) for ${entry.plugin.name}."))
    }

    @Subcommand("debug")
    @Syntax("[player]")
    @CommandCompletion("@players")
    @Description("Print runtime debug information for a player")
    fun onDebug(sender: CommandSender, @Name("player") @Optional targetArg: OnlinePlayer?) {
        val target = targetArg?.player ?: (sender as? Player)
        if (target == null) {
            sender.sendMessage(CC.translate("&cConsole must provide a player: &f/unify debug <player>"))
            return
        }

        val plugin = UnifyCore.instance
        val nms = plugin.nms
        val ping = nms?.getPing(target) ?: -1
        val vanished = VanishHandler.isVanished(target)

        sender.sendMessage(CC.translate("&6&lUnify Debug: &e${target.name}"))
        sender.sendMessage(CC.translate("&7World: &f${target.world.name} &7at &f${target.location.blockX}, ${target.location.blockY}, ${target.location.blockZ}"))
        sender.sendMessage(CC.translate("&7Ping: &f${ping}ms"))
        sender.sendMessage(CC.translate("&7Vanished: &f${if (vanished) "&aYes" else "&cNo"}"))
        sender.sendMessage(CC.translate("&7NMS Handler: &f${nms?.getServerVersion()?.versionString ?: "unavailable"}"))
        sender.sendMessage(CC.translate("&7Scoreboard: &f${if (ScoreboardHandler.isEnabled()) "enabled" else "disabled"} &7(${ScoreboardHandler.updateInterval}t, providers=${ScoreboardHandler.providerCount()})"))
        sender.sendMessage(CC.translate("&7Tab: &f${if (TabHandler.isEnabled()) "enabled" else "disabled"} &7(${TabHandler.updateInterval}t, providers=${TabHandler.providerCount()}, sort=${TabHandler.sortProviderCount()})"))
        if (TabHandler.sortProviderCount() > 0) {
            val sortWeight = TabHandler.sortWeight(target)
            sender.sendMessage(CC.translate("&7Tab Sort: &f$sortWeight &7(${TabHandler.sortProviders().joinToString { it.name }})"))
        }
        sender.sendMessage(CC.translate("&7Nametags: &f${if (NametagHandler.isEnabled()) "enabled" else "disabled"} &7(${NametagHandler.updateInterval}t, providers=${NametagHandler.providerCount()})"))
        sender.sendMessage(CC.translate("&7Loaded NPCs: &f${NPCRegistry.getIds().size}"))
        sender.sendMessage(CC.translate("&7Loaded Holograms: &f${HologramManager.getIds().size}"))

        val viewer = sender as? Player
        if (viewer != null) {
            val canSee = viewer.canSee(target)
            sender.sendMessage(CC.translate("&7Viewer Relation: &f${viewer.name} ${if (canSee) "can" else "cannot"} see ${target.name}"))
            VisibilityHandler.getDebugInfo(target, viewer).forEach { line ->
                sender.sendMessage(CC.translate("&8- $line"))
            }
        }
    }

    @Subcommand("scoreboards")
    @CommandPermission("unify.command.scoreboards")
    fun scoreboard(player: Player) {
        val modules = UnifyCore.instance.modules
        if (!modules.isEnabled(UnifyModule.SCOREBOARD) || !modules.isEnabled(UnifyModule.MENUS)) {
            player.sendMessage(CC.translate("&cThe scoreboard and menus modules must be enabled to use this command."))
            return
        }
        ScoreboardsMenu().open(player)
    }
}
