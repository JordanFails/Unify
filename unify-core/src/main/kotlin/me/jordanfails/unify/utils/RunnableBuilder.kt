package me.jordanfails.unify.utils

import me.jordanfails.unify.UnifyCore
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitScheduler

class RunnableBuilder private constructor(
    private val plugin: JavaPlugin
) {

    private var runnable: Runnable? = null
    private var taskId: Int = -1

    companion object {
        val scheduler: BukkitScheduler = Bukkit.getScheduler()

        @JvmStatic
        fun forPlugin(plugin: JavaPlugin): RunnableBuilder = RunnableBuilder(plugin)

        @JvmStatic
        fun bind(runnable: Runnable): RunnableBuilder =
            forPlugin(UnifyCore.instance).with(runnable)
    }

    fun with(runnable: Runnable): RunnableBuilder {
        this.runnable = runnable
        return this
    }

    fun cancel() {
        if (taskId != -1) {
            scheduler.cancelTask(taskId)
            taskId = -1
        }
    }

    fun isCancelled(): Boolean = taskId == -1

    fun isQueued(): Boolean = !isCancelled() && scheduler.isQueued(taskId)

    fun isRunning(): Boolean = !isCancelled() && scheduler.isCurrentlyRunning(taskId)

    fun runSync(): Int {
        taskId = scheduler.runTask(plugin, checkRunnable()).taskId
        return taskId
    }

    fun runSyncLater(delay: Long): Int {
        taskId = scheduler.runTaskLater(plugin, checkRunnable(), delay).taskId
        return taskId
    }

    fun runSyncTimer(delay: Long, interval: Long): Int {
        taskId = scheduler.runTaskTimer(plugin, checkRunnable(), delay, interval).taskId
        return taskId
    }

    fun runAsync(): Int {
        taskId = scheduler.runTaskAsynchronously(plugin, checkRunnable()).taskId
        return taskId
    }

    fun runAsyncLater(delay: Long): Int {
        taskId = scheduler.runTaskLaterAsynchronously(plugin, checkRunnable(), delay).taskId
        return taskId
    }

    fun runAsyncTimer(delay: Long, interval: Long): Int {
        taskId = scheduler.runTaskTimerAsynchronously(plugin, checkRunnable(), delay, interval).taskId
        return taskId
    }

    private fun checkRunnable(): Runnable {
        return runnable
            ?: throw kotlin.IllegalStateException("Runnable not set! Use .with(runnable) before running.")
    }
}