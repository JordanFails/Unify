package me.jordanfails.unify.utils

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class Expirable(
    val addedAt: Long = System.currentTimeMillis(),
    val duration: Duration,
) {
    companion object {
        /** Creates an Expirable that never expires. */
        fun permanent() = Expirable(duration = Duration.INFINITE)
    }

    /** Timestamp (epoch ms) at which this expires, or null if permanent. */
    val expiresAt: Long? = if (duration.isInfinite()) null
    else addedAt + duration.inWholeMilliseconds

    val isActive: Boolean get() = expiresAt == null || System.currentTimeMillis() < expiresAt

    val isExpired: Boolean get() = !isActive

    /** How much time remains, or null if permanent. Returns zero if already expired. */
    val remaining: Duration? get() = expiresAt?.let {
        maxOf(0L, it - System.currentTimeMillis()).milliseconds
    }

    /**
     * Returns a new Expirable with a fresh start time,
     * optionally overriding the duration.
     */
    fun reset(newDuration: Duration = duration) = copy(
        addedAt = System.currentTimeMillis(),
        duration = newDuration
    )

    /**
     * Returns a new Expirable that is already expired.
     * Since expiry is time-derived, we achieve this by backdating addedAt.
     */
    fun expiredCopy() = copy(
        addedAt = 0L,
        duration = 1.milliseconds
    )
}