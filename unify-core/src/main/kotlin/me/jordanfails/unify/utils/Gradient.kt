package me.jordanfails.unify.utils

import java.awt.Color

data class Gradient(
    val from: Color,
    val to: Color,
    val content: String,
    val bold: Boolean,
    val italic: Boolean
)
