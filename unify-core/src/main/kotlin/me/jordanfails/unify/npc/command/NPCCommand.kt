package me.jordanfails.unify.npc.command

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.HelpCommand
import co.aikar.commands.annotation.Optional
import co.aikar.commands.annotation.Subcommand
import co.aikar.commands.annotation.Syntax
import me.jordanfails.unify.npc.NPCManager
import me.jordanfails.unify.npc.UnifyNPC
import me.jordanfails.unify.utils.CC
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

@CommandAlias("npc")
@CommandPermission("unify.npc")
class NPCCommand : BaseCommand() {

    @Default
    @HelpCommand
    fun onHelp(sender: CommandSender) {
        sender.sendMessage(CC.translate("&6&lNPC Commands:"))
        sender.sendMessage(CC.translate("&e/npc create <id> [action command] &7- Create an NPC at your location"))
        sender.sendMessage(CC.translate("&e/npc delete <id> &7- Delete an NPC"))
        sender.sendMessage(CC.translate("&e/npc setaction <id> <command> &7- Set click command (runs as clicker)"))
        sender.sendMessage(CC.translate("&e/npc clearaction <id> &7- Remove click command"))
        sender.sendMessage(CC.translate("&e/npc setskin <id> <name|url:...|base64:...> &7- Set NPC skin"))
        sender.sendMessage(CC.translate("&e/npc clearskin <id> &7- Remove NPC skin"))
        sender.sendMessage(CC.translate("&e/npc setholo <id> <text> &7- Set hologram text above NPC"))
        sender.sendMessage(CC.translate("&e/npc clearholo <id> &7- Remove hologram"))
        sender.sendMessage(CC.translate("&e/npc movehere <id> &7- Move NPC to your location"))
        sender.sendMessage(CC.translate("&e/npc tp <id> [x y z] &7- Move NPC to coordinates"))
        sender.sendMessage(CC.translate("&e/npc list &7- List all NPCs"))
    }

    @Subcommand("create")
    @Syntax("<id> [action command]")
    @Description("Create an NPC at your location")
    fun onCreate(player: Player, id: String, @Optional actionCommand: String?) {
        if (NPCManager.exists(id)) {
            player.sendMessage(CC.translate("&cAn NPC with ID '&e$id&c' already exists."))
            return
        }

        val defaultLine = "&e$id"
        val created = NPCManager.create(id, player.location, listOf(defaultLine), actionCommand)
        if (created == null) {
            player.sendMessage(CC.translate("&cFailed to create NPC '&e$id&c'."))
            return
        }

        player.sendMessage(CC.translate("&aCreated NPC '&e$id&a' at your location."))
        if (!actionCommand.isNullOrBlank()) {
            player.sendMessage(CC.translate("&7Click action: &f/${actionCommand.trim().removePrefix("/")}"))
        }
    }

    @Subcommand("delete|remove")
    @Syntax("<id>")
    @CommandCompletion("@npcs")
    @Description("Delete an NPC")
    fun onDelete(sender: CommandSender, id: String) {
        if (!NPCManager.delete(id)) {
            sender.sendMessage(CC.translate("&cNo NPC found with ID '&e$id&c'."))
            return
        }

        sender.sendMessage(CC.translate("&aDeleted NPC '&e$id&a'."))
    }

    @Subcommand("setaction")
    @Syntax("<id> <command>")
    @CommandCompletion("@npcs")
    @Description("Set click action command")
    fun onSetAction(sender: CommandSender, id: String, command: String) {
        if (!NPCManager.setActionCommand(id, command)) {
            sender.sendMessage(CC.translate("&cNo NPC found with ID '&e$id&c'."))
            return
        }

        sender.sendMessage(CC.translate("&aNPC '&e$id&a' action updated to &f/${command.trim().removePrefix("/")}"))
    }

    @Subcommand("clearaction")
    @Syntax("<id>")
    @CommandCompletion("@npcs")
    @Description("Clear click action command")
    fun onClearAction(sender: CommandSender, id: String) {
        if (!NPCManager.setActionCommand(id, null)) {
            sender.sendMessage(CC.translate("&cNo NPC found with ID '&e$id&c'."))
            return
        }

        sender.sendMessage(CC.translate("&aCleared click action for NPC '&e$id&a'."))
    }

    @Subcommand("setskin")
    @Syntax("<id> <name|url:...|base64:...>")
    @CommandCompletion("@npcs")
    @Description("Set NPC skin")
    fun onSetSkin(sender: CommandSender, id: String, skin: String) {
        val parsed = parseSkinInput(skin)
        if (parsed == null) {
            sender.sendMessage(CC.translate("&cInvalid skin input. Use player name, url:<textureUrl>, or base64:<value>."))
            return
        }

        val (type, value) = parsed
        if (!NPCManager.setSkin(id, type, value)) {
            sender.sendMessage(CC.translate("&cNo NPC found with ID '&e$id&c'."))
            return
        }

        sender.sendMessage(CC.translate("&aUpdated skin for NPC '&e$id&a'."))
    }

