package me.jordanfails.unify.utils

import java.util.concurrent.TimeUnit

class Cooldown(seconds: Long) {
    val time: Long = seconds * 1000L

    constructor(seconds: Int): this(seconds.toLong())

    var startTime: Long
        private set

    init {
        this.startTime = System.currentTimeMillis()
    }

    val timeSinceStart: Long
        get() = System.currentTimeMillis() - this.startTime

    val remainingTime: Long
        get() = this.time - this.timeSinceStart

    fun getRemainingTime(timeUnit: TimeUnit): Long {
        return timeUnit.convert(this.remainingTime, TimeUnit.MILLISECONDS)
    }

    val isOver: Boolean
        get() = (this.startTime == 0L || this.remainingTime < 0L)

    fun restart() {
        this.startTime = System.currentTimeMillis()
    }

    fun stop() {
        this.startTime = 0L
    }

    val formattedTime: String
        get() {
            val time: String = TimeUnits.convertMillisToString(this.remainingTime, true)
            if (time == "") return "0 seconds"
            return time
        }
}