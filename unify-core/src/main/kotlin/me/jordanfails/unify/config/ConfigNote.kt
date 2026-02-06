package me.jordanfails.unify.config

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigNote(val value: String)
