package me.jordanfails.unify.npc

/**
 * Body poses an NPC can hold.
 *
 * Not every pose exists on every server version — [NMSHandler.supportsNpcPose] reports what a
 * given module can do, and unsupported poses fall back to [STANDING] rather than failing the
 * spawn. (1.8 in particular has no swim or crawl pose at all.)
 */
enum class NPCPose {
    STANDING,
    SNEAKING,
    SITTING,
    SLEEPING,
    SWIMMING,
}

/** Equipment slots addressable on an NPC body, independent of Bukkit's version-split slot enums. */
enum class NPCEquipmentSlot {
    HAND,
    OFF_HAND,
    HELMET,
    CHESTPLATE,
    LEGGINGS,
    BOOTS,
}
