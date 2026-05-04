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
import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.hologram.HologramManager
import me.jordanfails.unify.nametag.NametagHandler
import me.jordanfails.unify.nms.ServerVersion
import me.jordanfails.unify.npc.NPCManager
import me.jordanfails.unify.scoreboard.ScoreboardHandler
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
        sender.sendMessage(CC.translate("&e/unify reload [all|config|scoreboard|tab|nametag|hologram|npc]"))
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
    @CommandCompletion("all|config|scoreboard|tab|nametag|hologram|npc")
    @Description("Reload all or specific Unify systems")
    fun onReload(sender: CommandSender, @Name("component") @Optional component: String?) {
        val target = component?.lowercase() ?: "all"
        val plugin = UnifyCore.instance

        when (target) {
            "all" -> {
                plugin.reloadConfig()
                ScoreboardHandler.reloadAll()
                TabHandler.reloadAll()
                NametagHandler.reloadAll()
                HologramManager.load()
                NPCManager.load()

                for (player in Bukkit.getOnlinePlayers()) {
                    VisibilityHandler.update(player)
                }

                sender.sendMessage(CC.translate("&aReloaded all Unify systems."))
            }
            "config" -> {
                plugin.reloadConfig()
                sender.sendMessage(CC.translate("&aReloaded config.yml."))
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
            "hologram", "holograms", "holo" -> {
                HologramManager.load()
                sender.sendMessage(CC.translate("&aReloaded holograms from disk."))
            }
            "npc", "npcs" -> {
                NPCManager.load()
                sender.sendMessage(CC.translate("&aReloaded NPCs from disk."))
            }
            else -> {
                sender.sendMessage(CC.translate("&cUnknown reload target '&e$target&c'."))
                sender.sendMessage(CC.translate("&7Use: &f/unify reload [all|config|scoreboard|tab|nametag|hologram|npc]"))
            }
        }
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
        sender.sendMessage(CC.translate("&7Tab: &f${if (TabHandler.isEnabled()) "enabled" else "disabled"} &7(${TabHandler.updateInterval}t, providers=${TabHandler.providerCount()})"))
        sender.sendMessage(CC.translate("&7Nametags: &f${if (NametagHandler.isEnabled()) "enabled" else "disabled"} &7(${NametagHandler.updateInterval}t, providers=${NametagHandler.providerCount()})"))
        sender.sendMessage(CC.translate("&7Loaded NPCs: &f${NPCManager.getIds().size}"))
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
}
