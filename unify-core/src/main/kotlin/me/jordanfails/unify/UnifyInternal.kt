package me.jordanfails.unify

/**
 * Unify's own plumbing. It can change in any release, so plugins built on Unify shouldn't use it;
 * Unify's modules opt in through the build.
 */
@RequiresOptIn("This is internal to Unify and may change without notice.", RequiresOptIn.Level.ERROR)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
annotation class UnifyInternal
