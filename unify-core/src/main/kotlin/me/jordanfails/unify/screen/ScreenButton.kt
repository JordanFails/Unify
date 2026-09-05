package me.jordanfails.unify.screen

/**
 * A clickable footer / grid button on a native screen.
 *
 * [action] of `null` still closes the screen (vanilla default).
 */
data class ScreenButton(
    val label: String,
    val tooltip: String? = null,
    val width: Int = DEFAULT_WIDTH,
    val action: ScreenAction? = null,
) {
    companion object {
        const val DEFAULT_WIDTH = 150
    }
}

/**
 * What happens when a [ScreenButton] is clicked.
 */
sealed class ScreenAction {
    class Click(val handler: (ScreenClick) -> Unit) : ScreenAction()
    class Url(val url: String) : ScreenAction()
    class Command(val command: String) : ScreenAction()
    class Suggest(val command: String) : ScreenAction()
    class Copy(val text: String) : ScreenAction()
    class Open(val screen: Screen) : ScreenAction()
}
