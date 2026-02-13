package me.jordanfails.unify.hologram.command

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import me.jordanfails.unify.hologram.HologramLine
import me.jordanfails.unify.hologram.HologramManager
import me.jordanfails.unify.hologram.UnifyHologram
import me.jordanfails.unify.utils.CC
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

@CommandAlias("hologram|holo|hd")
@CommandPermission("unify.hologram")
class HologramCommand : BaseCommand() {
    
    @Default
    @HelpCommand
    fun onHelp(sender: CommandSender) {
        sender.sendMessage(CC.translate("&6&lHologram Commands:"))
        sender.sendMessage(CC.translate("&e/hologram create <id> [text] &7- Create a new hologram"))
        sender.sendMessage(CC.translate("&e/hologram delete <id> &7- Delete a hologram"))
        sender.sendMessage(CC.translate("&e/hologram addline <id> <text> &7- Add a text line"))
        sender.sendMessage(CC.translate("&e/hologram additem <id> <material> &7- Add a floating item"))
        sender.sendMessage(CC.translate("&e/hologram setline <id> <#> <text> &7- Edit a line"))
        sender.sendMessage(CC.translate("&e/hologram setitem <id> <#> <material> &7- Set line to item"))
        sender.sendMessage(CC.translate("&e/hologram setspin <id> <#> <true|false> &7- Toggle item spin"))
        sender.sendMessage(CC.translate("&e/hologram removeline <id> <#> &7- Remove a line"))
        sender.sendMessage(CC.translate("&e/hologram tp <id> [x y z] &7- Move hologram"))
        sender.sendMessage(CC.translate("&e/hologram info <id> &7- View hologram info"))
        sender.sendMessage(CC.translate("&e/hologram list &7- List all holograms"))
        sender.sendMessage(CC.translate("&e/hologram near &7- Find nearby holograms"))
    }
    
    @Subcommand("create")
    @Syntax("<id> [text]")
    @Description("Create a new hologram at your location")
    fun onCreate(player: Player, id: String, @Optional text: String?) {
        if (HologramManager.exists(id)) {
            player.sendMessage(CC.translate("&cA hologram with ID '$id' already exists."))
            return
        }
        
        val lines = if (text != null) listOf(text) else emptyList()
        val hologram = HologramManager.create(id, player.location, *lines.toTypedArray())
        hologram.addViewer(player)
        
        player.sendMessage(CC.translate("&aCreated hologram '&e$id&a' at your location."))
        if (lines.isEmpty()) {
            player.sendMessage(CC.translate("&7Use &f/hologram addline $id <text> &7to add lines."))
        }
    }
    
    @Subcommand("delete|remove")
    @Syntax("<id>")
    @CommandCompletion("@holograms")
    @Description("Delete a hologram")
    fun onDelete(sender: CommandSender, id: String) {
        if (HologramManager.delete(id)) {
            sender.sendMessage(CC.translate("&aDeleted hologram '&e$id&a'."))
        } else {
            sender.sendMessage(CC.translate("&cNo hologram found with ID '$id'."))
        }
    }
    
    @Subcommand("addline")
    @Syntax("<id> <text>")
    @CommandCompletion("@holograms")
    @Description("Add a line to a hologram")
    fun onAddLine(sender: CommandSender, id: String, text: String) {
        val hologram = HologramManager.get(id)
        
        if (hologram == null) {
            sender.sendMessage(CC.translate("&cNo hologram found with ID '$id'."))
            return
        }
        
        hologram.addLine(text)
        sender.sendMessage(CC.translate("&aAdded line to hologram '&e$id&a': $text"))
    }
    
