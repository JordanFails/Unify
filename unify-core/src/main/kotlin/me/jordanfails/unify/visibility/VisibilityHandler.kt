package me.jordanfails.unify.visibility

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
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

    fun registerAdapter(adapter: VisibilityAdapter) {
        adapters.add(adapter)
        adapters.sortByDescending { it.weight }
    }

    fun registerOverride(handler: OverrideHandler) {
        overrideHandlers.add(handler)
        overrideHandlers.sortByDescending { it.weight }
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
            if (!shouldSee(target, viewer)) {
                viewer.hidePlayer(target)
            } else {
                viewer.showPlayer(target)
            }
        }
    }

    fun updateToAll(target: Player) {
        for (viewer in Bukkit.getOnlinePlayers()) {
            if (!shouldSee(target, viewer)) {
                viewer.hidePlayer(target)
            } else {
                viewer.showPlayer(target)
            }
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
