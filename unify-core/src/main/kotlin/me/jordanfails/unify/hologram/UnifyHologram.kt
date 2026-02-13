package me.jordanfails.unify.hologram

import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.utils.CC
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class UnifyHologram(
    location: Location,
    lines: List<HologramLine> = emptyList()
) {
    val uuid: UUID = UUID.randomUUID()
    var location: Location = location
        private set
        
    private val _lines = mutableListOf<HologramLine>()
    
    val viewers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    
    var lines: List<HologramLine>
        get() = _lines.toList()
        set(value) {
            _lines.clear()
            _lines.addAll(value.map { translateLine(it) })
            refresh()
        }

    init {
        this.lines = lines
    }
    
    private fun translateLine(line: HologramLine): HologramLine {
        return when (line) {
            is HologramLine.Text -> HologramLine.Text(CC.translate(line.text))
            is HologramLine.Item -> line
        }
    }

    fun teleport(location: Location) {
        this.location = location
        refresh()
        HologramManager.markDirty()
    }
    
    // --- Text Line Methods ---
    
    fun addLine(text: String) {
        _lines.add(HologramLine.Text(CC.translate(text)))
        refresh()
        HologramManager.markDirty()
    }
    
    fun addLine(line: HologramLine) {
        _lines.add(translateLine(line))
        refresh()
        HologramManager.markDirty()
    }
    
    fun removeLine(index: Int) {
        if (index in _lines.indices) {
            _lines.removeAt(index)
            refresh()
            HologramManager.markDirty()
        }
    }
    
    fun setLine(index: Int, text: String) {
        if (index in _lines.indices) {
            val wasItem = _lines[index] is HologramLine.Item
            _lines[index] = HologramLine.Text(CC.translate(text))
            if (wasItem) forceRespawn() else refresh()
            HologramManager.markDirty()
        }
    }
    
    fun setLine(index: Int, line: HologramLine) {
        if (index in _lines.indices) {
            val oldType = _lines[index]::class
            _lines[index] = translateLine(line)
            val newType = _lines[index]::class
            if (oldType != newType) forceRespawn() else refresh()
            HologramManager.markDirty()
        }
    }
    
    // --- Item Line Methods ---
    
    fun addItemLine(material: Material, spin: Boolean = true) {
        _lines.add(HologramLine.Item(material, spin))
        refresh()
        HologramManager.markDirty()
    }
    
    fun addItemLine(itemStack: ItemStack, spin: Boolean = true) {
        _lines.add(HologramLine.Item(itemStack, spin))
        refresh()
        HologramManager.markDirty()
    }
    
    fun setItemLine(index: Int, material: Material, spin: Boolean = true) {
        if (index in _lines.indices) {
            val wasText = _lines[index] is HologramLine.Text
            _lines[index] = HologramLine.Item(material, spin)
            if (wasText) forceRespawn() else refresh()
            HologramManager.markDirty()
        }
    }
    
    fun setItemLine(index: Int, itemStack: ItemStack, spin: Boolean = true) {
        if (index in _lines.indices) {
            val wasText = _lines[index] is HologramLine.Text
            _lines[index] = HologramLine.Item(itemStack, spin)
            if (wasText) forceRespawn() else refresh()
            HologramManager.markDirty()
        }
    }
    
    // --- Viewer Management ---
    
    fun addViewer(player: Player) {
        if (viewers.add(player.uniqueId)) {
            UnifyCore.instance.nms?.showHologram(player, this)
        }
    }

    fun removeViewer(player: Player) {
        if (viewers.remove(player.uniqueId)) {
            UnifyCore.instance.nms?.hideHologram(player, this)
        }
    }
    
    fun hasViewer(player: Player): Boolean {
        return viewers.contains(player.uniqueId)
    }

    fun removeAll() {
        val nms = UnifyCore.instance.nms ?: return
        for (uuid in viewers.toList()) {
            val player = Bukkit.getPlayer(uuid) ?: continue
            nms.hideHologram(player, this)
        }
        viewers.clear()
    }

    private fun refresh() {
        val nms = UnifyCore.instance.nms ?: return
        for (uuid in viewers) {
            val player = Bukkit.getPlayer(uuid) ?: continue
            nms.updateHologram(player, this)
        }
    }
    
    private fun forceRespawn() {
        val nms = UnifyCore.instance.nms ?: return
        for (uuid in viewers.toList()) {
            val player = Bukkit.getPlayer(uuid) ?: continue
            nms.hideHologram(player, this)
            nms.showHologram(player, this)
        }
    }

    fun getId(): String? {
        return HologramManager.getId(this)
    }
    
    // --- Text-only Helpers (backward compatibility) ---
    
    val textLines: List<String>
        get() = _lines.filterIsInstance<HologramLine.Text>().map { it.text }
    
    companion object {
        fun create(location: Location, vararg lines: String): UnifyHologram {
            return UnifyHologram(location, lines.map { HologramLine.Text(it) })
        }
        
        fun create(location: Location, lines: List<HologramLine>): UnifyHologram {
            return UnifyHologram(location, lines)
        }
    }
}
