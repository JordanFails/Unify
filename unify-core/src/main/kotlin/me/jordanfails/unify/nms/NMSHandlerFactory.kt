package me.jordanfails.unify.nms

import org.bukkit.Bukkit

/**
 * Detects the current server version and provides the right NMS handler.
 */
object NMSHandlerFactory {

    private var nmsHandler: NMSHandler? = null

    /** Get the current NMS handler instance, or try to load it if missing. */
    fun getHandler(): NMSHandler? {
        if (nmsHandler != null) return nmsHandler

        val packageName = Bukkit.getServer().javaClass.`package`.name
        val version = packageName.split(".").lastOrNull() ?: return null

        val className = "me.jordanfails.unify.nms.$version.NMSHandler_${version}"
        return try {
            val clazz = Class.forName(className)
            val instance = clazz.getDeclaredConstructor().newInstance() as NMSHandler
            nmsHandler = instance
            Bukkit.getLogger().info("[Unify] Loaded NMS handler for $version")
            instance
        } catch (ex: Exception) {
            Bukkit.getLogger().warning("[Unify] No NMS handler found for version $version")
            null
        }
    }
}