    @Subcommand("setline")
    @Syntax("<id> <line#> <text>")
    @CommandCompletion("@holograms")
    @Description("Edit a specific line of a hologram")
    fun onSetLine(sender: CommandSender, holo: UnifyHologram, lineNumber: Int, text: String) {
        val id = HologramManager.getId(holo) ?: return
        val hologram = HologramManager.get(id)
        
        if (hologram == null) {
            sender.sendMessage(CC.translate("&cNo hologram found with ID '$id'."))
            return
        }
        
        val lineIndex = lineNumber - 1
        if (lineIndex < 0 || lineIndex >= hologram.lines.size) {
            sender.sendMessage(CC.translate("&cLine $lineNumber doesn't exist. This hologram has ${hologram.lines.size} lines."))
            return
        }
        
        hologram.setLine(lineIndex, text)
        sender.sendMessage(CC.translate("&aSet line $lineNumber of '&e$id&a' to: $text"))
    }
    
//    @Subcommand("additem")
//    @Syntax("<id> <material>")
//    @CommandCompletion("@holograms @materials")
//    @Description("Add a floating item to a hologram")
//    fun onAddItem(sender: CommandSender, id: String, materialName: String) {
//        val hologram = HologramManager.get(id)
//
//        if (hologram == null) {
//            sender.sendMessage(CC.translate("&cNo hologram found with ID '$id'."))
//            return
//        }
//
//        val material = Material.matchMaterial(materialName.uppercase())
//        if (material == null) {
//            sender.sendMessage(CC.translate("&cInvalid material '$materialName'."))
//            return
//        }
//
//        hologram.addItemLine(material)
//        sender.sendMessage(CC.translate("&aAdded floating item to hologram '&e$id&a': &f${material.name}"))
//    }
    
    @Subcommand("setitem")
    @Syntax("<id> <line#> <material>")
    @CommandCompletion("@holograms")
    @Description("Set a hologram line to a floating item")
    fun onSetItem(sender: CommandSender, holo: UnifyHologram, lineNumber: Int, materialName: String) {
        val id = HologramManager.getId(holo) ?: return
        val hologram = HologramManager.get(id)
        
        if (hologram == null) {
            sender.sendMessage(CC.translate("&cNo hologram found with ID '$id'."))
            return
        }
        
        val lineIndex = lineNumber - 1
        if (lineIndex < 0 || lineIndex >= hologram.lines.size) {
            sender.sendMessage(CC.translate("&cLine $lineNumber doesn't exist. This hologram has ${hologram.lines.size} lines."))
            return
        }
        
        val material = Material.matchMaterial(materialName.uppercase())
        if (material == null) {
            sender.sendMessage(CC.translate("&cInvalid material '$materialName'."))
            return
        }
        
        hologram.setItemLine(lineIndex, material)
        sender.sendMessage(CC.translate("&aSet line $lineNumber of '&e$id&a' to floating item: &f${material.name}"))
    }
    
    @Subcommand("setspin")
    @Syntax("<id> <line#> <true|false>")
    @CommandCompletion("@holograms")
    @Description("Toggle spinning for an item line")
    fun onSetSpin(sender: CommandSender, holo: UnifyHologram, lineNumber: Int, spin: Boolean) {
        val id = HologramManager.getId(holo) ?: return

        val hologram = HologramManager.get(id)
        
        if (hologram == null) {
            sender.sendMessage(CC.translate("&cNo hologram found with ID '$id'."))
            return
        }
        
        val lineIndex = lineNumber - 1
        if (lineIndex < 0 || lineIndex >= hologram.lines.size) {
            sender.sendMessage(CC.translate("&cLine $lineNumber doesn't exist. This hologram has ${hologram.lines.size} lines."))
            return
        }
        
        val line = hologram.lines[lineIndex]
        if (line !is HologramLine.Item) {
            sender.sendMessage(CC.translate("&cLine $lineNumber is not an item line."))
            return
        }
        
        hologram.setLine(lineIndex, HologramLine.Item(line.itemStack, spin))
        val spinText = if (spin) "&aenabled" else "&cdisabled"
        sender.sendMessage(CC.translate("&aSpin $spinText for line $lineNumber of '&e$id&a'."))
    }
    
