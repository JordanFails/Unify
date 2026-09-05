package me.jordanfails.unify.screen

/**
 * A control that collects a value from the player.
 *
 * [key] is how you read the value back from [ScreenValues]. It must be letters,
 * digits, or `_` (Minecraft template identifier rules).
 */
sealed class ScreenInput {
    abstract val key: String
    abstract val label: String

    data class Text(
        override val key: String,
        override val label: String,
        val initial: String = "",
        val maxLength: Int = 32,
        val width: Int = DEFAULT_WIDTH,
        val labelVisible: Boolean = true,
        val maxLines: Int? = null,
        val height: Int? = null,
    ) : ScreenInput() {
        val multiline: Boolean get() = maxLines != null || height != null
    }

    data class Bool(
        override val key: String,
        override val label: String,
        val initial: Boolean = false,
        val onTrue: String = "true",
        val onFalse: String = "false",
    ) : ScreenInput()

    data class Number(
        override val key: String,
        override val label: String,
        val start: Float,
        val end: Float,
        val initial: Float? = null,
        val step: Float? = null,
        val width: Int = DEFAULT_WIDTH,
        val labelFormat: String = "options.generic_value",
    ) : ScreenInput()

    data class Option(
        override val key: String,
        override val label: String,
        val choices: List<Choice>,
        val width: Int = DEFAULT_WIDTH,
        val labelVisible: Boolean = true,
    ) : ScreenInput()

    data class Choice(
        val id: String,
        val display: String? = null,
        val initial: Boolean = false,
    )

    companion object {
        const val DEFAULT_WIDTH = 200

        private val KEY = Regex("^[A-Za-z0-9_]+$")

        fun requireValidKey(key: String) {
            require(KEY.matches(key)) {
                "screen input key '$key' must be letters, digits, or underscore"
            }
        }
    }
}
