package me.jordanfails.unify

/**
 * A feature that's new and may change or break between Unify releases. Kotlin callers see a warning
 * until they opt in with `@OptIn(Beta::class)`; Java callers only see this annotation in docs.
 */
@RequiresOptIn("This Unify feature is in beta and may change or break between releases.", RequiresOptIn.Level.WARNING)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.TYPEALIAS)
@MustBeDocumented
annotation class Beta
