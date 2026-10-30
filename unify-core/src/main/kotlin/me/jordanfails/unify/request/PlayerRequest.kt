package me.jordanfails.unify.request

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/** A one-shot request. All responses and the result callback run on the server thread. */
class PlayerRequest internal constructor(
    val owner: Plugin,
    val senderId: UUID,
    val recipientId: UUID,
    val category: String,
    val title: String,
    val timeout: Duration,
    internal val notifyPlayers: Boolean,
    internal val onResult: (PlayerRequest) -> Unit,
) : AutoCloseable {
    val id: UUID = UUID.randomUUID()
    private val startedAt = System.nanoTime()

    var state: RequestState = RequestState.PENDING
        internal set

    val remaining: Duration
        get() = if (state != RequestState.PENDING) Duration.ZERO
        else (timeout - (System.nanoTime() - startedAt).nanoseconds).coerceAtLeast(Duration.ZERO)

    val remainingMillis: Long
        get() = remaining.inWholeMilliseconds

    fun accept(player: Player): Boolean = PlayerRequests.respond(this, player, RequestState.ACCEPTED)

    fun decline(player: Player): Boolean = PlayerRequests.respond(this, player, RequestState.DECLINED)

    fun cancel(): Boolean = PlayerRequests.finish(this, RequestState.CANCELLED)

    override fun close() {
        cancel()
    }
}
