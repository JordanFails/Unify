package me.jordanfails.unify.utils

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.bukkit.WorldGuardPlugin
import com.sk89q.worldguard.protection.flags.StateFlag
import com.sk89q.worldguard.protection.flags.Flags
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player

object WorldGuardUtils {

    /**
     * Checks if PVP is allowed in this location by WorldGuard region flags.
     */
    @JvmStatic
    fun isPvpAllowed(location: Location): Boolean {
        return queryState(location, Flags.PVP, null)
    }

    /**
     * Checks if PVP is allowed for this player at this location by WorldGuard region flags.
     */
    @JvmStatic
    fun isPvpAllowed(player: Player, location: Location = player.location): Boolean {
        return queryState(location, Flags.PVP, player)
    }

    /**
     * Checks a WorldGuard state flag (for example: "pvp", "build", "mob-spawning") at a location.
     */
    @JvmStatic
    fun isStateFlagAllowed(location: Location, flagName: String): Boolean {
        return queryState(location, requireStateFlag(flagName), null)
    }

    /**
     * Checks a WorldGuard state flag for a player at a location.
     */
    @JvmStatic
    fun isStateFlagAllowed(player: Player, location: Location = player.location, flagName: String): Boolean {
        return queryState(location, requireStateFlag(flagName), player)
    }

    private fun queryState(location: Location, flag: StateFlag, player: Player?): Boolean {
        val worldGuard = requireWorldGuard()
        val query = WorldGuard.getInstance().platform.regionContainer.createQuery()
        val adaptedLocation = BukkitAdapter.adapt(location)
        val localPlayer = player?.let { worldGuard.wrapPlayer(it) }
        return query.testState(adaptedLocation, localPlayer, flag)
    }

    private fun requireStateFlag(flagName: String): StateFlag {
        val normalizedName = flagName.lowercase()
        val flag = WorldGuard.getInstance().flagRegistry.get(normalizedName)
            ?: throw IllegalArgumentException("WorldGuard flag '$normalizedName' does not exist.")

        if (flag !is StateFlag) {
            throw IllegalArgumentException("WorldGuard flag '$normalizedName' is not a state flag.")
        }

        return flag
    }

    private fun requireWorldGuard(): WorldGuardPlugin {
        val plugin = Bukkit.getPluginManager().getPlugin("WorldGuard")
            ?: throw IllegalStateException("WorldGuard is required when using WorldGuardUtils, but it is not installed.")

        if (plugin !is WorldGuardPlugin) {
            throw IllegalStateException("Installed plugin 'WorldGuard' is not a valid WorldGuard instance.")
        }

        if (!plugin.isEnabled) {
            throw IllegalStateException("WorldGuard is installed but not enabled.")
        }

        return plugin
    }
}
