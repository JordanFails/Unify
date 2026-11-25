package me.jordanfails.unify.menu

import me.jordanfails.unify.Beta

/** Chest menu heights. Only these exist, so a [MenuSize] built from them can't be wrong. */
@Beta
enum class Rows(val slots: Int) {
    ONE(9), TWO(18), THREE(27), FOUR(36), FIVE(45), SIX(54);
}

/**
 * Minimum size of a chest [Menu]; content can still grow it. Use `@MenuSize(Rows.THREE)` (checked as
 * you type) or `@MenuSize(slots = 27)` (checked when the plugin loads). Overriding [Menu.getMinSize]
 * works too and wins over this. Subclasses inherit it unless they declare their own.
 */
@Beta
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@java.lang.annotation.Inherited
@MustBeDocumented
annotation class MenuSize(vararg val rows: Rows, val slots: Int = 0)

internal object MenuSizes {
    // ClassValue caches per class without keeping a disabled plugin's classes (and classloader) alive.
    private val sizes = object : ClassValue<Int>() {
        override fun computeValue(type: Class<*>): Int {
            val annotation = type.getAnnotation(MenuSize::class.java) ?: return -1
            return resolve(type, annotation)
        }
    }

    /** The minimum size a menu class declares, or -1. Throws with a fix-it message when the annotation is invalid. */
    fun of(type: Class<*>): Int = sizes.get(type)

    /** Startup check: an annotation next to a getMinSize() override is dead weight that misleads readers. */
    fun warnIfShadowed(type: Class<*>, log: (String) -> Unit) {
        if (!Menu::class.java.isAssignableFrom(type) || !type.isAnnotationPresent(MenuSize::class.java)) return
        if (type.getMethod("getMinSize").declaringClass != Menu::class.java) {
            log("${type.name} has @MenuSize and overrides getMinSize(); the override wins, so the annotation is ignored.")
        }
    }

    fun resolve(type: Class<*>, size: MenuSize): Int {
        val where = "${type.simpleName}: @MenuSize"
        require(size.rows.size <= 1) { "$where takes one Rows value, not ${size.rows.size}" }
        require(size.rows.isEmpty() || size.slots == 0) { "$where sets both rows and slots; use one" }
        size.rows.firstOrNull()?.let { return it.slots }
        require(size.slots != 0) { "$where needs a size, e.g. @MenuSize(Rows.THREE)" }
        requireValid(size.slots) { "$where(slots = ${size.slots})" }
        return size.slots
    }

    /** Shared by the annotation and by [Menu] itself, so both report the same rule and suggestion. */
    fun requireValid(slots: Int, where: () -> String) {
        if (slots in 9..54 && slots % 9 == 0) return
        val lower = (slots / 9 * 9).coerceIn(9, 45)
        throw IllegalArgumentException("${where()} must be a multiple of 9 between 9 and 54 (use $lower or ${lower + 9})")
    }
}