    @Subcommand("removeline")
    @Syntax("<id> <line#>")
    @CommandCompletion("@holograms")
    @Description("Remove a specific line from a hologram")
    fun onRemoveLine(sender: CommandSender, holo: UnifyHologram, lineNumber: Int) {
        val id = HologramManager.getId(holo) ?: return
        val hologram = HologramManager.get(id)
        
        if (hologram == null) {
            sender.sendMessage(CC.translate("&cNo hologram found with ID '$id'."))
            return
        }
        
        val lineIndex = lineNumber - 1
        if (lineIndex < 0 || lineIndex >= hologram.lines.size) {
            sender.sendMessage(CC.translate("&cLine $lineNumber doesn't exist."))
            return
        }
        
        hologram.removeLine(lineIndex)
        sender.sendMessage(CC.translate("&aRemoved line $lineNumber from '&e$id&a'."))
    }
    
    @Subcommand("tp|teleport|move")
    @Syntax("<id> [x] [y] [z]")
    @CommandCompletion("@holograms")
    @Description("Move a hologram to a location")
    fun onTeleport(player: Player, holo: UnifyHologram, @Optional x: Double?, @Optional y: Double?, @Optional z: Double?) {
        val id = HologramManager.getId(holo)
        
        val location = if (x != null && y != null && z != null) {
            Location(player.world, x, y, z)
        } else {
            player.location
        }
        
        holo.teleport(location)
        player.sendMessage(CC.translate("&aTeleported hologram '&e$id&a' to ${location.x.toInt()}, ${location.y.toInt()}, ${location.z.toInt()}."))
    }
    
    @Subcommand("info")
    @Syntax("<id>")
    @CommandCompletion("@holograms")
    @Description("View information about a hologram")
    fun onInfo(sender: CommandSender, holo: UnifyHologram) {
        val hologram = holo
        val id = HologramManager.getId(holo)
        
        sender.sendMessage(CC.translate("&6&lHologram: &e$id"))
        sender.sendMessage(CC.translate("&7Location: &f${hologram.location.world?.name} ${hologram.location.x.toInt()}, ${hologram.location.y.toInt()}, ${hologram.location.z.toInt()}"))
        sender.sendMessage(CC.translate("&7Viewers: &f${hologram.viewers.size}"))
        sender.sendMessage(CC.translate("&7Lines (${hologram.lines.size}):"))
        hologram.lines.forEachIndexed { index, line ->
            sender.sendMessage(CC.translate("  &e${index + 1}. &r$line"))
        }
    }
    
    @Subcommand("list")
    @Description("List all holograms")
    fun onList(sender: CommandSender) {
        val ids = HologramManager.getIds()
        
        if (ids.isEmpty()) {
            sender.sendMessage(CC.translate("&7No holograms created."))
            return
        }
        
        sender.sendMessage(CC.translate("&6&lHolograms (${ids.size}):"))
        ids.forEach { id ->
            val hologram = HologramManager.get(id)!!
            val loc = hologram.location
            sender.sendMessage(CC.translate("  &e$id &7- ${loc.world?.name} ${loc.x.toInt()}, ${loc.y.toInt()}, ${loc.z.toInt()} &8(${hologram.lines.size} lines)"))
        }
    }
    
    @Subcommand("near")
    @Description("Find nearby holograms")
    fun onNear(player: Player) {
        val nearbyHolograms = HologramManager.getAll().filter { (_, hologram) ->
            hologram.location.world == player.world && 
            hologram.location.distance(player.location) <= 50
        }
        
        if (nearbyHolograms.isEmpty()) {
            player.sendMessage(CC.translate("&7No holograms within 50 blocks."))
            return
        }
        
        player.sendMessage(CC.translate("&6&lNearby Holograms:"))
        nearbyHolograms.forEach { (id, hologram) ->
            val distance = hologram.location.distance(player.location).toInt()
            player.sendMessage(CC.translate("  &e$id &7- ${distance}m away"))
        }
    }

    @Subcommand("movehere|tphere")
    @Description("Move specific holograms to your location")
    @CommandCompletion("@holograms")
    fun onTphere(player: Player, @Name("hologram")hologram: UnifyHologram) {
        val playerLoc = player.location

        hologram.teleport(playerLoc)
        player.sendMessage(CC.translate("&aTeleported hologram '&e${hologram.getId()}&a' to ${playerLoc.x.toInt()}, ${playerLoc.y.toInt()}, ${playerLoc.z.toInt()}."))

    }
}
