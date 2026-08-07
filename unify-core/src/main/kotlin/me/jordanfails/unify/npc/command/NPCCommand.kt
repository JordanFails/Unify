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
import me.jordanfails.unify.nms.NMSHandlerFactory
import me.jordanfails.unify.npc.NPCEquipmentSlot
import me.jordanfails.unify.npc.NPCPose
import me.jordanfails.unify.npc.NPCRegistry
import me.jordanfails.unify.npc.NPCSkin
import me.jordanfails.unify.npc.UnifyNPC
import me.jordanfails.unify.npc.trait.CommandTrait
import me.jordanfails.unify.npc.trait.EquipmentTrait
import me.jordanfails.unify.npc.trait.HologramTrait
import me.jordanfails.unify.npc.trait.LookCloseTrait
import me.jordanfails.unify.npc.trait.NameTrait
import me.jordanfails.unify.npc.trait.PoseTrait
import me.jordanfails.unify.npc.trait.ProtectedTrait
import me.jordanfails.unify.npc.trait.SkinTrait
import me.jordanfails.unify.utils.CC
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player

/**
 * NPC administration.
 *
 * Most subcommands act on the player's selected NPC ([NPCSelection]) rather than taking an id, so
 * that trait commands are free to use their arguments for their own options. `create` and `select`
 * set the selection.
 */
@CommandAlias("unpc")
@CommandPermission("unify.npc")
class NPCCommand : BaseCommand() {

