package me.jordanfails.unify.npc

import me.jordanfails.unify.UnifyCore
import org.bukkit.Bukkit
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Turns the three skin sources into a signed texture blob, off the main thread.
 *
 * The old implementation called Mojang from inside the spawn path, which meant either a network
 * round-trip on the server thread or (as it ended up) a cache-only lookup that spawned the NPC
 * bald and repaired it later. Resolution is asynchronous here by construction: [resolve] never
 * blocks, and its callback is handed back on the main thread so callers can touch the world
 * directly.
 *
 * Only NAME lookups hit the network. URL is assembled locally and BASE64 is already the answer.
 */
object SkinResolver {

    /** Resolved NAME skins, keyed by lowercase player name. Never expires within a session. */
    private val nameCache = ConcurrentHashMap<String, NPCSkin>()

    /** Names with a lookup already in flight, so N NPCs sharing a skin cause one request, not N. */
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    /**
     * Callbacks waiting on an in-flight lookup, keyed by lowercase name.
     *
     * Everyone who asks for a name still gets an answer, even if someone else triggered the
     * fetch. Reporting failure to all but the first caller meant two NPCs sharing a skin left one
     * of them permanently on the default body.
     */
    private val waiting = ConcurrentHashMap<String, MutableList<(NPCSkin?) -> Unit>>()

    private const val UUID_ENDPOINT = "https://api.mojang.com/users/profiles/minecraft/"
    private const val PROFILE_ENDPOINT = "https://sessionserver.mojang.com/session/minecraft/profile/"

    /** A previously resolved NAME skin, if one is cached. Safe on the main thread. */
    fun cached(name: String): NPCSkin? = nameCache[name.lowercase()]

    /**
     * Resolves a skin and invokes [callback] on the main thread — with null if resolution failed.
     *
     * For BASE64 and URL sources the callback may run synchronously, before this method returns.
     */
    fun resolve(sourceType: NPCSkin.SourceType, source: String, callback: (NPCSkin?) -> Unit) {
        when (sourceType) {
            NPCSkin.SourceType.BASE64 -> {
                // `base64:<value>;<signature>` — the signature half is optional. Unsigned skins
                // render fine; they just cannot be verified by the client.
                val parts = source.split(';', limit = 2)
                callback(NPCSkin(parts[0].trim(), parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }, sourceType, source))
            }

            NPCSkin.SourceType.URL -> {
                val json = """{"textures":{"SKIN":{"url":"${source.trim()}"}}}"""
                val encoded = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
                callback(NPCSkin(encoded, null, sourceType, source))
            }

            NPCSkin.SourceType.NAME -> resolveName(source.trim(), callback)
        }
    }

    private fun resolveName(name: String, callback: (NPCSkin?) -> Unit) {
        val key = name.lowercase()
        nameCache[key]?.let {
            callback(it)
            return
        }

        // Queue behind any lookup already running for this name, so two NPCs created back to back
        // with the same skin cause one request but both still get answered.
        waiting.computeIfAbsent(key) { CopyOnWriteArrayList() }.add(callback)
        if (!inFlight.add(key)) return

        Bukkit.getScheduler().runTaskAsynchronously(UnifyCore.instance, Runnable {
            val resolved = runCatching { fetchProfileSkin(name) }
                .onFailure { UnifyCore.instance.logger.warning("Skin lookup for '$name' failed: ${it.message}") }
                .getOrNull()

            if (resolved != null) nameCache[key] = resolved
            // Cleared before draining: a callback registered after this point starts a fresh
            // lookup, which then hits the cache immediately rather than waiting on a queue that
            // has already been emptied.
            inFlight.remove(key)

            Bukkit.getScheduler().runTask(UnifyCore.instance, Runnable {
                waiting.remove(key)?.forEach { waiter ->
                    runCatching { waiter(resolved) }
                        .onFailure { UnifyCore.instance.logger.warning("Skin callback for '$name' failed: ${it.message}") }
                }
            })
        })
    }

    /** Blocking Mojang lookup: name to UUID, then UUID to signed textures. Async callers only. */
    private fun fetchProfileSkin(name: String): NPCSkin? {
        val idJson = httpGet(UUID_ENDPOINT + name) ?: return null
        val id = extractJsonString(idJson, "id") ?: return null

        val profileJson = httpGet("$PROFILE_ENDPOINT$id?unsigned=false") ?: return null
        val value = extractJsonString(profileJson, "value") ?: return null
        val signature = extractJsonString(profileJson, "signature")

        return NPCSkin(value, signature, NPCSkin.SourceType.NAME, name)
    }

    private fun httpGet(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.setRequestProperty("User-Agent", "Unify")

            // 204 is Mojang's "no such player" for the name endpoint, not an error condition.
            if (connection.responseCode != 200) return null

            val buffer = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val chunk = ByteArray(4096)
                while (true) {
                    val read = input.read(chunk)
                    if (read == -1) break
                    buffer.write(chunk, 0, read)
                }
            }
            buffer.toString("UTF-8")
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Pulls a string field out of a Mojang response.
     *
     * Deliberately not a JSON parser: Gson is relocated on some legacy Spigot builds and absent
     * from others, and these two responses have a fixed, flat shape. Texture values are base64
     * and signatures are base64 too, so neither can contain an escaped quote to trip this up.
     */
    private fun extractJsonString(json: String, field: String): String? {
        val marker = "\"$field\""
        val fieldIndex = json.indexOf(marker)
        if (fieldIndex == -1) return null

        val colon = json.indexOf(':', fieldIndex + marker.length)
        if (colon == -1) return null

        val open = json.indexOf('"', colon + 1)
        if (open == -1) return null

        val close = json.indexOf('"', open + 1)
        if (close == -1) return null

        return json.substring(open + 1, close).takeIf { it.isNotEmpty() }
    }
}
