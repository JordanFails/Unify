package me.jordanfails.unify.utils

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask

object Tasks {

    /**
     * Run a task on the main thread immediately.
     */
    fun run(plugin: Plugin, block: () -> Unit): BukkitTask =
        Bukkit.getScheduler().runTask(plugin, Runnable { block() })

    /**
     * Run a task asynchronously immediately.
     */
    fun runAsync(plugin: Plugin, block: () -> Unit): BukkitTask =
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { block() })

    /**
     * Run a repeating task on the main thread with the given interval (in ticks).
     * Example: interval = 20L → runs every second.
     */
    fun runTimer(plugin: Plugin, interval: Long, block: () -> Unit): BukkitTask =
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable { block() }, 0L, interval)

    /**
     * Run a repeating task asynchronously with the given interval (in ticks).
     */
    fun runTimerAsync(plugin: Plugin, interval: Long, block: () -> Unit): BukkitTask =
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable { block() }, 0L, interval)

    /**
     * Run a delayed task on the main thread (delay in ticks).
     * Example: delay = 40L → runs after 2 seconds.
     */
    fun runLater(plugin: Plugin, delay: Long, block: () -> Unit): BukkitTask =
        Bukkit.getScheduler().runTaskLater(plugin, Runnable { block() }, delay)

    /**
     * Run a delayed task asynchronously (delay in ticks).
     */
    fun runLaterAsync(plugin: Plugin, delay: Long, block: () -> Unit): BukkitTask =
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, Runnable { block() }, delay)

    /**
     * Kotlin-style helper for delayed execution (main thread).
     * More idiomatic alternative to traditional Runnable overloads.
     */
    fun delayed(plugin: Plugin, delay: Long, block: () -> Unit): BukkitTask =
        Bukkit.getScheduler().runTaskLater(plugin, Runnable { block() }, delay)

    /**
     * Overload for delayed execution using a BukkitRunnable.
     */
    fun delayed(plugin: Plugin, delay: Long, runnable: BukkitRunnable): BukkitTask =
        runnable.runTaskLater(plugin, delay)
}