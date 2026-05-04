package me.jordanfails.unify.utils

import me.jordanfails.unify.UnifyCore
import org.bukkit.Material
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

/**
 * Cycles through a list of 5 materials on a repeating task.
 * You can call [getCurrent] at any time to get whichever material is "active"
 * in the rotation at that moment.
 */
class MaterialRotator(
    private val materials: List<Material>,
    private val intervalTicks: Long = 20L,
    private val plugin: JavaPlugin = UnifyCore.instance
) {

    init {
        require(materials.isNotEmpty()) { "Material list cannot be empty" }
        start()
    }

    private var index = 0
    private var current: Material = materials.first()
    private var task: BukkitTask? = null

    /** Start rotating between materials. */
    fun start() {
        if (task != null) return
        task = Tasks.runLater(
            plugin,
            intervalTicks,
        ) {
            index = (index + 1) % materials.size
            current = materials[index]
        }
    }

    /** Stop the rotation. */
    fun stop() {
        task?.cancel()
        task = null
    }

    /** Returns the current active Material in the rotation. */
    fun getCurrent(): Material = current
}