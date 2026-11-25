package me.jordanfails.unify.hologram

import me.jordanfails.unify.module.UnifyModule
import me.jordanfails.unify.UnifyCore
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.WorldLoadEvent
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

object HologramManager : Listener {
    
    private val holograms = ConcurrentHashMap<String, UnifyHologram>()
    private lateinit var plugin: UnifyCore
    private lateinit var dataFile: File

    // Set while holograms.yml can't be read; writing then would replace the operator's data with what loaded.
    private var readOnly = false

    // Raw records for holograms whose world isn't loaded yet, keyed by id.
    private val unloaded = LinkedHashMap<String, Map<String, Any?>>()
    
    internal fun enable(plugin: UnifyCore) {
        this.plugin = plugin
        this.dataFile = File(plugin.dataFolder, "holograms.yml")
        Bukkit.getPluginManager().registerEvents(this, plugin)
        load()
    }
    
    internal fun disable() {
        save()
        holograms.values.forEach { it.removeAll() }
        holograms.clear()
    }
    
    fun create(id: String, location: Location, vararg lines: String): UnifyHologram {
        UnifyModule.HOLOGRAMS.require()
        requireValidId(id)
        val hologram = UnifyHologram(location, lines.map { HologramLine.Text(it) })
        holograms[id.lowercase()] = hologram
        save()
        return hologram
    }
    
    fun createWithLines(id: String, location: Location, lines: List<HologramLine>): UnifyHologram {
        UnifyModule.HOLOGRAMS.require()
        requireValidId(id)
        val hologram = UnifyHologram(location, lines)
        holograms[id.lowercase()] = hologram
        save()
        return hologram
    }
    
    // A dot would split the id into nested YAML keys, and reusing a waiting hologram's id would clobber it on save.
    private fun requireValidId(id: String) {
        require('.' !in id) { "Hologram ids can't contain '.': $id" }
        require(!unloaded.containsKey(id.lowercase())) { "Hologram '$id' already exists in a world that isn't loaded" }
    }

    fun get(id: String): UnifyHologram? {
        return holograms[id.lowercase()]
    }

    fun getId(hologram: UnifyHologram): String? {
        // holograms: Map<String, UnifyHologram>
        return holograms.entries.find { it.value == hologram }?.key
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
    
    /** Writes through a temp file so a failed save never truncates holograms.yml. */
    fun save() {
        if (readOnly) return
        try {
            dataFile.parentFile.mkdirs()
            val config = YamlConfiguration()

            holograms.forEach { (id, hologram) ->
                val path = "holograms.$id"
                config.set("$path.location.world", hologram.location.world?.name)
                config.set("$path.location.x", hologram.location.x)
                config.set("$path.location.y", hologram.location.y)
                config.set("$path.location.z", hologram.location.z)
                val linesList = hologram.lines.filterIsInstance<HologramLine.Text>().map { line ->
                    mapOf("type" to "text", "content" to line.text)
                }
                config.set("$path.lines", linesList)
            }
            // Holograms whose world isn't loaded are written back exactly as they were read.
            unloaded.forEach { (id, data) -> config.createSection("holograms.$id", data) }

            val temp = File(dataFile.parentFile, "${dataFile.name}.tmp")
            config.save(temp)
            Files.move(temp.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to save holograms: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Reloads every hologram from disk. If the file can't be read, the holograms in memory are kept
     * and nothing is written until it's fixed, so a YAML typo can never wipe it.
     */
    fun load() {
        val config = YamlConfiguration()
        if (dataFile.exists()) {
            try {
                config.load(dataFile)
            } catch (e: Exception) {
                readOnly = true
                plugin.logger.severe("holograms.yml couldn't be read, so holograms were not reloaded and won't be saved until it's fixed: ${e.message}")
                return
            }
        }
        readOnly = false

        // Take the old ones off players' screens; their replacements are shown below.
        holograms.values.forEach { it.removeAll() }
        holograms.clear()
        unloaded.clear()

        val section = config.getConfigurationSection("holograms")
        for (id in section?.getKeys(false).orEmpty()) {
            val hologram = read(config, id)
            if (hologram == null) section?.getConfigurationSection(id)?.let { unloaded[id] = it.toMap() }
            else holograms[id.lowercase()] = hologram
        }
        Bukkit.getOnlinePlayers().forEach(::showAllToPlayer)
        plugin.logger.info("Loaded ${holograms.size} holograms" + if (unloaded.isEmpty()) "" else " (${unloaded.size} waiting for their world)")
    }

    /** Null when the hologram's world isn't loaded yet. */
    private fun read(config: YamlConfiguration, id: String): UnifyHologram? {
        val path = "holograms.$id"
        val world = config.getString("$path.location.world")?.let(Bukkit::getWorld) ?: return null
        val location = Location(
            world,
            config.getDouble("$path.location.x"),
            config.getDouble("$path.location.y"),
            config.getDouble("$path.location.z"),
        )
        val lines = config.getMapList("$path.lines").mapNotNull { line ->
            if (line["type"] != "text") return@mapNotNull null // item lines are no longer supported
            HologramLine.Text((line["content"] as? String) ?: (line["contains"] as? String) ?: "")
        }
        return UnifyHologram(location, lines)
    }

    // Worlds loaded after Unify (Multiverse and friends) bring their holograms with them.
    @EventHandler
    fun onWorldLoad(event: WorldLoadEvent) {
        for ((id, data) in unloaded.filterValues { (it["location"] as? Map<*, *>)?.get("world") == event.world.name }) {
            val hologram = read(YamlConfiguration().apply { createSection("holograms.$id", data) }, id) ?: continue
            holograms[id.lowercase()] = hologram
            unloaded.remove(id)
            event.world.players.forEach { hologram.addViewer(it) }
        }
    }

    // Nested sections become nested maps so createSection(path, map) can write them back verbatim.
    private fun ConfigurationSection.toMap(): Map<String, Any?> =
        getValues(false).mapValues { (_, value) -> if (value is ConfigurationSection) value.toMap() else value }

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
