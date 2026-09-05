package me.jordanfails.unify.npc

/**
 * A resolved NPC skin: the Mojang texture blob plus its signature.
 *
 * Skins reach us three ways (player name, texture URL, raw base64) but only ever apply one way —
 * as a `textures` property on the NPC's GameProfile. Resolution to [value]/[signature] happens off
 * the main thread in [SkinResolver]; everything downstream of that deals in this type alone, so no
 * NMS module has to know a URL from a username.
 *
 * [source] and [sourceType] are kept purely so the skin can round-trip through `npcs.yml` and be
 * re-resolved later (a player changing their skin should follow through to NPCs using it).
 */
data class NPCSkin(
    val value: String,
    val signature: String?,
    val sourceType: SourceType,
    val source: String,
) {
    enum class SourceType {
        /** A player name, resolved through the Mojang profile API. */
        NAME,

        /** A `http(s)://textures.minecraft.net/texture/...` URL, wrapped into a texture blob locally. */
        URL,

        /** An already-encoded texture blob, used verbatim. */
        BASE64,
    }

    companion object {
        /**
         * Parses the user-facing skin syntax accepted by `/npc skin`:
         * `<name>`, `url:<textureUrl>`, `base64:<value>[;<signature>]`, or a bare `http(s)://` URL.
         *
         * Returns the source pair to hand to [SkinResolver] — not a finished [NPCSkin], since
         * NAME and URL sources both need work that must not happen on the main thread.
         */
        fun parseSource(input: String): Pair<SourceType, String>? {
            val raw = input.trim()
            if (raw.isEmpty()) return null

            return when {
                raw.startsWith("base64:", ignoreCase = true) ->
                    raw.substringAfter(':', "").trim().takeIf { it.isNotEmpty() }?.let { SourceType.BASE64 to it }

                raw.startsWith("url:", ignoreCase = true) ->
                    raw.substringAfter(':', "").trim().takeIf { it.isNotEmpty() }?.let { SourceType.URL to it }

                raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true) ->
                    SourceType.URL to raw

                else -> SourceType.NAME to raw
            }
        }
    }
}
