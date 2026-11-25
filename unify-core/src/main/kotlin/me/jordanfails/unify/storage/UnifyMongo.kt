package me.jordanfails.unify.storage

import com.mongodb.client.MongoClient
import me.jordanfails.honey.Honey
import me.jordanfails.honey.connection.mongo.SharedMongoConnectionPool
import me.jordanfails.honey.connection.mongo.URIMongoConnectionPool
import me.jordanfails.unify.Beta
import me.jordanfails.unify.UnifyCore
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin

/**
 * Mongo for plugins built on Unify. Each call gives the plugin its own [Honey] (its own stores,
 * serializers and Gson adapters), while every plugin using the same URI shares one [MongoClient],
 * as the Mongo driver recommends. The plugin's instances are closed after it disables, and a client
 * is closed once nobody uses it.
 *
 * ```kotlin
 * val honey = UnifyMongo.honey(this, uri = config.mongoUri, database = "core")
 * val profiles = honey.createStoreType<UUID, Profile>(DataStoreType.MONGO, "profiles")
 * ```
 */
@Beta
object UnifyMongo {

    private class SharedClient(val client: MongoClient, val users: MutableSet<Plugin> = HashSet())

    private val clients = HashMap<String, SharedClient>()
    private val owned = HashMap<Plugin, MutableList<Pair<String, Honey>>>()

    /** A [Honey] for [plugin] whose Mongo stores use [database] on the client for [uri]. */
    @JvmStatic
    @Synchronized
    fun honey(plugin: Plugin, uri: String, database: String): Honey {
        require(uri.isNotBlank()) { "Mongo URI is blank" }
        // Honey's URI pool already sets sensible timeouts; reuse it to build the shared client.
        val shared = clients.getOrPut(uri) { SharedClient(URIMongoConnectionPool().also { it.uri = uri }.getConnectionPool()) }
        shared.users += plugin
        val honey = Honey().withConnectionPool(SharedMongoConnectionPool(shared.client, database))
        owned.getOrPut(plugin) { ArrayList() } += uri to honey
        return honey
    }

    /**
     * Bukkit announces a disable before the plugin's own onDisable runs, which is where plugins
     * save their data, so the connection is released a tick later. When Unify itself is shutting
     * down, [closeAll] covers it instead.
     */
    internal fun forgetPlugin(plugin: Plugin) {
        if (!hasConnections(plugin)) return
        Bukkit.getScheduler().runTask(UnifyCore.instance, Runnable { release(plugin) })
    }

    @Synchronized
    private fun hasConnections(plugin: Plugin) = plugin in owned

    @Synchronized
    private fun release(plugin: Plugin) {
        val mine = owned.remove(plugin) ?: return
        for ((uri, honey) in mine) {
            honey.closeAllConnections() // flushes this plugin's pending writes; the shared client stays open
            val shared = clients[uri] ?: continue
            shared.users -= plugin
            if (shared.users.isEmpty()) {
                shared.client.close()
                clients.remove(uri)
            }
        }
    }

    /** Called when Unify disables, after every dependent has already run its onDisable. */
    @Synchronized
    internal fun closeAll() {
        owned.keys.toList().forEach(::release)
    }
}
