package me.jordanfails.unify.plugin

import me.jordanfails.unify.UnifyCore
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import java.util.Collections
import java.util.IdentityHashMap
import java.util.logging.Level

/**
 * The plugin whose jar defines [value]'s class (lambdas included), or null for server/JDK classes.
 * A [Class] is resolved as itself rather than as java.lang.Class.
 */
internal fun owningPlugin(value: Any): Plugin? = try {
    JavaPlugin.getProvidingPlugin(value as? Class<*> ?: value.javaClass)
} catch (_: IllegalArgumentException) {
    null
} catch (_: IllegalStateException) {
    null
}

// Frames from these never belong to a dependent plugin, so they're skipped without a class lookup.
private val serverPrefixes = listOf(
    "java.", "javax.", "jdk.", "sun.", "kotlin.", "kotlinx.", "org.bukkit.", "org.spigotmc.", "io.papermc.",
    "com.destroystokyo.", "net.minecraft.", "com.mojang.", "com.google.", "co.aikar.", "me.jordanfails.unify.",
)

/**
 * The first plugin (other than Unify) on the current call stack, for APIs that hand out objects
 * without an owner parameter. Null when Unify itself or the server is the caller.
 */
internal fun callingPlugin(): Plugin? {
    for (frame in Thread.currentThread().stackTrace) {
        val name = frame.className
        if (serverPrefixes.any(name::startsWith)) continue
        return Bukkit.getPluginManager().plugins.firstOrNull { plugin ->
            val loader = plugin.javaClass.classLoader
            try {
                Class.forName(name, false, loader).classLoader === loader
            } catch (_: ClassNotFoundException) {
                false
            } catch (_: LinkageError) {
                false
            }
        } ?: continue
    }
    return null
}

/**
 * Providers for one Unify system. Unify's built-ins are swapped on every reload; providers from
 * other plugins survive reloads and are dropped when the plugin that registered them disables.
 */
internal class ProviderRegistry<T : Any>(private val weight: (T) -> Int) {
    private var builtIns: List<T> = emptyList()
    private val external = ArrayList<T>()

    // Logged once per provider so a broken one can't flood the console every refresh.
    private val reported: MutableSet<T> = Collections.synchronizedSet(Collections.newSetFromMap(IdentityHashMap()))

    /** Highest weight first. Replaced on every change, so async readers can iterate it without locking. */
    @Volatile
    var sorted: List<T> = emptyList()
        private set

    val size: Int get() = sorted.size

    fun isEmpty(): Boolean = sorted.isEmpty()

    @Synchronized
    fun setBuiltIns(providers: List<T>) {
        builtIns = providers
        resort()
    }

    @Synchronized
    fun add(provider: T) {
        external += provider
        resort()
    }

    /** Returns true if anything was removed. */
    @Synchronized
    fun removeOwnedBy(plugin: Plugin): Boolean {
        val removed = external.filter { owningPlugin(it) === plugin }
        external.removeAll(removed)
        reported.removeAll(removed)
        if (removed.isNotEmpty()) resort()
        return removed.isNotEmpty()
    }

    /** The first non-null answer, skipping (and reporting) providers that throw. */
    fun <R : Any> firstOf(fetch: (T) -> R?): R? {
        for (provider in sorted) {
            return safely(provider, fetch) ?: continue
        }
        return null
    }

    /** Every non-null answer, highest weight first, skipping (and reporting) providers that throw. */
    fun <R : Any> allOf(fetch: (T) -> R?): List<R> = sorted.mapNotNull { safely(it, fetch) }

    private fun <R : Any> safely(provider: T, fetch: (T) -> R?): R? = try {
        fetch(provider)
    } catch (error: Exception) {
        report(provider, error)
    } catch (error: LinkageError) {
        report(provider, error)
    }

    private fun report(provider: T, error: Throwable): Nothing? {
        if (reported.add(provider)) {
            val owner = owningPlugin(provider)?.name ?: "Unify"
            UnifyCore.instance.logger.log(Level.WARNING, "$owner's ${provider.javaClass.name} failed; skipping it until it recovers", error)
        }
        return null
    }

    // sortedByDescending is stable, so equal weights keep registration order (built-ins first).
    private fun resort() {
        sorted = (builtIns + external).sortedByDescending(weight)
    }
}
