package me.jordanfails.unify.bossbar

import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.utils.CC
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Cross-version BossBar API.
 * Uses Bukkit's BossBar API for 1.9+ and NMS Wither packets for 1.8.
 */
class UnifyBossBar(
    title: String,
    var progress: Double = 1.0,
    var color: BossBarColor = BossBarColor.PURPLE,
    var style: BossBarStyle = BossBarStyle.SOLID
) {
    
    var title: String = title
        set(value) {
            field = CC.translate(value)
            update()
        }
    
    private val viewers = ConcurrentHashMap.newKeySet<UUID>()
    
    init {
        this.title = CC.translate(title)
    }
    
    /**
     * Add a player to see this boss bar.
     */
    fun addPlayer(player: Player) {
        if (viewers.add(player.uniqueId)) {
            UnifyCore.instance.nms?.showBossBar(player, this)
        }
    }
    
    /**
     * Remove a player from seeing this boss bar.
     */
    fun removePlayer(player: Player) {
        if (viewers.remove(player.uniqueId)) {
            UnifyCore.instance.nms?.hideBossBar(player, this)
        }
    }
    
    /**
     * Check if a player can see this boss bar.
     */
    fun hasPlayer(player: Player): Boolean {
        return viewers.contains(player.uniqueId)
    }
    
    /**
     * Get all players viewing this boss bar.
     */
    fun getPlayers(): Set<UUID> {
        return viewers.toSet()
    }
    
    /**
     * Set the progress (0.0 to 1.0).
     */
    fun setProgress(value: Double): UnifyBossBar {
        this.progress = value.coerceIn(0.0, 1.0)
        update()
        return this
    }
    
    /**
     * Set the title.
     */
    fun setTitle(value: String): UnifyBossBar {
        this.title = CC.translate(value)
        update()
        return this
    }
    
    /**
     * Set the color.
     */
    fun setColor(value: BossBarColor): UnifyBossBar {
        this.color = value
        update()
        return this
    }
    
    /**
     * Set the style.
     */
    fun setStyle(value: BossBarStyle): UnifyBossBar {
        this.style = value
        update()
        return this
    }
    
    /**
     * Update the boss bar for all viewers.
     */
    fun update() {
        val nms = UnifyCore.instance.nms ?: return
        for (uuid in viewers) {
            val player = org.bukkit.Bukkit.getPlayer(uuid) ?: continue
            nms.updateBossBar(player, this)
        }
    }
    
    /**
     * Remove this boss bar from all players.
     */
    fun removeAll() {
        val nms = UnifyCore.instance.nms ?: return
        for (uuid in viewers.toList()) {
            val player = org.bukkit.Bukkit.getPlayer(uuid) ?: continue
            nms.hideBossBar(player, this)
        }
        viewers.clear()
    }
    
    /**
     * Unique ID for this boss bar (used for tracking).
     */
    val uuid: UUID = UUID.randomUUID()
    
    companion object {
        /**
         * Create a boss bar with the given title.
         */
        fun create(title: String): UnifyBossBar {
            return UnifyBossBar(title)
        }
        
        /**
         * Create a boss bar with title and progress.
         */
        fun create(title: String, progress: Double): UnifyBossBar {
            return UnifyBossBar(title, progress)
        }
        
        /**
         * Create a boss bar with all options.
         */
        fun create(
            title: String,
            progress: Double,
            color: BossBarColor,
            style: BossBarStyle
        ): UnifyBossBar {
            return UnifyBossBar(title, progress, color, style)
        }
    }
}

enum class BossBarColor {
    PINK,
    BLUE,
    RED,
    GREEN,
    YELLOW,
    PURPLE,
    WHITE
}

enum class BossBarStyle {
    SOLID,
    SEGMENTED_6,
    SEGMENTED_10,
    SEGMENTED_12,
    SEGMENTED_20
}
