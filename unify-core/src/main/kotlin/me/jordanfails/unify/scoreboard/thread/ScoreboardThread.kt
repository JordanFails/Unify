package me.jordanfails.unify.scoreboard.thread

import me.jordanfails.unify.scoreboard.ScoreboardHandler
import me.jordanfails.unify.scoreboard.ScoreboardUpdate
import java.util.concurrent.ConcurrentHashMap

class ScoreboardThread : Thread("Unify - Scoreboard Thread") {

    companion object {
        val pendingUpdates = ConcurrentHashMap<ScoreboardUpdate, Boolean>()
    }

    init {
        isDaemon = true
    }

    override fun run() {
        while (true) {
            val updates = ArrayList(pendingUpdates.keys)
            
            for (update in updates) {
                try {
                    ScoreboardHandler.applyUpdate(update)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                pendingUpdates.remove(update)
            }

            try {
                sleep(50L)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }
}
