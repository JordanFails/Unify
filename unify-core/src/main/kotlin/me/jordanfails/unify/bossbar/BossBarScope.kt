package me.jordanfails.unify.bossbar

/**
 * Who a named [UnifyBossBar] is shown to when the handler refreshes.
 *
 * - [GLOBAL] — every online (non-NPC) player
 * - [WORLD] — players in [UnifyBossBar.world]
 * - [VIEWERS] — only players added via [UnifyBossBar.addPlayer]
 */
enum class BossBarScope {
    GLOBAL,
    WORLD,
    VIEWERS
}
