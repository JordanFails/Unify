package me.jordanfails.unify.preview

import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.utils.checkMainThread
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.scheduler.BukkitTask
import org.bukkit.plugin.Plugin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

object WorldPreviews : Listener {
    private val previews = linkedSetOf<WorldPreview>()
    private var task: BukkitTask? = null
    private var order = 0L
    private var enabled = false

    val supportsBlockData: Boolean by lazy {
        Bukkit.getBukkitVersion().substringBefore('-').let {
            !it.startsWith("1.") || (it.split('.').getOrNull(1)?.toIntOrNull() ?: 0) >= 13
        }
    }

    private val minHeightMethod by lazy {
        World::class.java.methods.firstOrNull { it.name == "getMinHeight" && it.parameterCount == 0 }
    }

    internal fun minimumHeight(world: World): Int = minHeightMethod?.invoke(world) as? Int ?: 0

    internal fun enable(plugin: UnifyCore) {
        enabled = true
        plugin.server.pluginManager.registerEvents(this, plugin)
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            previews.toList().filter { it.isExpired }.forEach { it.close() }
            // Chunk reloads and real-world updates can replace fake blocks on the client.
            previews.flatMap { preview -> preview.viewers.map { it to preview.world } }
                .distinct().forEach { (id, world) ->
                    val player = Bukkit.getPlayer(id) ?: return@forEach
                    val positions = previews.filter { it.world == world && it.hasViewer(player) }
                        .flatMap { it.blocks.keys }.toSet()
                    refresh(player, world, positions)
                }
        }, 20L, 20L)
    }

    internal fun disable() {
        enabled = false
        task?.cancel()
        task = null
        previews.toList().forEach { it.close() }
    }

    @JvmStatic
    @JvmOverloads
    fun create(
        owner: Plugin,
        world: World,
        priority: Int = 0,
        duration: Duration? = null,
        blockInteractions: Boolean = true,
    ): WorldPreview {
        checkMainThread()
        check(enabled && owner.isEnabled) { "The preview service and owning plugin must be enabled" }
        require(duration == null || (duration.isFinite() && duration > Duration.ZERO)) {
            "Duration must be positive and finite"
        }
        return WorldPreview(owner, world, priority, blockInteractions, order++, duration).also { previews += it }
    }

    @JvmStatic
    @JvmOverloads
    fun createTimed(
        owner: Plugin,
        world: World,
        durationMillis: Long,
        priority: Int = 0,
        blockInteractions: Boolean = true,
    ): WorldPreview = create(owner, world, priority, durationMillis.milliseconds, blockInteractions)

    internal fun remove(preview: WorldPreview) {
        previews.remove(preview)
    }

    internal fun refresh(preview: WorldPreview, positions: Collection<BlockPosition>) {
        preview.viewers.toList().forEach { id ->
            Bukkit.getPlayer(id)?.let { refresh(it, preview.world, positions) }
        }
    }

    internal fun refresh(player: Player, world: World, positions: Collection<BlockPosition>) {
        if (!player.isOnline || player.world != world) return
        val viewDistance = Bukkit.getViewDistance().coerceAtLeast(1)
        val playerChunkX = player.location.blockX shr 4
        val playerChunkZ = player.location.blockZ shr 4
        for (position in positions) {
            val chunkX = position.x shr 4
            val chunkZ = position.z shr 4
            if (kotlin.math.abs(chunkX - playerChunkX) > viewDistance ||
                kotlin.math.abs(chunkZ - playerChunkZ) > viewDistance || !world.isChunkLoaded(chunkX, chunkZ)) continue
            val location = position.location(world)
            val block = winner(player, world, position)?.blocks?.get(position)
            if (block != null) block.send(player, location)
            else if (supportsBlockData) ModernPreviewBlocks.restore(player, location)
            else player.sendBlockChange(location, location.block.type, location.block.data)
        }
    }

    private fun winner(player: Player, world: World, position: BlockPosition): WorldPreview? =
        previews.asSequence().filter {
            it.world == world && it.hasViewer(player) && it.blocks.containsKey(position)
        }.maxWithOrNull(compareBy<WorldPreview> { it.priority }.thenBy { it.order })

    private fun protected(player: Player, location: Location): Boolean {
        val world = location.world ?: return false
        return winner(player, world, BlockPosition(location.blockX, location.blockY, location.blockZ))
            ?.blockInteractions == true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) {
        if (protected(event.player, event.block.location)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) {
        if (protected(event.player, event.block.location) || protected(event.player, event.blockAgainst.location)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onInteract(event: PlayerInteractEvent) {
        val block = event.clickedBlock ?: return
        if (protected(event.player, block.location)) event.isCancelled = true
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        previews.toList().forEach { it.removeViewer(event.player) }
    }

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        previews.toList().forEach { it.removeViewer(event.player) }
    }

    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        UnifyCore.instance.server.scheduler.runTaskLater(UnifyCore.instance, Runnable {
            previews.filter { it.hasViewer(event.player) }.forEach { it.refresh(event.player) }
        }, 2L)
    }

    @EventHandler
    fun onPluginDisable(event: PluginDisableEvent) {
        previews.toList().filter { it.owner === event.plugin }.forEach { it.close() }
    }
}
