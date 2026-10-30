package me.jordanfails.unify.preview

import me.jordanfails.unify.utils.checkMainThread
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/** A private visual layer. Higher priority wins; the newest layer wins ties. */
class WorldPreview internal constructor(
    val owner: Plugin,
    val world: World,
    val priority: Int,
    val blockInteractions: Boolean,
    internal val order: Long,
    private val duration: Duration?,
) : AutoCloseable {
    val id: UUID = UUID.randomUUID()
    private val startedAt = System.nanoTime()
    private val minimumHeight = WorldPreviews.minimumHeight(world)
    internal val blocks = linkedMapOf<BlockPosition, PreviewBlock>()
    internal val viewers = linkedSetOf<UUID>()

    var isClosed: Boolean = false
        private set

    internal val isExpired: Boolean
        get() = duration?.let { (System.nanoTime() - startedAt).nanoseconds >= it } ?: false

    private fun checkOpen() {
        checkMainThread()
        check(!isClosed) { "This preview is closed" }
    }

    @JvmOverloads
    fun setBlock(location: Location, material: Material, legacyData: Byte = 0): WorldPreview =
        setBlock(location, PreviewBlock(material, legacyData))

    fun setBlock(location: Location, block: PreviewBlock): WorldPreview {
        checkOpen()
        val position = position(location)
        blocks[position] = block
        WorldPreviews.refresh(this, setOf(position))
        return this
    }

    /** Validates the complete batch before changing the layer. */
    fun setBlocks(blocks: Map<Location, PreviewBlock>): WorldPreview {
        checkOpen()
        val changes = blocks.entries.associate { position(it.key) to it.value }
        this.blocks.putAll(changes)
        WorldPreviews.refresh(this, changes.keys)
        return this
    }

    fun removeBlock(location: Location): Boolean {
        checkOpen()
        val position = position(location)
        if (blocks.remove(position) == null) return false
        WorldPreviews.refresh(this, setOf(position))
        return true
    }

    fun clear() {
        checkOpen()
        val positions = blocks.keys.toSet()
        blocks.clear()
        WorldPreviews.refresh(this, positions)
    }

    fun addViewer(player: Player): WorldPreview {
        checkOpen()
        require(player.isOnline && player.world == world) { "Viewer must be online in the preview world" }
        viewers.add(player.uniqueId)
        refresh(player)
        return this
    }

    fun removeViewer(player: Player): Boolean {
        checkMainThread()
        if (!viewers.remove(player.uniqueId)) return false
        WorldPreviews.refresh(player, world, blocks.keys)
        return true
    }

    fun hasViewer(player: Player): Boolean = viewers.contains(player.uniqueId)

    fun getViewers(): Set<UUID> = viewers.toSet()

    fun getBlockCount(): Int = blocks.size

    fun refresh(player: Player) {
        checkOpen()
        if (hasViewer(player)) WorldPreviews.refresh(player, world, blocks.keys)
    }

    override fun close() {
        checkMainThread()
        if (isClosed) return
        isClosed = true
        WorldPreviews.remove(this)
        viewers.toList().forEach { uuid ->
            org.bukkit.Bukkit.getPlayer(uuid)?.let { removeViewer(it) }
        }
        viewers.clear()
        blocks.clear()
    }

    private fun position(location: Location): BlockPosition {
        require(location.world == world) { "Every preview block must be in the preview world" }
        require(location.x.isFinite() && location.y.isFinite() && location.z.isFinite()) { "Coordinates must be finite" }
        require(location.blockY >= minimumHeight) { "Block is below the world height limit" }
        require(location.blockY < world.maxHeight) { "Block is above the world height limit" }
        return BlockPosition(location.blockX, location.blockY, location.blockZ)
    }
}

internal data class BlockPosition(val x: Int, val y: Int, val z: Int) {
    fun location(world: World): Location = Location(world, x.toDouble(), y.toDouble(), z.toDouble())
}
