package me.jordanfails.unify.plugin

import me.jordanfails.unify.Beta
import me.jordanfails.unify.module.UnifyModule

/**
 * Put this on your JavaPlugin class to opt into auto-registration. Unify scans classes in the
 * main class's package (and below) and registers everything under your plugin, not Unify:
 * - ACF commands with @CommandAlias that are Kotlin objects or have a no-arg constructor, plus any
 *   CommonCommandContext resolvers they need (registered first)
 * - [AutoListener] classes
 * - [Repeating] functions
 * - [ConfigFile] objects
 *
 * Set [autoCommands] to false when the plugin manages its own command lifecycle (e.g. per-feature toggles).
 * [modules] and [minVersion] are checked at startup and logged as errors when the server doesn't meet them.
 */
@Beta
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class UnifyPlugin(
    val autoCommands: Boolean = true,
    val modules: Array<UnifyModule> = [],
    val minVersion: String = "",
)

@Beta
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AutoListener

/** Runs the function every [ticks]. Works on functions in objects, no-arg classes, and top-level functions. */
@Beta
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Repeating(val ticks: Long, val async: Boolean = false)

/**
 * Binds an object's `var` properties to a YAML file in the plugin's data folder; `maxHomes` maps
 * to the `max-homes` key. Loaded before the plugin's onEnable, so the object's initializer must
 * not touch anything set up there.
 */
@Beta
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigFile(val name: String)
