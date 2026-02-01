package me.jordanfails.unify.nametag.thread

import me.jordanfails.unify.nametag.NametagHandler
import me.jordanfails.unify.nametag.update.NametagUpdate
import java.util.concurrent.ConcurrentHashMap

internal class NametagThread : Thread("Unify - Nametag Thread") {

    init {
        this.isDaemon = true
    }

    override fun run() {
        while (true) {
            val updatesIterator = pendingUpdates.keys.iterator()
            while (updatesIterator.hasNext()) {
                val pendingUpdate = updatesIterator.next()
                try {
                    NametagHandler.applyUpdate(pendingUpdate)
                    updatesIterator.remove()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

            }

            try {
                sleep(NametagHandler.updateInterval * 50L)
            } catch (e2: InterruptedException) {
                e2.printStackTrace()
            }

        }
    }

    companion object {
        var pendingUpdates = ConcurrentHashMap<NametagUpdate, Boolean>()
    }
}