package me.jordanfails.unify.hologram

import me.jordanfails.unify.UnifyCore
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object HologramManager : Listener {
    
    private val holograms = ConcurrentHashMap<String, UnifyHologram>()
    private lateinit var plugin: UnifyCore
    private lateinit var dataFile: File
    
    fun enable(plugin: UnifyCore) {
        this.plugin = plugin
        this.dataFile = File(plugin.dataFolder, "holograms.yml")
        Bukkit.getPluginManager().registerEvents(this, plugin)
        load()
    }
    
    fun disable() {
        save()
        holograms.values.forEach { it.removeAll() }
        holograms.clear()
    }
    
    fun create(id: String, location: Location, vararg lines: String): UnifyHologram {
        val hologram = UnifyHologram(location, lines.map { HologramLine.Text(it) })
        holograms[id.lowercase()] = hologram
        save()
        return hologram
    }
    
    fun createWithLines(id: String, location: Location, lines: List<HologramLine>): UnifyHologram {
        val hologram = UnifyHologram(location, lines)
        holograms[id.lowercase()] = hologram
        save()
        return hologram
    }
    
    fun get(id: String): UnifyHologram? {
        return holograms[id.lowercase()]
    }
    
    fun delete(id: String): Boolean {
        val hologram = holograms.remove(id.lowercase()) ?: return false
        hologram.removeAll()
        save()
        return true
    }
    
    fun exists(id: String): Boolean {
        return holograms.containsKey(id.lowercase())
    }
    
    fun getAll(): Map<String, UnifyHologram> {
        return holograms.toMap()
    }
    
    fun getIds(): Set<String> {
        return holograms.keys.toSet()
    }
    
    fun showToPlayer(player: Player, id: String): Boolean {
        val hologram = get(id) ?: return false
        hologram.addViewer(player)
        return true
    }
    
    fun hideFromPlayer(player: Player, id: String): Boolean {
        val hologram = get(id) ?: return false
        hologram.removeViewer(player)
        return true
    }
    
    fun showAllToPlayer(player: Player) {
        holograms.values.forEach { hologram ->
            if (hologram.location.world == player.world) {
                hologram.addViewer(player)
            }
        }
    }
    
    fun hideAllFromPlayer(player: Player) {
        holograms.values.forEach { hologram ->
            hologram.removeViewer(player)
        }
    }
    
    fun save() {
        try {
            if (!dataFile.parentFile.exists()) {
                dataFile.parentFile.mkdirs()
            }
            
            val config = YamlConfiguration()
            
            holograms.forEach { (id, hologram) ->
                val path = "holograms.$id"
                
                // Save location
                config.set("$path.location.world", hologram.location.world?.name)
                config.set("$path.location.x", hologram.location.x)
                config.set("$path.location.y", hologram.location.y)
                config.set("$path.location.z", hologram.location.z)
                
                // Save lines
                val linesList = hologram.lines.map { line ->
                    when (line) {
                        is HologramLine.Text -> mapOf(
                            "type" to "text",
                            "content" to line.text
                        )
                        is HologramLine.Item -> mapOf(
                            "type" to "item",
                            "material" to line.itemStack.type.name,
                            "spin" to line.spin
                        )
                    }
                }
                config.set("$path.lines", linesList)
            }
            
            config.save(dataFile)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to save holograms: ${e.message}")
            e.printStackTrace()
        }
    }
    
    fun load() {
        try {
            if (!dataFile.exists()) {
                return
            }
            
            val config = YamlConfiguration.loadConfiguration(dataFile)
            val hologramsSection = config.getConfigurationSection("holograms") ?: return
            
            for (id in hologramsSection.getKeys(false)) {
                val path = "holograms.$id"
                
                // Load location
                val worldName = config.getString("$path.location.world") ?: continue
                val world = Bukkit.getWorld(worldName) ?: continue
                val x = config.getDouble("$path.location.x")
                val y = config.getDouble("$path.location.y")
                val z = config.getDouble("$path.location.z")
                val location = Location(world, x, y, z)
                
                // Load lines
                val linesList = config.getMapList("$path.lines")
                val lines = mutableListOf<HologramLine>()
                
                for (lineMap in linesList) {
                    val type = lineMap["type"] as? String ?: continue
                    when (type) {
                        "text" -> {
                            val content = lineMap["content"] as? String ?: ""
                            lines.add(HologramLine.Text(content))
                        }
                        "item" -> {
                            val materialName = lineMap["material"] as? String ?: continue
                            val material = Material.matchMaterial(materialName) ?: Material.STONE
                            val spin = lineMap["spin"] as? Boolean ?: true
                            lines.add(HologramLine.Item(ItemStack(material), spin))
                        }
                    }
                }
                
                if (lines.isNotEmpty()) {
                    val hologram = UnifyHologram(location, lines)
                    holograms[id.lowercase()] = hologram
                }
            }
            
            plugin.logger.info("Loaded ${holograms.size} holograms from config")
        } catch (e: Exception) {
            plugin.logger.warning("Failed to load holograms: ${e.message}")
            e.printStackTrace()
        }
    }
    
    fun markDirty() {
        save()
    }
    
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        Bukkit.getScheduler().runTaskLater(UnifyCore.instance, Runnable {
            showAllToPlayer(event.player)
        }, 5L)
    }
    
    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        hideAllFromPlayer(event.player)
    }
    
    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        holograms.values.forEach { hologram ->
            if (hologram.location.world == event.from) {
                hologram.removeViewer(event.player)
            } else if (hologram.location.world == event.player.world) {
                hologram.addViewer(event.player)
            }
        }
    }
}
