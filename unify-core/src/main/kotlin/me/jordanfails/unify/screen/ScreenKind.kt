package me.jordanfails.unify.screen

/**
 * Footer layout of a native custom screen.
 *
 * Maps 1:1 onto Minecraft dialog types (1.21.6+ / Paper 1.21.7+).
 */
enum class ScreenKind {
    /** Single acknowledgment button. */
    NOTICE,
    /** Yes / no pair in the footer. */
    CONFIRMATION,
    /** Scrollable grid of action buttons. */
    MULTI,
}
