package me.jordanfails.unify.utils

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Represents something that is active for a period of time and then expires.
 *
 * Use this for anything with a lifespan: mutes, buffs, cooldowns, temporary
 * roles, cached tokens, etc. Time is checked lazily (on access), not with a
 * scheduled task — so there's no timer/thread running in the background.
 *
 * ### Basic usage
 * ```kotlin
 * val mute = Expirable(duration = 10.minutes)
 *
 * if (mute.isActive) {
 *     println("Still muted, ${mute.remaining} left")
 * }
 * ```
 *
 * ### Permanent (never expires)
 * ```kotlin
 * val ban = Expirable.permanent()
 * ban.isActive // always true
 * ban.expiresAt // always null
 * ```
 *
 * ### Manually cancelling early
 * Sometimes you need to end something before its natural expiry (e.g. an
 * admin unmutes a player, or a token is manually invalidated). Use [revoke]
 * for this instead of trying to fake expiry with timestamps:
 * ```kotlin
 * var mute = Expirable(duration = 10.minutes)
 * mute = mute.revoke() // now inactive immediately, regardless of duration
 * mute.isActive // false
 * mute.isRevoked // true
 * mute.isExpired // still false! it didn't run out, it was cancelled
 * ```
 *
 * ### Refreshing / extending
 * ```kotlin
 * mute = mute.reset() // restarts the timer with the same duration
 * mute = mute.reset(newDuration = 30.minutes) // restarts with a new duration
 * ```
 *
 * @property addedAt Epoch millis when this started counting down.
 * @property duration How long this lasts. Use [Duration.INFINITE] for permanent.
 * @property revoked Manual override — if true, this is inactive regardless of duration.
 */
data class Expirable(
    val addedAt: Long = System.currentTimeMillis(),
    val duration: Duration,
    val revoked: Boolean = false,
) {
    companion object {
        /** Creates an Expirable that never expires (until manually [revoke]d). */
        fun permanent() = Expirable(duration = Duration.INFINITE)
    }

    /** Timestamp (epoch ms) this naturally expires at, or null if [duration] is infinite. */
    val expiresAt: Long? = if (duration.isInfinite()) null
    else addedAt + duration.inWholeMilliseconds

    /**
     * True if this has run past its [duration]. Does NOT account for manual
     * [revoke]cation — use [isActive] if you just want to know "can I use this right now?"
     */
    val isExpired: Boolean get() = expiresAt != null && System.currentTimeMillis() >= expiresAt

    /** True if this was manually canceled via [revoke], separate from time-based expiry. */
    val isRevoked: Boolean get() = revoked

    /**
     * The single check you almost always want: is this currently usable?
     * False if it's either run out of time OR been manually [revoke]d.
     */
    val isActive: Boolean get() = !revoked && !isExpired

    /**
     * How much time remains, or null if permanent.
     * Returns zero once expired OR revoked — never negative.
     */
    val remaining: Duration? get() = when {
        revoked -> 0.milliseconds
        expiresAt == null -> null
        else -> maxOf(0L, expiresAt - System.currentTimeMillis()).milliseconds
    }

    /**
     * Returns a new Expirable with a fresh start time and (optionally) a new
     * duration. Also clears any prior [revoked] state — resetting un-revokes it.
     */
    fun reset(newDuration: Duration = duration) = copy(
        addedAt = System.currentTimeMillis(),
        duration = newDuration,
        revoked = false,
    )

    /**
     * Manually cancels this immediately, regardless of remaining duration.
     * Prefer this over faking an expired timestamp — it keeps the original
     * [addedAt]/[duration] intact for auditing, and lets you tell the
     * difference between "ran out" ([isExpired]) and "cancelled" ([isRevoked]).
     */
    fun revoke() = copy(revoked = true)

    /** Reverses [revoke] — makes this active again (if not also time-expired). */
    fun unrevoke() = copy(revoked = false)
}