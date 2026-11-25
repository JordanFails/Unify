package me.jordanfails.unify.nametag.thread

import me.jordanfails.unify.nametag.NametagHandler

internal class NametagThread : Thread("Unify - Nametag Thread") {

    init {
        isDaemon = true
    }

    override fun run() {
        while (!isInterrupted) {
            for (update in NametagHandler.pending.toList()) {
                NametagHandler.pending.remove(update)
                try {
                    NametagHandler.applyUpdate(update)
                } catch (e: Exception) {
                    e.printStackTrace()
                } catch (e: LinkageError) {
                    // A class from a reloaded plugin; drop the update instead of killing the thread.
                    e.printStackTrace()
                }
            }

            try {
                sleep(NametagHandler.updateInterval * 50L)
            } catch (_: InterruptedException) {
                return
            }
        }
    }
}
