package me.jordanfails.unify.request

import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.utils.CC
import me.jordanfails.unify.utils.checkMainThread
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.logging.Level
import java.util.function.Consumer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object PlayerRequests : Listener {
    private val pending = linkedMapOf<UUID, PlayerRequest>()
    private var task: BukkitTask? = null
    private var enabled = false

    internal fun enable(plugin: UnifyCore) {
        enabled = true
        plugin.server.pluginManager.registerEvents(this, plugin)
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            pending.values.toList().filter { it.remaining == Duration.ZERO }
                .forEach { finish(it, RequestState.EXPIRED) }
        }, 20L, 20L)
    }

    internal fun disable() {
        enabled = false
        task?.cancel()
        task = null
        pending.values.toList().forEach { finish(it, RequestState.OWNER_DISABLED) }
    }

    /** Only one pending request per owner, category, sender and recipient is allowed. */
    @JvmStatic
    fun send(
        owner: Plugin,
        sender: Player,
        recipient: Player,
        category: String,
        title: String,
        timeout: Duration = 60.seconds,
        notifyPlayers: Boolean = true,
        onResult: (PlayerRequest) -> Unit,
    ): PlayerRequest {
        checkMainThread()
        check(enabled && owner.isEnabled) { "The request service and owning plugin must be enabled" }
        require(sender.isOnline && recipient.isOnline) { "Both players must be online" }
        require(sender.uniqueId != recipient.uniqueId) { "A player cannot send a request to themselves" }
        require(category.isNotBlank()) { "A request category is required" }
        require(title.isNotBlank()) { "A request title is required" }
        require(timeout.isFinite() && timeout > Duration.ZERO) { "Timeout must be positive and finite" }
        pending.values.toList().filter { it.remaining == Duration.ZERO }
            .forEach { finish(it, RequestState.EXPIRED) }
        check(enabled && owner.isEnabled) { "The request service and owning plugin must be enabled" }
        check(pending.values.none {
            it.owner === owner && it.category == category &&
                it.senderId == sender.uniqueId && it.recipientId == recipient.uniqueId
        }) { "An equivalent request is already pending" }

        val request = PlayerRequest(owner, sender.uniqueId, recipient.uniqueId, category, title,
            timeout, notifyPlayers, onResult)
        pending[request.id] = request
        if (notifyPlayers) {
            sender.sendMessage(CC.translate("&aRequest sent to ${recipient.name}: &f$title"))
            recipient.sendMessage(CC.translate("&e${sender.name} sent you a request: &f$title"))
            val buttons = Component.text("[Accept]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/unifyrequest accept ${request.id}"))
                .append(Component.text("  "))
                .append(Component.text("[Decline]", NamedTextColor.RED)
                    .clickEvent(ClickEvent.runCommand("/unifyrequest decline ${request.id}")))
            val audience = UnifyCore.instance.audience
            if (audience != null) audience.player(recipient).sendMessage(buttons)
            else recipient.sendMessage("/unifyrequest accept ${request.id} or /unifyrequest decline ${request.id}")
        }
        return request
    }

    @JvmStatic
    @JvmOverloads
    fun send(
        owner: Plugin,
        sender: Player,
        recipient: Player,
        category: String,
        title: String,
        timeoutMillis: Long = 60_000L,
        notifyPlayers: Boolean = true,
        onResult: Consumer<PlayerRequest>,
    ): PlayerRequest = send(owner, sender, recipient, category, title,
        timeoutMillis.milliseconds, notifyPlayers) { onResult.accept(it) }

    @JvmStatic
    fun get(id: UUID): PlayerRequest? {
        checkMainThread()
        return pending[id]
    }

    @JvmStatic
    fun incoming(player: Player): List<PlayerRequest> {
        checkMainThread()
        return pending.values.filter { it.recipientId == player.uniqueId }
    }

    @JvmStatic
    fun outgoing(player: Player): List<PlayerRequest> {
        checkMainThread()
        return pending.values.filter { it.senderId == player.uniqueId }
    }

    internal fun respond(request: PlayerRequest, player: Player, state: RequestState): Boolean {
        checkMainThread()
        if (player.uniqueId != request.recipientId || !player.isOnline) return false
        if (request.remaining == Duration.ZERO) {
            finish(request, RequestState.EXPIRED)
            return false
        }
        if (Bukkit.getPlayer(request.senderId)?.isOnline != true) {
            finish(request, RequestState.DISCONNECTED)
            return false
        }
        return finish(request, state)
    }

    internal fun finish(request: PlayerRequest, state: RequestState): Boolean {
        checkMainThread()
        if (pending[request.id] !== request || request.state != RequestState.PENDING) return false
        pending.remove(request.id)
        request.state = state
        if (request.notifyPlayers) {
            val message = CC.translate("&7Request '${request.title}': ${state.name.lowercase().replace('_', ' ')}.")
            Bukkit.getPlayer(request.senderId)?.sendMessage(message)
            Bukkit.getPlayer(request.recipientId)?.sendMessage(message)
        }
        try {
            request.onResult(request)
        } catch (error: Exception) {
            request.owner.logger.log(Level.SEVERE, "Request ${request.id} result callback failed", error)
        }
        return true
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        pending.values.toList().filter {
            it.senderId == event.player.uniqueId || it.recipientId == event.player.uniqueId
        }.forEach { finish(it, RequestState.DISCONNECTED) }
    }

    @EventHandler
    fun onPluginDisable(event: PluginDisableEvent) {
        pending.values.toList().filter { it.owner === event.plugin }
            .forEach { finish(it, RequestState.OWNER_DISABLED) }
    }
}
