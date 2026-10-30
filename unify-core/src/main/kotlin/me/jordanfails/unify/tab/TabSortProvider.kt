package me.jordanfails.unify.tab

import org.bukkit.entity.Player

/**
 * Supplies a player's tab-list sort weight. Register from another plugin with
 * [TabHandler.registerSortProvider].
 *
 * Providers are tried highest [weight] first; the first non-null [fetchSortWeight] wins, exactly
 * like [TabProvider]. Returning `null` means "no opinion" and lets a lower-weight provider answer.
 *
 * Higher sort values appear higher in the tab list. Values are clamped to `0..`[MAX_WEIGHT].
 * Sorting is applied through nametag teams, so nametags must stay enabled.
 *
 * Example (in a ranks plugin):
 * ```
 * class RankTabSortProvider : TabSortProvider("Ranks", 100) {
 *     override fun fetchSortWeight(player: Player): Int =
 *         RankHandler.getRank(player).priority
 * }
 *
 * // onEnable:
 * TabHandler.registerSortProvider(RankTabSortProvider())
 * ```
 *
 * After a rank change, call [me.jordanfails.unify.nametag.NametagHandler.reloadPlayer] so the
 * player's tab position updates immediately.
 */
abstract class TabSortProvider(val name: String, val weight: Int) {

    /**
     * Tab position for [player]. Higher values sort higher. Return `null` to defer to a
     * lower-weight provider.
     */
    abstract fun fetchSortWeight(player: Player): Int?

    companion object {
        const val MAX_WEIGHT = 9999
    }
}
