package me.jordanfails.unify.plugin

import org.bukkit.plugin.Plugin
import java.io.File
import java.util.jar.JarFile

/** Startup checks that turn common setup mistakes into one clear log line instead of a confusing crash later. */
internal object DependentChecks {
    private const val UNIFY = "unify-core"

    // Unify exposes these libraries at their normal packages, so another plugin bundling them unrelocated
    // can hand Unify's dependents a second, incompatible copy of the same classes.
    private val sharedLibraries = mapOf(
        "co/aikar/commands/BaseCommand.class" to "ACF",
        "com/cryptomorin/xseries/XMaterial.class" to "XSeries",
        "me/jordanfails/honey/DataHandler.class" to "Honey",
    )

    fun run(core: Plugin, plugins: Array<Plugin>) {
        for (plugin in plugins) {
            if (plugin === core) continue
            val entries = jarEntries(plugin) ?: continue
            val dependsOnUnify = UNIFY in plugin.description.depend || UNIFY in plugin.description.softDepend

            // Paper-format plugins declare dependencies in paper-plugin.yml, which this doesn't read.
            if (dependsOnUnify && "kotlin/Unit.class" in entries) {
                core.logger.warning(
                    "${plugin.name} bundles its own Kotlin runtime. Unify provides Kotlin to its dependents; " +
                        "two copies cause LinkageErrors when lambdas cross between plugins. Exclude kotlin/** from its jar."
                )
            }
            for ((entry, library) in sharedLibraries) {
                if (entry !in entries) continue
                val hint = if (dependsOnUnify) "Use the copy Unify provides instead of bundling it" else "Relocate it in that plugin's build"
                core.logger.warning("${plugin.name} bundles $library without relocating it, which can clash with the copy Unify provides. $hint.")
            }
        }
    }

    fun checkRequirements(core: Plugin, plugin: Plugin, declaration: UnifyPlugin) {
        val disabled = declaration.modules.filterNot { it.isEnabled }
        if (disabled.isNotEmpty()) {
            core.logger.severe(
                "${plugin.name} needs Unify modules that are disabled: ${disabled.joinToString { it.key }}. " +
                    "Enable them under modules: in plugins/$UNIFY/config.yml and restart, or ${plugin.name} will fail when it uses them."
            )
        }
        val installed = core.description.version
        if (declaration.minVersion.isNotBlank() && compareVersions(installed, declaration.minVersion) < 0) {
            core.logger.severe("${plugin.name} needs Unify ${declaration.minVersion} or newer, but $installed is installed.")
        }
    }

    /**
     * SemVer precedence: numeric parts compare as numbers ("1.10" > "1.9"), build metadata after '+'
     * is ignored, a pre-release sorts before its release, and pre-releases compare part by part
     * ("rc.2" > "rc.1").
     */
    fun compareVersions(a: String, b: String): Int {
        val (aCore, aPre) = splitVersion(a)
        val (bCore, bPre) = splitVersion(b)
        for (i in 0 until maxOf(aCore.size, bCore.size)) {
            val diff = aCore.getOrElse(i) { 0 }.compareTo(bCore.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        if (aPre == bPre) return 0
        if (aPre == null) return 1
        if (bPre == null) return -1
        for (i in 0 until minOf(aPre.size, bPre.size)) {
            val diff = compareIdentifiers(aPre[i], bPre[i])
            if (diff != 0) return diff
        }
        return aPre.size.compareTo(bPre.size)
    }

    private fun splitVersion(version: String): Pair<List<Int>, List<String>?> {
        val parts = version.substringBefore('+').split('-', limit = 2)
        return parts[0].split('.').map { it.toIntOrNull() ?: 0 } to parts.getOrNull(1)?.split('.')
    }

    // Numeric identifiers compare as numbers and sort before alphanumeric ones.
    private fun compareIdentifiers(a: String, b: String): Int {
        val aNumber = a.toIntOrNull()
        val bNumber = b.toIntOrNull()
        return when {
            aNumber != null && bNumber != null -> aNumber.compareTo(bNumber)
            aNumber != null -> -1
            bNumber != null -> 1
            else -> a.compareTo(b)
        }
    }

    fun jarOf(plugin: Plugin): File? =
        plugin.javaClass.protectionDomain?.codeSource?.location?.toURI()?.let(::File)?.takeIf { it.isFile }

    private fun jarEntries(plugin: Plugin): Set<String>? {
        val jar = jarOf(plugin) ?: return null
        return try {
            JarFile(jar).use { file -> file.entries().asSequence().map { it.name }.toHashSet() }
        } catch (_: Exception) {
            null
        }
    }
}
