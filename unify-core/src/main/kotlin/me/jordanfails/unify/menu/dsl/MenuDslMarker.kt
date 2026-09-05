package me.jordanfails.unify.menu.dsl

/**
 * Prevents accidental scope leakage across nested menu DSL builders.
 */
@DslMarker
annotation class MenuDslMarker
