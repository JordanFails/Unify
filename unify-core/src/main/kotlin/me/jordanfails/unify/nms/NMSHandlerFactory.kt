package me.jordanfails.unify.nms

import org.bukkit.Bukkit

/**
 * Detects the current server version and provides the right NMS handler.
 */
object NMSHandlerFactory {

    private var nmsHandler: NMSHandler? = null

    /**
     * Maps Minecraft versions to NMS version strings.
     * Format: "major.minor" -> "v1_XX_RX"
     */
    private val versionMap = mapOf(
        // 1.8.x
        "1.8" to "v1_8_R3",
        "1.8.1" to "v1_8_R3",
        "1.8.2" to "v1_8_R3",
        "1.8.3" to "v1_8_R3",
        "1.8.4" to "v1_8_R3",
        "1.8.5" to "v1_8_R3",
        "1.8.6" to "v1_8_R3",
        "1.8.7" to "v1_8_R3",
        "1.8.8" to "v1_8_R3",
        "1.8.9" to "v1_8_R3",
        
        // 1.9.x
        "1.9" to "v1_9_R2",
        "1.9.1" to "v1_9_R2",
        "1.9.2" to "v1_9_R2",
        "1.9.3" to "v1_9_R2",
        "1.9.4" to "v1_9_R2",
        
        // 1.12.x
        "1.12" to "v1_12_R1",
        "1.12.1" to "v1_12_R1",
        "1.12.2" to "v1_12_R1",
        
        // 1.16.x
        "1.16" to "v1_16_R3",
        "1.16.1" to "v1_16_R3",
        "1.16.2" to "v1_16_R3",
        "1.16.3" to "v1_16_R3",
        "1.16.4" to "v1_16_R3",
        "1.16.5" to "v1_16_R3",
        
        // 1.20.x (1.20.5+ uses Mojang mappings)
        "1.20" to "v1_20_R4",
        "1.20.1" to "v1_20_R4",
        "1.20.2" to "v1_20_R4",
        "1.20.3" to "v1_20_R4",
        "1.20.4" to "v1_20_R4",
        "1.20.5" to "v1_20_R4",
        "1.20.6" to "v1_20_R4",
        
        // 1.21.x
        "1.21" to "v1_21_R1",
        "1.21.1" to "v1_21_R1",
        "1.21.2" to "v1_21_R1",
        "1.21.3" to "v1_21_R1",
        "1.21.4" to "v1_21_R1"
    )

    /** Get the current NMS handler instance, or try to load it if missing. */
    fun getHandler(): NMSHandler? {
        if (nmsHandler != null) return nmsHandler

        val version = detectNMSVersion()
        if (version == null) {
            Bukkit.getLogger().warning("[Unify] Could not detect server version")
            return null
        }

        val className = "me.jordanfails.unify.nms.$version.NMSHandler_${version}"
        return try {
            val clazz = Class.forName(className)
            val instance = clazz.getDeclaredConstructor().newInstance() as NMSHandler
            nmsHandler = instance
            Bukkit.getLogger().info("[Unify] Loaded NMS handler for $version")
            instance
        } catch (ex: Exception) {
            Bukkit.getLogger().warning("[Unify] No NMS handler found for version $version")
            Bukkit.getLogger().warning("[Unify] Error: ${ex.message}")
            null
        }
    }

    /**
     * Detects the NMS version string to use.
     * First tries the legacy package-based detection (pre-1.20.5),
     * then falls back to Minecraft version mapping (1.20.5+).
     */
    private fun detectNMSVersion(): String? {
        // Try legacy detection (pre-1.20.5) - package name contains version
        val packageName = Bukkit.getServer().javaClass.`package`.name
        val packageVersion = packageName.split(".").lastOrNull()
        
        // If the package version looks like v1_XX_RX, use it directly
        if (packageVersion != null && packageVersion.matches(Regex("v\\d+_\\d+_R\\d+"))) {
            return packageVersion
        }
        
        // Modern detection (1.20.5+) - use Bukkit.getMinecraftVersion() via reflection
        val minecraftVersion = try {
            val method = Bukkit::class.java.getMethod("getMinecraftVersion")
            method.invoke(null) as String
        } catch (e: Exception) {
            // Fallback for older Bukkit APIs - parse from getBukkitVersion()
            Bukkit.getBukkitVersion().split("-").firstOrNull()
        }
        
        if (minecraftVersion != null) {
            // Try exact match first
            versionMap[minecraftVersion]?.let { return it }
            
            // Try major.minor match (e.g., "1.21.5" -> "1.21")
            val majorMinor = minecraftVersion.split(".").take(2).joinToString(".")
            versionMap[majorMinor]?.let { return it }
        }
        
        Bukkit.getLogger().warning("[Unify] Unknown Minecraft version: $minecraftVersion")
        return null
    }
}