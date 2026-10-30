package me.jordanfails.unify.hologram

import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.utils.checkMainThread
import org.bukkit.plugin.Plugin
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class UnifyHologram(
    location: Location,
    lines: List<HologramLine> = emptyList()
) : AutoCloseable {
    val uuid: UUID = UUID.randomUUID()
    var location: Location = location.clone()
        private set
        get() = field.clone()
        
    var isClosed: Boolean = false
        private set

    private fun checkOpen() {
        checkMainThread()
        check(!isClosed) { "This hologram is closed" }
    }

    private val _lines = mutableListOf<HologramLine>()
    private val viewerLines = mutableMapOf<UUID, List<HologramLine>>()
    private val renderedLines = mutableMapOf<UUID, List<HologramLine>>()
    private var contentProvider: ((Player) -> List<HologramLine>)? = null

    /** Zero disables automatic refresh; otherwise measured in server ticks. */
    var updateIntervalTicks: Long = 0L
        set(value) {
            checkMainThread()
            require(value >= 0L) { "Update interval cannot be negative" }
            field = value
        }
    
    val viewers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    
    var lines: List<HologramLine>
        get() = snapshot(_lines)
        set(value) {
            checkOpen()
            val translated = snapshot(value)
            // Callers that regenerate their lines on a timer hand us the same text most ticks.
            // Re-sending it costs a metadata packet per line per viewer for no visible change.
            if (_lines == translated) return
            _lines.clear()
            _lines.addAll(translated)
            refresh()
        }

    init {
        this.lines = lines
        HologramViews.track(this)
    }
    
    fun teleport(location: Location) {
        checkOpen()
        if (this.location == location) return
        this.location = location.clone()
        refresh(force = true)
        // Only holograms the manager owns live in holograms.yml. Saving for an unregistered one
        // (an NPC's hologram, say) rewrites the whole file on the main thread for nothing, and
        // those are exactly the ones that move every tick.
        if (getId() != null) HologramManager.markDirty()
    }
    
    // --- Text Line Methods ---
    
    fun addLine(text: String) {
        checkOpen()
        _lines.add(HologramLine.Text(text))
        refresh()
        HologramManager.markDirty()
    }
    
    fun addLine(line: HologramLine) {
        checkOpen()
        _lines.addAll(snapshot(listOf(line)))
        refresh()
        HologramManager.markDirty()
    }
    
    fun removeLine(index: Int) {
        checkOpen()
        if (index in _lines.indices) {
            _lines.removeAt(index)
            refresh()
            HologramManager.markDirty()
        }
    }
    
    fun setLine(index: Int, text: String) {
        checkOpen()
        if (index in _lines.indices) {
            _lines[index] = HologramLine.Text(text)
            refresh()
            HologramManager.markDirty()
        }
    }
    
    fun setLine(index: Int, line: HologramLine) {
        checkOpen()
        if (index in _lines.indices) {
            _lines[index] = snapshot(listOf(line)).single()
            refresh()
            HologramManager.markDirty()
        }
    }
    
    // --- Viewer Management ---
    
    fun addViewer(player: Player) {
        checkOpen()
        viewers.add(player.uniqueId)
        refresh(player)
    }

    fun removeViewer(player: Player) {
        checkMainThread()
        if (renderedLines.remove(player.uniqueId) != null) {
            UnifyCore.instance.nms?.hideHologram(player, this)
        }
        viewers.remove(player.uniqueId)
        viewerLines.remove(player.uniqueId)
    }

    fun hasViewer(player: Player): Boolean = viewers.contains(player.uniqueId)

    /** Explicit viewer lines take precedence over the provider and shared lines. */
    fun setLines(player: Player, lines: List<HologramLine>) {
        checkOpen()
        viewerLines[player.uniqueId] = snapshot(lines)
        refresh(player)
    }

    fun setLines(player: Player, vararg lines: String) {
        setLines(player, lines.map { HologramLine.Text(it) })
    }

    fun clearLines(player: Player) {
        checkOpen()
        viewerLines.remove(player.uniqueId)
        refresh(player)
    }

    fun setContentProvider(provider: ((Player) -> List<HologramLine>)?) {
        checkOpen()
        contentProvider = provider
        refresh()
    }

    /** The snapshot currently rendered for this viewer, used by every NMS implementation. */
    fun getLines(player: Player): List<HologramLine> {
        checkMainThread()
        return snapshot(renderedLines[player.uniqueId] ?: viewerLines[player.uniqueId]
            ?: contentProvider?.invoke(player) ?: _lines)
    }

    @JvmOverloads
    fun refresh(player: Player, force: Boolean = false) {
        checkOpen()
        if (!viewers.contains(player.uniqueId)) return
        val nms = UnifyCore.instance.nms ?: return
        val previous = renderedLines[player.uniqueId]
        if (!player.isOnline || player.world != location.world) {
            if (previous != null) nms.hideHologram(player, this)
            renderedLines.remove(player.uniqueId)
            return
        }
        val resolved = snapshot(viewerLines[player.uniqueId] ?: contentProvider?.invoke(player) ?: _lines)
        if (!force && previous == resolved) return
        val changedShape = previous != null &&
            previous.map { it::class } != resolved.map { it::class }
        if (changedShape) nms.hideHologram(player, this)
        renderedLines[player.uniqueId] = resolved
        if (previous == null || changedShape) nms.showHologram(player, this)
        else nms.updateHologram(player, this)
    }

    fun removeAll() {
        checkMainThread()
        for (uuid in viewers.toList()) {
            Bukkit.getPlayer(uuid)?.let { removeViewer(it) }
        }
        viewers.clear()
        renderedLines.clear()
        viewerLines.clear()
    }

    /** Removes all views, the provider, and ownership/automatic refresh registration. */
    override fun close() {
        checkMainThread()
        if (isClosed) return
        removeAll()
        isClosed = true
        contentProvider = null
        updateIntervalTicks = 0L
        HologramViews.forget(this)
    }

    @JvmOverloads
    fun refresh(force: Boolean = false) {
        checkOpen()
        for (uuid in viewers.toList()) {
            Bukkit.getPlayer(uuid)?.let { refresh(it, force) }
        }
    }

    internal fun resetView(player: Player) {
        if (renderedLines.remove(player.uniqueId) != null) {
            UnifyCore.instance.nms?.hideHologram(player, this)
        }
        refresh(player)
    }

    private fun snapshot(lines: List<HologramLine>): List<HologramLine> = lines.map {
        when (it) {
            is HologramLine.Text -> it.copy()
            is HologramLine.Item -> it.copy(itemStack = it.itemStack.clone())
        }
    }

    fun getId(): String? {
        return HologramManager.getId(this)
    }
    
    // --- Text-only Helpers (backward compatibility) ---
    
    val textLines: List<String>
        get() = _lines.filterIsInstance<HologramLine.Text>().map { it.text }
    
    companion object {
        /** Runtime-only content; membership is controlled through addViewer/removeViewer. */
        @JvmStatic
        @JvmOverloads
        fun personalized(
            owner: Plugin,
            location: Location,
            updateIntervalTicks: Long = 20L,
            provider: (Player) -> List<HologramLine>,
        ): UnifyHologram {
            checkMainThread()
            require(owner.isEnabled) { "The owning plugin must be enabled" }
            require(updateIntervalTicks >= 0L) { "Update interval cannot be negative" }
            return UnifyHologram(location).also {
                it.setContentProvider(provider)
                it.updateIntervalTicks = updateIntervalTicks
                HologramViews.own(owner, it)
            }
        }

        fun create(location: Location, vararg lines: String): UnifyHologram {
            return UnifyHologram(location, lines.map { HologramLine.Text(it) })
        }
        
        fun create(location: Location, lines: List<HologramLine>): UnifyHologram {
            return UnifyHologram(location, lines)
        }
    }
}
