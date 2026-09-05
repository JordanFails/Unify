package me.jordanfails.unify.npc.trait

import me.jordanfails.unify.nms.NMSHandlerFactory
import me.jordanfails.unify.npc.NPCEquipmentSlot
import me.jordanfails.unify.npc.NPCRegistry
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.ItemStack
import java.util.EnumMap

/**
 * What the NPC is wearing and holding.
 *
 * Uses Unify's own [NPCEquipmentSlot] rather than Bukkit's `EquipmentSlot`, which does not exist
 * on 1.8 and gained slots over time. The NMS layer maps it to whatever the running version has,
 * and drops slots that version lacks — an off-hand item on 1.8 is stored but never rendered, so it
 * reappears if the server is later upgraded.
 */
class EquipmentTrait : Trait("equipment") {

    private val items = EnumMap<NPCEquipmentSlot, ItemStack>(NPCEquipmentSlot::class.java)

    /** The item in [slot], or null if empty. */
    fun get(slot: NPCEquipmentSlot): ItemStack? = items[slot]

    /** Every non-empty slot. */
    fun all(): Map<NPCEquipmentSlot, ItemStack> = EnumMap(items)

    /** Sets [slot], or clears it when [item] is null or air. */
    fun set(slot: NPCEquipmentSlot, item: ItemStack?) {
        if (item == null || item.type == org.bukkit.Material.AIR) {
            items.remove(slot)
        } else {
            items[slot] = item.clone()
        }
        apply(slot)
        NPCRegistry.save()
    }

    fun clear() {
        val cleared = items.keys.toList()
        items.clear()
        cleared.forEach { apply(it) }
        NPCRegistry.save()
    }

    override fun onSpawn() {
        // Push every slot, including empty ones we are tracking, so a rebuilt body never keeps
        // equipment from a previous configuration.
        NPCEquipmentSlot.values().forEach { apply(it) }
    }

    private fun apply(slot: NPCEquipmentSlot) {
        if (!isAttached || !npc.isSpawned()) return
        val entity = npc.entityUuid ?: return
        NMSHandlerFactory.getHandler()?.setNpcEquipment(entity, slot, items[slot])
    }

    override fun save(section: ConfigurationSection) {
        // ItemStack is ConfigurationSerializable, so Bukkit round-trips enchants, names and lore
        // for us. Writing raw material names here would silently drop all of that.
        items.forEach { (slot, item) -> section.set(slot.name.lowercase(), item) }
    }

    override fun load(section: ConfigurationSection) {
        NPCEquipmentSlot.values().forEach { slot ->
            val item = section.getItemStack(slot.name.lowercase()) ?: return@forEach
            items[slot] = item
        }
    }
}
