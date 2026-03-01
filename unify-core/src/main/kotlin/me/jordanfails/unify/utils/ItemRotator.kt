package me.jordanfails.unify.utils

import me.jordanfails.unify.UnifyCore
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask

class ItemRotator(
    private val items: List<ItemStack>,
    private val intervalTicks: Long = 5L // 0.25s between updates
) {

    init {
        require(items.isNotEmpty()) { "Item list cannot be empty" }
        start()
    }

    private var index = 0
    private var current: ItemStack = items.first()
    private var task: BukkitTask? = null

    /** Starts cycling through items. */
    fun start() {
        if (task != null) return
        task = UnifyCore.instance.server.scheduler.runTaskTimer(
            UnifyCore.instance,
            Runnable {
                index = (index + 1) % items.size
                current = items[index]
            },
            0L,
            intervalTicks
        )
    }

    /** Stops the rotation task. */
    fun stop() {
        task?.cancel()
        task = null
    }

    /** Returns the currently active ItemStack in the cycle. */
    fun getCurrent(): ItemStack = current.clone()
}