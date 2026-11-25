package me.jordanfails.unify.visibility

import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.module.UnifyModule
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import me.jordanfails.unify.plugin.owningPlugin
import java.util.*
import kotlin.collections.iterator

object VisibilityHandler {

    private val adapters = ArrayList<VisibilityAdapter>()
    private val overrideHandlers = ArrayList<OverrideHandler>()

    private data class VisibilityDecision(
        val source: Any,
        val sourceType: String,
        val name: String,
        val weight: Int,
        val canSee: Boolean
    )

    // 1.12.2+ tracks hides per plugin, so Unify showing a player can't undo another plugin's hide.
    private val pluginScopedHiding = runCatching {
        Player::class.java.getMethod("hidePlayer", Plugin::class.java, Player::class.java)
    }.isSuccess

    // Older servers share one hide flag between plugins, so Unify only re-shows pairs it hid itself.
    private val legacyHidden: MutableSet<Pair<UUID, UUID>> = Collections.synchronizedSet(HashSet())

    @Suppress("DEPRECATION")
    private fun setVisible(viewer: Player, target: Player, visible: Boolean) {
        when {
            pluginScopedHiding && visible -> viewer.showPlayer(UnifyCore.instance, target)
            pluginScopedHiding -> viewer.hidePlayer(UnifyCore.instance, target)
            visible -> if (legacyHidden.remove(viewer.uniqueId to target.uniqueId)) viewer.showPlayer(target)
            else -> if (legacyHidden.add(viewer.uniqueId to target.uniqueId)) viewer.hidePlayer(target)
        }
    }

    fun registerAdapter(adapter: VisibilityAdapter) {
        if (!UnifyModule.VISIBILITY.allowsRegistration(adapter)) return
        adapters.add(adapter)
        adapters.sortByDescending { it.weight }
    }

    fun registerOverride(handler: OverrideHandler) {
        if (!UnifyModule.VISIBILITY.allowsRegistration(handler)) return
        overrideHandlers.add(handler)
        overrideHandlers.sortByDescending { it.weight }
    }

    internal fun forgetPlugin(plugin: Plugin) {
        val removed = adapters.removeAll { owningPlugin(it) === plugin } or
            overrideHandlers.removeAll { owningPlugin(it) === plugin }
        if (removed) Bukkit.getOnlinePlayers().forEach { updateAllTo(it) }
    }

    fun update(player: Player) {
        if (adapters.isEmpty() && overrideHandlers.isEmpty()) {
            return
        }

        updateAllTo(player)
        updateToAll(player)
    }

    fun updateAllTo(viewer: Player) {
        for (target in Bukkit.getOnlinePlayers()) {
            setVisible(viewer, target, shouldSee(target, viewer))
        }
    }

    fun updateToAll(target: Player) {
        for (viewer in Bukkit.getOnlinePlayers()) {
            setVisible(viewer, target, shouldSee(target, viewer))
        }
    }

    fun treatAsOnline(target: Player, viewer: Player): Boolean {
        return viewer.canSee(target) || !VanishHandler.isVanished(target) || VanishHandler.canSeeVanished(viewer)
    }

    private fun shouldSee(target: Player, viewer: Player): Boolean {
        val decision = resolveDecision(target, viewer)
        return decision?.canSee ?: true
    }

    private fun resolveDecision(target: Player, viewer: Player): VisibilityDecision? {
        var topShow: VisibilityDecision? = null
        var topHide: VisibilityDecision? = null

        for (handler in overrideHandlers) {
            val action = handler.getAction(target, viewer)
            if (action == OverrideAction.SHOW) {
                if (topShow == null || handler.weight > topShow?.weight ?: Int.MIN_VALUE) {
                    topShow = VisibilityDecision(
                        source = handler,
                        sourceType = "Overriding Handler",
                        name = handler.name,
                        weight = handler.weight,
                        canSee = true
                    )
                }
            }
        }

        for (handler in adapters) {
            val action = handler.getAction(target, viewer)
            if (action == VisibilityAction.HIDE) {
                if (topHide == null || handler.weight > topHide?.weight ?: Int.MIN_VALUE) {
                    topHide = VisibilityDecision(
                        source = handler,
                        sourceType = "Normal Handler",
                        name = handler.name,
                        weight = handler.weight,
                        canSee = false
                    )
                }
            }
        }

        return when {
            topShow == null && topHide == null -> null
            topShow != null && (topHide == null || topShow.weight > topHide.weight) -> topShow
            else -> topHide
        }
    }

    fun getDebugInfo(target: Player, viewer: Player): List<String> {
        val debug = ArrayList<String>()
        val winningDecision = resolveDecision(target, viewer)
        val canSee = winningDecision?.canSee ?: true

        for (handler in overrideHandlers) {
            val action = handler.getAction(target, viewer)
            val color = if (winningDecision?.source === handler && action == OverrideAction.SHOW) ChatColor.GREEN else ChatColor.GRAY

            debug.add("${color}Overriding Handler: ${handler.name} (${handler.weight}): $action")
        }

        for (handler in adapters) {
            val action = handler.getAction(target, viewer)
            val color = if (winningDecision?.source === handler && action == VisibilityAction.HIDE) ChatColor.GREEN else ChatColor.GRAY

            debug.add("${color}Normal Handler: ${handler.name} (${handler.weight}): $action")
        }

        if (winningDecision != null) {
            debug.add("${ChatColor.YELLOW}Winner: ${winningDecision.sourceType}: ${winningDecision.name} (${winningDecision.weight})")
        }

        debug.add("${ChatColor.AQUA}Result: ${viewer.name} ${if (canSee) "can" else "cannot"} see ${target.name}")

        return debug
    }

}
