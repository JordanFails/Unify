package me.jordanfails.unify.plugin

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import me.jordanfails.unify.menu.MenuSize
import me.jordanfails.unify.menu.MenuSizes
import me.jordanfails.unify.utils.command.CommonCommandContext
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.jar.JarFile

internal class ScannedClasses(
    val configs: List<Class<*>>,
    val contexts: List<Class<*>>,
    val commands: List<Class<*>>,
    val listeners: List<Class<*>>,
    val repeating: List<Method>,
)

internal object PluginScanner {
    // Annotations always appear in the annotated class's own bytes, so searching for these skips
    // loading classes that can't be relevant.
    private val markers = listOf(ConfigFile::class.java, AutoListener::class.java, Repeating::class.java, CommandAlias::class.java, MenuSize::class.java)
        .map { "L${it.name.replace('.', '/')};" }

    fun scan(plugin: Plugin, autoCommands: Boolean): ScannedClasses {
        // Context resolvers can extend CommonCommandContext through an intermediate base, which their
        // bytes don't mention, so finding them means checking every class in the plugin's package.
        val classes = candidateClasses(plugin, loadEverything = autoCommands)
        // Report bad @MenuSize(slots = ...) on startup instead of when a player first opens the menu.
        for (menu in classes) {
            val size = menu.getAnnotation(MenuSize::class.java) ?: continue
            MenuSizes.warnIfShadowed(menu) { plugin.logger.warning("[Unify] $it") }
            try {
                MenuSizes.resolve(menu, size)
            } catch (error: IllegalArgumentException) {
                plugin.logger.severe("[Unify] ${error.message}")
            }
        }
        return ScannedClasses(
            configs = classes.filter { it.isAnnotationPresent(ConfigFile::class.java) },
            contexts = if (autoCommands) classes.filter(::isContext) else emptyList(),
            commands = if (autoCommands) classes.filter(::isRootCommand) else emptyList(),
            listeners = classes.filter { it.isAnnotationPresent(AutoListener::class.java) && isListener(plugin, it) },
            repeating = classes.flatMap { cls ->
                plugin.guarded("read methods of ${cls.name}") {
                    cls.declaredMethods.filter { it.isAnnotationPresent(Repeating::class.java) && it.parameterCount == 0 }
                }.orEmpty()
            },
        )
    }

    /** A Kotlin object's INSTANCE, or a new instance from a no-arg constructor; null if neither exists. */
    fun instanceOf(cls: Class<*>): Any? {
        val singleton = cls.declaredFields.firstOrNull {
            it.name == "INSTANCE" && it.type == cls && Modifier.isStatic(it.modifiers)
        }
        if (singleton != null) return singleton.apply { isAccessible = true }.get(null)

        val constructor = cls.declaredConstructors.firstOrNull { it.parameterCount == 0 } ?: return null
        return constructor.apply { isAccessible = true }.newInstance()
    }

    private fun candidateClasses(plugin: Plugin, loadEverything: Boolean): List<Class<*>> {
        val root = plugin.javaClass.name.substringBeforeLast('.', "").replace('.', '/').let { if (it.isEmpty()) "" else "$it/" }
        val jar = DependentChecks.jarOf(plugin) ?: return emptyList()

        return JarFile(jar).use { file ->
            file.entries().asSequence()
                .filter { it.name.startsWith(root) && it.name.endsWith(".class") }
                .mapNotNull { entry ->
                    val bytes = String(file.getInputStream(entry).use { it.readBytes() }, Charsets.ISO_8859_1)
                    val marked = markers.any { it in bytes }
                    if (!marked && !loadEverything) return@mapNotNull null
                    load(plugin, entry.name.removeSuffix(".class").replace('/', '.'), reportFailure = marked)
                }
                .toList()
        }
    }

    private fun load(plugin: Plugin, name: String, reportFailure: Boolean): Class<*>? {
        val loader = plugin.javaClass.classLoader
        return try {
            Class.forName(name, false, loader).takeIf { it.classLoader === loader }
        } catch (error: LinkageError) {
            // Usually a class referencing an optional dependency that isn't installed.
            if (reportFailure) plugin.logger.warning("[Unify] Skipped $name: ${error.message}")
            null
        } catch (_: ClassNotFoundException) {
            null
        }
    }

    // Nested BaseCommand classes are subcommands; ACF registers those through their parent.
    private fun isRootCommand(cls: Class<*>) =
        cls.isAnnotationPresent(CommandAlias::class.java) &&
            BaseCommand::class.java.isAssignableFrom(cls) &&
            cls.declaringClass == null &&
            !Modifier.isAbstract(cls.modifiers)

    private fun isContext(cls: Class<*>) =
        CommonCommandContext::class.java.isAssignableFrom(cls) && !Modifier.isAbstract(cls.modifiers)

    private fun isListener(plugin: Plugin, cls: Class<*>): Boolean {
        if (Listener::class.java.isAssignableFrom(cls)) return true
        plugin.logger.warning("[Unify] ${cls.name} has @AutoListener but doesn't implement Listener")
        return false
    }
}