    @Subcommand("clearskin")
    @Syntax("<id>")
    @CommandCompletion("@npcs")
    @Description("Remove NPC skin")
    fun onClearSkin(sender: CommandSender, id: String) {
        if (!NPCManager.clearSkin(id)) {
            sender.sendMessage(CC.translate("&cNo NPC found with ID '&e$id&c'."))
            return
        }

        sender.sendMessage(CC.translate("&aCleared skin for NPC '&e$id&a'."))
    }

    @Subcommand("setholo|sethologram")
    @Syntax("<id> <text>")
    @CommandCompletion("@npcs")
    @Description("Set hologram text above NPC")
    fun onSetHologram(sender: CommandSender, id: String, text: String) {
        val lines = parseHologramLines(text)
        if (!NPCManager.setHologramLines(id, lines)) {
            sender.sendMessage(CC.translate("&cNo NPC found with ID '&e$id&c'."))
            return
        }

        sender.sendMessage(CC.translate("&aUpdated hologram for NPC '&e$id&a'."))
    }

    @Subcommand("clearholo|clearhologram")
    @Syntax("<id>")
    @CommandCompletion("@npcs")
    @Description("Clear hologram above NPC")
    fun onClearHologram(sender: CommandSender, id: String) {
        if (!NPCManager.setHologramLines(id, emptyList())) {
            sender.sendMessage(CC.translate("&cNo NPC found with ID '&e$id&c'."))
            return
        }

        sender.sendMessage(CC.translate("&aCleared hologram for NPC '&e$id&a'."))
    }

    @Subcommand("movehere|tphere")
    @Syntax("<id>")
    @CommandCompletion("@npcs")
    @Description("Move an NPC to your location")
    fun onMoveHere(player: Player, id: String) {
        if (!NPCManager.teleport(id, player.location)) {
            player.sendMessage(CC.translate("&cNo NPC found with ID '&e$id&c'."))
            return
        }

        player.sendMessage(CC.translate("&aMoved NPC '&e$id&a' to your location."))
    }

    @Subcommand("tp|teleport|move")
    @Syntax("<id> [x] [y] [z]")
    @CommandCompletion("@npcs")
    @Description("Move NPC to your location or coordinates")
    fun onTeleport(player: Player, id: String, @Optional x: Double?, @Optional y: Double?, @Optional z: Double?) {
        val target = if (x != null && y != null && z != null) {
            Location(player.world, x, y, z, player.location.yaw, player.location.pitch)
        } else {
            player.location
        }

        if (!NPCManager.teleport(id, target)) {
            player.sendMessage(CC.translate("&cNo NPC found with ID '&e$id&c'."))
            return
        }

        player.sendMessage(CC.translate("&aMoved NPC '&e$id&a' to ${target.x.toInt()}, ${target.y.toInt()}, ${target.z.toInt()}."))
    }

    @Subcommand("list")
    @Description("List all NPC IDs")
    fun onList(sender: CommandSender) {
        val all = NPCManager.getAll()
        if (all.isEmpty()) {
            sender.sendMessage(CC.translate("&7No NPCs created."))
            return
        }

        sender.sendMessage(CC.translate("&6&lNPCs (${all.size}):"))
        all.toSortedMap().forEach { (id, npc) ->
            val location = npc.spawnLocation
            sender.sendMessage(
                CC.translate(
                    "  &e$id &7- ${location.world?.name} ${location.x.toInt()}, ${location.y.toInt()}, ${location.z.toInt()}"
                )
            )
        }
    }

    private fun parseHologramLines(input: String): List<String> {
        return input
            .replace("\\n", "\n")
            .split("|", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun parseSkinInput(input: String): Pair<UnifyNPC.SkinType, String>? {
        val raw = input.trim()
        if (raw.isEmpty()) {
            return null
        }

        return when {
            raw.startsWith("base64:", ignoreCase = true) -> {
                val value = raw.substringAfter(":", "").trim()
                value.takeIf { it.isNotEmpty() }?.let { UnifyNPC.SkinType.BASE64 to it }
            }
            raw.startsWith("url:", ignoreCase = true) -> {
                val value = raw.substringAfter(":", "").trim()
                value.takeIf { it.isNotEmpty() }?.let { UnifyNPC.SkinType.URL to it }
            }
            raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true) -> {
                UnifyNPC.SkinType.URL to raw
            }
            else -> UnifyNPC.SkinType.NAME to raw
        }
    }
}
