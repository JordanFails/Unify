package me.jordanfails.unify.npc.trait

import me.jordanfails.unify.npc.NPCRegistry
import me.jordanfails.unify.utils.CC
import org.bukkit.Bukkit
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Runs commands when the NPC is clicked.
 *
 * Replaces the old single `action-command` string. An NPC can hold any number of commands, each
 * bound to left-click, right-click, or both, each optionally gated by a permission and a
 * per-player cooldown, and each run either as the clicking player or as console.
 *
 * Not implemented: Citizens' per-command monetary cost, which needs an economy provider. Unify has
 * no economy dependency, so adding it would mean a soft-depend on Vault.
 */
class CommandTrait : Trait("command") {

    enum class Hand {
        LEFT,
        RIGHT,
        BOTH;

        fun matches(right: Boolean): Boolean = this == BOTH || (this == RIGHT) == right
    }

    data class Entry(
        /** Command to run, without a leading slash. Supports `{player}`, `{npc}` and `{npc_id}`. */
        val command: String,
        val hand: Hand = Hand.RIGHT,
        /** Run as console instead of as the clicking player. */
        val asConsole: Boolean = false,
        val permission: String? = null,
        val cooldownSeconds: Int = 0,
    )

    private val entries = CopyOnWriteArrayList<Entry>()

    /** Per-player, per-entry expiry timestamps. Entries are keyed by index into [entries]. */
    private val cooldowns = ConcurrentHashMap<UUID, MutableMap<Int, Long>>()

    /** All configured commands, in execution order. */
    fun entries(): List<Entry> = entries.toList()

    fun add(entry: Entry) {
        entries.add(entry)
        NPCRegistry.save()
    }

    /** Removes the command at [index]. Returns false when the index is out of range. */
    fun removeAt(index: Int): Boolean {
        if (index !in entries.indices) return false
        entries.removeAt(index)
        // Indices shifted, so any cooldown keyed by the old positions is now pointing at the
        // wrong command. Dropping them all is simpler than remapping and only costs one free use.
        cooldowns.clear()
        NPCRegistry.save()
        return true
    }

    fun clear() {
        entries.clear()
        cooldowns.clear()
        NPCRegistry.save()
    }

    override fun onRightClick(player: Player) = run(player, right = true)

    override fun onLeftClick(player: Player) = run(player, right = false)

    private fun run(player: Player, right: Boolean) {
        entries.forEachIndexed { index, entry ->
            if (!entry.hand.matches(right)) return@forEachIndexed
            if (entry.permission != null && !player.hasPermission(entry.permission)) return@forEachIndexed
            if (!consumeCooldown(player.uniqueId, index, entry.cooldownSeconds)) return@forEachIndexed

            val command = entry.command
                .replace("{player}", player.name, ignoreCase = true)
                .replace("{npc}", npc.id, ignoreCase = true)
                .replace("{npc_id}", npc.id, ignoreCase = true)
                .trim()
                .removePrefix("/")

            if (command.isEmpty()) return@forEachIndexed

            if (entry.asConsole) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
            } else {
                player.performCommand(command)
            }
        }
    }

    /** Returns true when the command may run now, recording the next allowed time if so. */
    private fun consumeCooldown(playerUuid: UUID, index: Int, seconds: Int): Boolean {
        if (seconds <= 0) return true

        val now = System.currentTimeMillis()
        val perPlayer = cooldowns.getOrPut(playerUuid) { ConcurrentHashMap() }
        if (now < (perPlayer[index] ?: 0L)) return false

        perPlayer[index] = now + seconds * 1000L
        return true
    }

    override fun onRemove() {
        cooldowns.clear()
    }

    /** Drops cooldown state for a player who has left, so it cannot accumulate over a restart-free uptime. */
    override fun onViewerUpdate(player: Player, canSee: Boolean) {
        if (!canSee && !player.isOnline) cooldowns.remove(player.uniqueId)
    }

    override fun save(section: ConfigurationSection) {
        entries.forEachIndexed { index, entry ->
            val path = "commands.$index"
            section.set("$path.command", entry.command)
            section.set("$path.hand", entry.hand.name)
            section.set("$path.as-console", entry.asConsole)
            section.set("$path.permission", entry.permission)
            section.set("$path.cooldown", entry.cooldownSeconds)
        }
    }

    override fun load(section: ConfigurationSection) {
        val commands = section.getConfigurationSection("commands") ?: return

        // Numeric keys so order survives the round-trip; YAML map order is not guaranteed.
        commands.getKeys(false)
            .sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }
            .forEach { key ->
                val command = commands.getString("$key.command") ?: return@forEach
                val hand = commands.getString("$key.hand")
                    ?.let { runCatching { Hand.valueOf(it.uppercase()) }.getOrNull() }
                    ?: Hand.RIGHT

                entries.add(
                    Entry(
                        command = command,
                        hand = hand,
                        asConsole = commands.getBoolean("$key.as-console", false),
                        permission = commands.getString("$key.permission"),
                        cooldownSeconds = commands.getInt("$key.cooldown", 0),
                    )
                )
            }
    }

    /** One-line summary of an entry, for `/npc command list`. */
    fun describe(index: Int): String {
        val entry = entries.getOrNull(index) ?: return CC.translate("&cNo command at index $index")
        val flags = mutableListOf(entry.hand.name.lowercase())
        if (entry.asConsole) flags.add("console")
        entry.permission?.let { flags.add("perm=$it") }
        if (entry.cooldownSeconds > 0) flags.add("cd=${entry.cooldownSeconds}s")

        return CC.translate("&7[$index] &f/${entry.command} &8(${flags.joinToString(", ")})")
    }
}
