package me.jordanfails.unify.screen

/**
 * What the client does after a button is clicked (or the form is submitted).
 */
enum class ScreenAfterAction {
    /** Close this screen and return to the previous non-dialog screen. */
    CLOSE,
    /** Leave this screen open. Requires [Screen.pause] to be false. */
    KEEP_OPEN,
    /** Replace this screen with a "Waiting for response" spinner until the next screen opens. */
    WAIT,
}