    @Default
    @HelpCommand
    fun onHelp(sender: CommandSender) {
        sender.sendMessage(CC.translate("&6&lNPC Commands &7(most act on your selected NPC)"))
        listOf(
            "create <id> [entityType]" to "Create and select an NPC here",
            "select <id>" to "Select an NPC to edit",
            "delete" to "Delete the selected NPC",
            "list" to "List all NPCs",
            "info" to "Show the selected NPC's configuration",
            "type <entityType>" to "Change the body's entity type",
            "skin <name|url:..|base64:..>" to "Set the skin (player NPCs)",
            "skin clear" to "Remove the skin",
            "name <text>" to "Set the nameplate text",
            "name hide|show" to "Toggle the nameplate",
            "holo <line1|line2>" to "Set hologram lines above the NPC",
            "holo clear" to "Remove the hologram",
            "lookclose [on|off] [range]" to "Follow nearby players with the head",
            "pose <standing|sneaking|sitting|sleeping|swimming>" to "Set the body pose",
            "equip <slot>" to "Equip the item in your hand",
            "equip clear" to "Remove all equipment",
            "cmd add <command>" to "Add a click command",
            "cmd list" to "List click commands",
            "cmd remove <index>" to "Remove a click command",
            "protect <on|off>" to "Toggle damage protection",
            "movehere" to "Move the NPC to you",
            "tp <x> <y> <z>" to "Move the NPC to coordinates",
            "respawn" to "Rebuild the NPC's body",
        ).forEach { (usage, description) ->
            sender.sendMessage(CC.translate("&e/npc $usage &7- $description"))
        }
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    @Subcommand("create")
    @Syntax("<id> [entityType]")
    @CommandCompletion("@nothing @entityTypes")
    @Description("Create an NPC at your location")
    fun onCreate(player: Player, id: String, @Optional entityTypeName: String?) {
        if (NPCRegistry.exists(id)) {
            player.sendMessage(CC.translate("&cAn NPC with ID '&e$id&c' already exists."))
            return
        }

        val entityType = resolveEntityType(player, entityTypeName) ?: return

        val npc = NPCRegistry.create(id, player.location, entityType)
        if (npc == null) {
            player.sendMessage(CC.translate("&cFailed to create NPC '&e$id&c'."))
            return
        }

        // A default label so a fresh NPC is identifiable without further setup. Player bodies
        // always render their own profile name, which is a synthetic id we hide — so they get a
        // hologram, while every other type can use the cheaper real nameplate.
        if (entityType == EntityType.PLAYER) {
            npc.getOrAddTrait(HologramTrait::class.java).setLines(listOf("&e$id"))
        } else {
            npc.getOrAddTrait(NameTrait::class.java).setDisplayName("&e$id")
        }
        NPCSelection.select(player, npc)

        player.sendMessage(CC.translate("&aCreated &e$id &aas &f${entityType.name} &aand selected it."))
        if (!npc.isSpawned()) {
            player.sendMessage(CC.translate("&cThe body failed to spawn — check the console. Its config is saved."))
        }
    }

    @Subcommand("select")
    @Syntax("<id>")
    @CommandCompletion("@npcs")
    @Description("Select an NPC to edit")
    fun onSelect(player: Player, id: String) {
        val npc = NPCRegistry.get(id)
        if (npc == null) {
            player.sendMessage(CC.translate("&cNo NPC found with ID '&e$id&c'."))
            return
        }
        NPCSelection.select(player, npc)
        player.sendMessage(CC.translate("&aSelected NPC '&e$id&a'."))
    }

    @Subcommand("delete|remove")
    @Description("Delete the selected NPC")
    fun onDelete(player: Player) {
        val npc = selected(player) ?: return
        NPCRegistry.delete(npc.id)
        NPCSelection.clear(player)
        player.sendMessage(CC.translate("&aDeleted NPC '&e${npc.id}&a'."))
    }

    @Subcommand("list")
    @Description("List all NPCs")
    fun onList(sender: CommandSender) {
        val all = NPCRegistry.getAll()
        if (all.isEmpty()) {
            sender.sendMessage(CC.translate("&7No NPCs created."))
            return
        }

        sender.sendMessage(CC.translate("&6&lNPCs (${all.size}):"))
        all.toSortedMap().forEach { (id, npc) ->
            val location = npc.location
            val status = if (npc.isSpawned()) "&a●" else "&c●"
            sender.sendMessage(
                CC.translate(
                    "  $status &e$id &7${npc.entityType.name.lowercase()} - " +
                        "${location.world?.name} ${location.blockX}, ${location.blockY}, ${location.blockZ}"
                )
            )
        }
    }

    @Subcommand("info")
    @Description("Show the selected NPC's configuration")
    fun onInfo(player: Player) {
        val npc = selected(player) ?: return
        player.sendMessage(CC.translate("&6&lNPC &e${npc.id}"))
        player.sendMessage(CC.translate("&7Type: &f${npc.entityType.name}  &7Spawned: &f${npc.isSpawned()}"))

        val location = npc.location
        player.sendMessage(
            CC.translate("&7At: &f${location.world?.name} ${location.blockX}, ${location.blockY}, ${location.blockZ}")
        )

        val traits = npc.traits().map { it.name }.sorted()
        player.sendMessage(CC.translate("&7Traits: &f${if (traits.isEmpty()) "none" else traits.joinToString(", ")}"))
    }

    @Subcommand("respawn")
    @Description("Rebuild the NPC's body")
    fun onRespawn(player: Player) {
        val npc = selected(player) ?: return
        val message = if (npc.spawn()) "&aRespawned '&e${npc.id}&a'." else "&cFailed to respawn '&e${npc.id}&c'."
        player.sendMessage(CC.translate(message))
    }

    @Subcommand("type")
    @Syntax("<entityType>")
    @CommandCompletion("@entityTypes")
    @Description("Change the body's entity type")
    fun onType(player: Player, entityTypeName: String) {
        val npc = selected(player) ?: return
        val entityType = resolveEntityType(player, entityTypeName) ?: return
        npc.setEntityType(entityType)
        NPCRegistry.save()
        player.sendMessage(CC.translate("&aNPC '&e${npc.id}&a' is now a &f${entityType.name}&a."))
    }

    // ── Appearance ──────────────────────────────────────────────────────────

    @Subcommand("skin")
    @Syntax("<name|url:...|base64:...|clear>")
    @Description("Set or clear the NPC's skin")
    fun onSkin(player: Player, value: String) {
        val npc = selected(player) ?: return

        if (value.equals("clear", ignoreCase = true)) {
            npc.getOrAddTrait(SkinTrait::class.java).clearSkin()
            player.sendMessage(CC.translate("&aCleared the skin on '&e${npc.id}&a'."))
            return
        }

        val parsed = NPCSkin.parseSource(value)
        if (parsed == null) {
            player.sendMessage(CC.translate("&cUse a player name, &furl:<textureUrl>&c, or &fbase64:<value>&c."))
            return
        }

        if (npc.entityType != EntityType.PLAYER) {
            player.sendMessage(CC.translate("&7Note: skins only render on PLAYER-type NPCs."))
        }

        player.sendMessage(CC.translate("&7Resolving skin..."))
        npc.getOrAddTrait(SkinTrait::class.java).setSkin(parsed.first, parsed.second) { success ->
            val message = if (success) {
                "&aSkin applied to '&e${npc.id}&a'."
            } else {
                "&cCould not resolve that skin. Check the name, or try again in a moment."
            }
            player.sendMessage(CC.translate(message))
        }
    }

    @Subcommand("name")
    @Syntax("<text|hide|show>")
    @Description("Set the NPC's nameplate")
    fun onName(player: Player, text: String) {
        val npc = selected(player) ?: return
        val trait = npc.getOrAddTrait(NameTrait::class.java)

        when {
            text.equals("hide", ignoreCase = true) -> {
                trait.setVisible(false)
                player.sendMessage(CC.translate("&aHid the nameplate on '&e${npc.id}&a'."))
            }

            text.equals("show", ignoreCase = true) -> {
                trait.setVisible(true)
                player.sendMessage(CC.translate("&aShowed the nameplate on '&e${npc.id}&a'."))
            }

            else -> {
                trait.setDisplayName(text)
                player.sendMessage(CC.translate("&aNameplate set to &r${CC.translate(text)}&a."))
            }
        }
    }

    @Subcommand("holo|hologram")
    @Syntax("<line1|line2|...|clear>")
    @Description("Set hologram lines above the NPC")
    fun onHologram(player: Player, text: String) {
        val npc = selected(player) ?: return
        val trait = npc.getOrAddTrait(HologramTrait::class.java)

        if (text.equals("clear", ignoreCase = true)) {
            trait.setLines(emptyList())
            player.sendMessage(CC.translate("&aCleared the hologram on '&e${npc.id}&a'."))
            return
        }

        val lines = text.replace("\\n", "\n").split("|", "\n").map { it.trim() }.filter { it.isNotEmpty() }
        trait.setLines(lines)
        player.sendMessage(CC.translate("&aSet ${lines.size} hologram line(s) on '&e${npc.id}&a'."))
    }

    @Subcommand("pose")
    @Syntax("<standing|sneaking|sitting|sleeping|swimming>")
    @CommandCompletion("standing|sneaking|sitting|sleeping|swimming")
    @Description("Set the NPC's body pose")
    fun onPose(player: Player, poseName: String) {
        val npc = selected(player) ?: return
        val pose = runCatching { NPCPose.valueOf(poseName.uppercase()) }.getOrNull()
        if (pose == null) {
            player.sendMessage(CC.translate("&cUnknown pose. Options: ${NPCPose.values().joinToString(", ") { it.name.lowercase() }}"))
            return
        }

        if (!npc.getOrAddTrait(PoseTrait::class.java).setPose(pose)) {
            player.sendMessage(CC.translate("&cThis server version cannot render the &f${pose.name.lowercase()} &cpose."))
            return
        }
        player.sendMessage(CC.translate("&aPose set to &f${pose.name.lowercase()}&a."))
    }

    @Subcommand("equip")
    @Syntax("<hand|off_hand|helmet|chestplate|leggings|boots|clear>")
    @CommandCompletion("hand|off_hand|helmet|chestplate|leggings|boots|clear")
    @Description("Equip the item in your hand onto the NPC")
    fun onEquip(player: Player, slotName: String) {
        val npc = selected(player) ?: return
        val trait = npc.getOrAddTrait(EquipmentTrait::class.java)

        if (slotName.equals("clear", ignoreCase = true)) {
            trait.clear()
            player.sendMessage(CC.translate("&aCleared equipment on '&e${npc.id}&a'."))
            return
        }

        val slot = runCatching { NPCEquipmentSlot.valueOf(slotName.uppercase()) }.getOrNull()
        if (slot == null) {
            player.sendMessage(CC.translate("&cUnknown slot. Options: ${NPCEquipmentSlot.values().joinToString(", ") { it.name.lowercase() }}"))
            return
        }

        // Deprecated on modern APIs but the only form that exists on 1.8, and Unify compiles
        // against 1.16 to run on both.
        @Suppress("DEPRECATION")
        val held = player.inventory.itemInHand

        trait.set(slot, held)
        val what = if (held == null || held.type == org.bukkit.Material.AIR) "Cleared" else "Set"
        player.sendMessage(CC.translate("&a$what the &f${slot.name.lowercase()} &aslot on '&e${npc.id}&a'."))
    }

    // ── Behaviour ───────────────────────────────────────────────────────────

    @Subcommand("lookclose")
    @Syntax("[on|off] [range]")
    @CommandCompletion("on|off")
    @Description("Make the NPC watch nearby players")
    fun onLookClose(player: Player, @Optional state: String?, @Optional range: Double?) {
        val npc = selected(player) ?: return
        val trait = npc.getOrAddTrait(LookCloseTrait::class.java)

        // No argument toggles, which is the common case once an NPC is set up.
        val enabled = when {
            state == null -> !trait.enabled
            state.equals("on", ignoreCase = true) || state.equals("true", ignoreCase = true) -> true
            state.equals("off", ignoreCase = true) || state.equals("false", ignoreCase = true) -> false
            else -> {
                player.sendMessage(CC.translate("&cUse &fon &cor &foff&c."))
                return
            }
        }

        trait.setEnabled(enabled)
        range?.let { trait.setRange(it) }

        player.sendMessage(
            CC.translate("&aLook-close ${if (enabled) "&aenabled" else "&cdisabled"} &7(range ${trait.range})")
        )
    }

    @Subcommand("protect")
    @Syntax("<on|off>")
    @CommandCompletion("on|off")
    @Description("Toggle damage protection")
    fun onProtect(player: Player, state: String) {
        val npc = selected(player) ?: return
        val enabled = state.equals("on", ignoreCase = true) || state.equals("true", ignoreCase = true)
        npc.getOrAddTrait(ProtectedTrait::class.java).setProtected(enabled)
        player.sendMessage(CC.translate("&aProtection ${if (enabled) "&aenabled" else "&cdisabled"} &afor '&e${npc.id}&a'."))
    }

    @Subcommand("cmd add|command add")
    @Syntax("<command>")
    @Description("Add a command run when the NPC is clicked")
    fun onCommandAdd(player: Player, command: String) {
        val npc = selected(player) ?: return
        npc.getOrAddTrait(CommandTrait::class.java).add(CommandTrait.Entry(command.removePrefix("/")))
        player.sendMessage(CC.translate("&aAdded click command &f/${command.removePrefix("/")}&a."))
    }

    @Subcommand("cmd console|command console")
    @Syntax("<command>")
    @Description("Add a command run as console when the NPC is clicked")
    fun onCommandAddConsole(player: Player, command: String) {
        val npc = selected(player) ?: return
        npc.getOrAddTrait(CommandTrait::class.java)
            .add(CommandTrait.Entry(command.removePrefix("/"), asConsole = true))
        player.sendMessage(CC.translate("&aAdded console command &f/${command.removePrefix("/")}&a."))
    }

    @Subcommand("cmd list|command list")
    @Description("List the NPC's click commands")
    fun onCommandList(player: Player) {
        val npc = selected(player) ?: return
        val trait = npc.getTrait(CommandTrait::class.java)
        val entries = trait?.entries().orEmpty()

        if (entries.isEmpty()) {
            player.sendMessage(CC.translate("&7'&e${npc.id}&7' has no click commands."))
            return
        }

        player.sendMessage(CC.translate("&6&lClick commands for &e${npc.id}&6:"))
        entries.indices.forEach { player.sendMessage(trait!!.describe(it)) }
    }

    @Subcommand("cmd remove|command remove")
    @Syntax("<index>")
    @Description("Remove a click command by index")
    fun onCommandRemove(player: Player, index: Int) {
        val npc = selected(player) ?: return
        val trait = npc.getTrait(CommandTrait::class.java)

        if (trait == null || !trait.removeAt(index)) {
            player.sendMessage(CC.translate("&cNo command at index &e$index&c. Use &f/npc cmd list&c."))
            return
        }
        player.sendMessage(CC.translate("&aRemoved click command $index."))
    }

    // ── Movement ────────────────────────────────────────────────────────────

    @Subcommand("movehere|tphere")
    @Description("Move the NPC to your location")
    fun onMoveHere(player: Player) {
        val npc = selected(player) ?: return
        npc.teleport(player.location)
        NPCRegistry.save()
        player.sendMessage(CC.translate("&aMoved '&e${npc.id}&a' to you."))
    }

    @Subcommand("tp|teleport|move")
    @Syntax("<x> <y> <z>")
    @Description("Move the NPC to coordinates in your world")
    fun onTeleport(player: Player, x: Double, y: Double, z: Double) {
        val npc = selected(player) ?: return
        val target = Location(player.world, x, y, z, player.location.yaw, player.location.pitch)
        npc.teleport(target)
        NPCRegistry.save()
        player.sendMessage(CC.translate("&aMoved '&e${npc.id}&a' to ${x.toInt()}, ${y.toInt()}, ${z.toInt()}."))
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** The player's selected NPC, telling them how to fix it when there is none. */
    private fun selected(player: Player): UnifyNPC? {
        val npc = NPCSelection.selected(player)
        if (npc == null) {
            player.sendMessage(CC.translate("&cNo NPC selected. Use &f/npc select <id>&c."))
        }
        return npc
    }

    /**
     * Resolves an entity type name, defaulting to PLAYER, and rejects types this server version
     * cannot spawn as an NPC before an NPC is created around them.
     */
    private fun resolveEntityType(player: Player, name: String?): EntityType? {
        if (name == null) return EntityType.PLAYER

        val entityType = runCatching { EntityType.valueOf(name.uppercase()) }.getOrNull()
        if (entityType == null) {
            player.sendMessage(CC.translate("&cUnknown entity type '&e$name&c'."))
            return null
        }

        val handler = NMSHandlerFactory.getHandler()
        if (handler != null && !handler.supportsNpcEntityType(entityType)) {
            player.sendMessage(CC.translate("&cNPCs cannot be spawned as &f${entityType.name} &con this version."))
            return null
        }
        return entityType
    }
}
