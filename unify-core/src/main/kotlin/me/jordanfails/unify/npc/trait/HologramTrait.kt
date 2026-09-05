package me.jordanfails.unify.npc.trait

import me.jordanfails.unify.hologram.HologramLine
import me.jordanfails.unify.hologram.UnifyHologram
import me.jordanfails.unify.npc.NPCRegistry
import org.bukkit.Bukkit
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player

/**
 * Renders a stack of text lines above the NPC using a [UnifyHologram].
 *
 * The multi-line counterpart to [NameTrait]. Prefer NameTrait for a single static label — this
 * trait costs one hologram (and its backing entities) per NPC.
 *
 * Lines are ordered top to bottom, matching how they read in `/npc holo set`.
 */
class HologramTrait : Trait("hologram") {

    var lines: List<String> = emptyList()
        private set

    private var hologram: UnifyHologram? = null

    fun setLines(value: List<String>) {
        lines = value.map { it.trim() }.filter { it.isNotEmpty() }
        rebuild()
        NPCRegistry.save()
    }

    /**
     * Updates the lines without touching `npcs.yml`.
     *
     * For text regenerated on a timer — leaderboards, countdowns, live counters — the saved copy
     * is overwritten seconds later anyway, and saving here would rewrite the whole file on the
     * main thread on every refresh.
     */
    fun setTransientLines(value: List<String>) {
        lines = value.map { it.trim() }.filter { it.isNotEmpty() }
        rebuild()
    }

    override fun onSpawn() = rebuild()

    override fun onDespawn() {
        hologram?.removeAll()
        hologram = null
    }

    override fun onRemove() {
        hologram?.removeAll()
        hologram = null
    }

    override fun onViewerUpdate(player: Player, canSee: Boolean) {
        val current = hologram ?: return
        if (canSee) current.addViewer(player) else current.removeViewer(player)
    }

    private fun rebuild() {
        if (!isAttached) return

        val world = npc.location.world
        if (world == null || lines.isEmpty()) {
            hologram?.removeAll()
            hologram = null
            return
        }

        val lineObjects = lines.map { HologramLine.Text(it) }

        // Lines stack downwards from the anchor, so the anchor has to rise with the line count to
        // keep the bottom line just above the NPC's head. Anchoring the top line at a fixed height
        // instead made every line past the second overlap the NPC, very visible on tall holograms.
        val stackHeight = (lineObjects.size - 1).coerceAtLeast(0) * LINE_SPACING
        val anchor = npc.location.add(0.0, BASE_HEIGHT + stackHeight, 0.0)

        val existing = hologram
        if (existing == null) {
            hologram = UnifyHologram(anchor, lineObjects).also { created ->
                world.players.forEach { created.addViewer(it) }
            }
            return
        }

        // teleport() persists the hologram, so only pay that cost when the anchor really moved.
        if (existing.location != anchor) existing.teleport(anchor)
        existing.lines = lineObjects

        Bukkit.getOnlinePlayers().forEach { viewer ->
            if (viewer.world == world) existing.addViewer(viewer) else existing.removeViewer(viewer)
        }
    }

    override fun save(section: ConfigurationSection) {
        section.set("lines", lines)
    }

    override fun load(section: ConfigurationSection) {
        lines = section.getStringList("lines")
    }

    companion object {
        private const val BASE_HEIGHT = 2.35

        /** Must match the text-line spacing the NMS hologram renderer uses. */
        private const val LINE_SPACING = 0.25
    }
}
