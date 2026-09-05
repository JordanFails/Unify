package me.jordanfails.unify.bossbar

import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.config.UnifyConfig
import me.jordanfails.unify.npc.NPCRegistry
import me.jordanfails.unify.utils.CC
import me.jordanfails.unify.utils.Time
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Player
import java.lang.ref.WeakReference
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Cross-version boss bar.
 *
 * 1.9+ uses Bukkit's BossBar API; 1.8 falls back to a hidden wither. Bars can nest (children
 * render under the parent with extra title indent), carry a countdown, and be shown either
 * directly via [addPlayer] or through [BossBarManager] / [BossBarHandler].
 */
class UnifyBossBar @JvmOverloads constructor(
    title: String,
    progress: Double = 1.0,
    color: BossBarColor = BossBarColor.PURPLE,
    style: BossBarStyle = BossBarStyle.SOLID,
    flags: Set<BossBarFlag> = emptySet(),
    indent: Int = 0,
) {

    val uuid: UUID = UUID.randomUUID()

    /**
     * Title template. Supports `{player}`, `{world}`, `{time}`, `{online}`, `{max_players}`.
     * Color codes (`&`, MiniMessage, hex) are translated when rendering.
     */
    var titleTemplate: String = title
        set(value) {
            field = value
            if (!updating) update()
        }

    /**
     * Last rendered title, which NMS reads. Prefer [setTitle] / [titleTemplate] to change it.
     */
    var title: String = CC.translate(title)
        internal set

    var progress: Double = progress.coerceIn(0.0, 1.0)
        set(value) {
            field = value.coerceIn(0.0, 1.0)
            if (!updating) {
                autoProgress = false
                update()
            }
        }

    var color: BossBarColor = color
        set(value) {
            field = value
            if (!updating) update()
        }

    var style: BossBarStyle = style
        set(value) {
            field = value
            if (!updating) update()
        }

    private val _flags: MutableSet<BossBarFlag> = flags.toMutableSet()
    val flags: Set<BossBarFlag>
        get() = _flags.toSet()

    var indent: Int = indent.coerceAtLeast(0)
        set(value) {
            field = value.coerceAtLeast(0)
            if (!updating) update()
        }

    var scope: BossBarScope = BossBarScope.VIEWERS

    /** Used when [scope] is [BossBarScope.WORLD], and as the `{world}` placeholder fallback. */
    var world: World? = null

    var visible: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            if (value) reshowViewers() else hideFromViewers()
        }

    var durationMillis: Long? = null
        private set

    var expiresAt: Long? = null
        private set

    /** When a duration is set, progress is remaining/duration unless [setProgress] was used. */
    var autoProgress: Boolean = false
        private set

    var removeOnExpire: Boolean = true

    var parent: UnifyBossBar? = null
        private set

    private val _children = mutableListOf<UnifyBossBar>()
    val children: List<UnifyBossBar>
        get() = _children.toList()

    private val viewers = ConcurrentHashMap.newKeySet<UUID>()

    @Volatile
    private var updating = false

    init {
        live[uuid] = WeakReference(this)
    }

    fun setTitle(value: String): UnifyBossBar {
        titleTemplate = value
        return this
    }

    fun setProgress(value: Double): UnifyBossBar {
        this.progress = value
        return this
    }

    fun setColor(value: BossBarColor): UnifyBossBar {
        this.color = value
        return this
    }

    fun setStyle(value: BossBarStyle): UnifyBossBar {
        this.style = value
        return this
    }

    fun setIndent(value: Int): UnifyBossBar {
        this.indent = value
        return this
    }

    fun setScope(value: BossBarScope): UnifyBossBar {
        this.scope = value
        return this
    }

    fun setWorld(value: World?): UnifyBossBar {
        this.world = value
        return this
    }

    fun setVisible(value: Boolean): UnifyBossBar {
        this.visible = value
        return this
    }

    fun addFlag(flag: BossBarFlag): UnifyBossBar {
        if (_flags.add(flag) && !updating) update()
        return this
    }

    fun removeFlag(flag: BossBarFlag): UnifyBossBar {
        if (_flags.remove(flag) && !updating) update()
        return this
    }

    fun setFlags(flags: Set<BossBarFlag>): UnifyBossBar {
        _flags.clear()
        _flags.addAll(flags)
        if (!updating) update()
        return this
    }

    /**
     * Starts a countdown. `{time}` in the title becomes remaining compact time (e.g. `4m 43s`),
     * and progress drains unless [setProgress] is called afterwards.
     */
    fun setDuration(millis: Long): UnifyBossBar {
        val duration = millis.coerceAtLeast(0L)
        durationMillis = duration
        expiresAt = Time.now() + duration
        autoProgress = true
        if (!updating) update()
        return this
    }

    fun clearDuration(): UnifyBossBar {
        durationMillis = null
        expiresAt = null
        autoProgress = false
        if (!updating) update()
        return this
    }

    fun remainingMillis(): Long = expiresAt?.let { Time.remaining(it) } ?: 0L

    fun isExpired(): Boolean {
        val end = expiresAt ?: return false
        return Time.hasPassed(end)
    }

    fun addChild(child: UnifyBossBar): UnifyBossBar {
        if (child === this) return this
        if (isDescendantOf(child)) return this
        child.parent?.removeChild(child)
        child.parent = this
        child.indent = indent + 1
        _children.add(child)
        for (uuid in viewers) {
            Bukkit.getPlayer(uuid)?.let { child.addPlayer(it) }
        }
        return this
    }

    fun addChild(
        title: String,
        progress: Double = 1.0,
        color: BossBarColor = this.color,
        style: BossBarStyle = this.style,
    ): UnifyBossBar {
        val child = UnifyBossBar(title, progress, color, style, emptySet(), indent + 1)
        addChild(child)
        return child
    }

    fun removeChild(child: UnifyBossBar): Boolean {
        if (!_children.remove(child)) return false
        if (child.parent === this) child.parent = null
        child.removeAll()
        return true
    }

    fun clearChildren() {
        for (child in _children.toList()) {
            removeChild(child)
        }
    }

    fun flatten(): List<UnifyBossBar> {
        val out = ArrayList<UnifyBossBar>(1 + _children.size)
        out += this
        for (child in _children) {
            out += child.flatten()
        }
        return out
    }

    fun addPlayer(player: Player): UnifyBossBar {
        if (NPCRegistry.isNpc(player)) return this
        renderFor(player)
        if (viewers.add(player.uniqueId) && visible) {
            UnifyCore.instance.nms?.showBossBar(player, this)
        } else if (visible && viewers.contains(player.uniqueId)) {
            UnifyCore.instance.nms?.updateBossBar(player, this)
        }
        for (child in _children) {
            child.addPlayer(player)
        }
        return this
    }

    fun addPlayers(players: Iterable<Player>): UnifyBossBar {
        players.forEach { addPlayer(it) }
        return this
    }

    fun removePlayer(player: Player): UnifyBossBar {
        if (viewers.remove(player.uniqueId)) {
            UnifyCore.instance.nms?.hideBossBar(player, this)
        }
        for (child in _children) {
            child.removePlayer(player)
        }
        return this
    }

    fun hasPlayer(player: Player): Boolean = viewers.contains(player.uniqueId)

    fun getPlayers(): Set<UUID> = viewers.toSet()

    fun showToAll(): UnifyBossBar {
        scope = BossBarScope.GLOBAL
        NPCRegistry.realOnlinePlayers().forEach { addPlayer(it) }
        return this
    }

    fun showToWorld(world: World): UnifyBossBar {
        this.world = world
        scope = BossBarScope.WORLD
        NPCRegistry.realOnlinePlayers()
            .filter { it.world == world }
            .forEach { addPlayer(it) }
        return this
    }

    fun shouldShowTo(player: Player): Boolean {
        if (!visible) return false
        if (NPCRegistry.isNpc(player)) return false
        return when (scope) {
            BossBarScope.GLOBAL -> true
            BossBarScope.WORLD -> player.world == world
            BossBarScope.VIEWERS -> viewers.contains(player.uniqueId)
        }
    }

    /**
     * Re-render title/progress for every viewer. Safe to call off the construction path.
     */
    fun update() {
        if (updating) return
        val nms = UnifyCore.instance.nms ?: return
        updating = true
        try {
            if (autoProgress) {
                progress = computedProgress()
            }
            if (!visible) return
            for (uuid in viewers) {
                val player = Bukkit.getPlayer(uuid) ?: continue
                renderFor(player)
                nms.updateBossBar(player, this)
            }
        } finally {
            updating = false
        }
        for (child in _children) {
            child.update()
        }
    }

    /**
     * Applies countdown progress/title. Returns true if the bar expired this tick.
     */
    fun tick(): Boolean {
        var expired = false
        if (expiresAt != null) {
            if (isExpired()) {
                if (autoProgress) {
                    updating = true
                    try {
                        progress = 0.0
                    } finally {
                        updating = false
                    }
                }
                update()
                if (removeOnExpire) {
                    removeAll()
                    BossBarManager.delete(this)
                }
                expired = true
            } else {
                update()
            }
        }
        for (child in _children.toList()) {
            child.tick()
        }
        return expired
    }

    fun removeAll() {
        val nms = UnifyCore.instance.nms
        if (nms != null) {
            for (uuid in viewers.toList()) {
                val player = Bukkit.getPlayer(uuid) ?: continue
                nms.hideBossBar(player, this)
            }
        }
        viewers.clear()
        for (child in _children.toList()) {
            child.removeAll()
        }
    }

    internal fun renderFor(player: Player) {
        if (autoProgress) {
            progress = computedProgress()
        }
        title = applyIndent(applyPlaceholders(titleTemplate, player))
    }

    private fun computedProgress(): Double {
        val duration = durationMillis ?: return progress
        if (duration <= 0L) return 0.0
        return (remainingMillis().toDouble() / duration.toDouble()).coerceIn(0.0, 1.0)
    }

    private fun applyPlaceholders(raw: String, player: Player): String {
        var text = raw
            .replace("{player}", player.name)
            .replace("{world}", world?.name ?: player.world.name)
            .replace("{online}", Bukkit.getOnlinePlayers().size.toString())
            .replace("{max_players}", Bukkit.getMaxPlayers().toString())
        val remaining = remainingMillis()
        if (text.contains("{time}")) {
            val label = if (expiresAt == null) "" else Time.formatCompact(remaining)
            text = text.replace("{time}", label)
        }
        return CC.translate(text)
    }

    private fun applyIndent(rendered: String): String {
        if (indent <= 0) return rendered
        val prefix = indentPrefix().repeat(indent)
        return prefix + rendered
    }

    private fun hideFromViewers() {
        val nms = UnifyCore.instance.nms ?: return
        for (uuid in viewers) {
            val player = Bukkit.getPlayer(uuid) ?: continue
            nms.hideBossBar(player, this)
        }
        for (child in _children) {
            child.visible = false
        }
    }

    private fun reshowViewers() {
        val nms = UnifyCore.instance.nms ?: return
        for (uuid in viewers) {
            val player = Bukkit.getPlayer(uuid) ?: continue
            renderFor(player)
            nms.showBossBar(player, this)
        }
        for (child in _children) {
            child.visible = true
        }
    }

    private fun isDescendantOf(other: UnifyBossBar): Boolean {
        var cursor = parent
        while (cursor != null) {
            if (cursor === other) return true
            cursor = cursor.parent
        }
        return false
    }

    companion object {
        private val live = ConcurrentHashMap<UUID, WeakReference<UnifyBossBar>>()
        private val NUMBER_FORMAT: NumberFormat = NumberFormat.getIntegerInstance(Locale.US)

        @JvmStatic
        fun create(title: String): UnifyBossBar = UnifyBossBar(title)

        @JvmStatic
        fun create(title: String, progress: Double): UnifyBossBar = UnifyBossBar(title, progress)

        @JvmStatic
        fun create(
            title: String,
            progress: Double,
            color: BossBarColor,
            style: BossBarStyle,
        ): UnifyBossBar = UnifyBossBar(title, progress, color, style)

        /**
         * Rank row matching the nested leaderboard style from event HUDs:
         * gold `#1`, yellow otherwise, US-grouped score, progress relative to [maxScore].
         */
        @JvmStatic
        @JvmOverloads
        fun ranked(
            rank: Int,
            name: String,
            score: Number,
            maxScore: Number,
            color: BossBarColor = BossBarColor.GREEN,
        ): UnifyBossBar {
            val rankColor = if (rank == 1) "&6" else "&e"
            val formatted = NUMBER_FORMAT.format(score.toLong())
            val max = maxScore.toDouble().coerceAtLeast(1.0)
            return UnifyBossBar(
                title = "$rankColor#$rank &f$name &7($formatted)",
                progress = (score.toDouble() / max).coerceIn(0.0, 1.0),
                color = color,
            )
        }

        internal fun allLive(): List<UnifyBossBar> {
            val bars = ArrayList<UnifyBossBar>(live.size)
            val stale = ArrayList<UUID>()
            for ((id, ref) in live) {
                val bar = ref.get()
                if (bar == null) stale += id else bars += bar
            }
            stale.forEach { live.remove(it) }
            return bars
        }

        internal fun drop(bar: UnifyBossBar) {
            live.remove(bar.uuid)
        }

        internal fun indentPrefix(): String {
            return try {
                UnifyConfig.BossBar.indent.get()
            } catch (_: Exception) {
                "  "
            }
        }
    }
}

fun bossBar(title: String, block: UnifyBossBar.() -> Unit = {}): UnifyBossBar {
    return UnifyBossBar(title).apply(block)
}
