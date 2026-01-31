package me.jordanfails.unify.utils

import com.cryptomorin.xseries.XMaterial
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import java.lang.reflect.Field
import java.util.*

class SkullBuilder {

    private var base64: String? = null
    private var url: String? = null
    private var uuid: UUID? = null
    private var name: String? = null
    private var type: SkullType? = null

    /** Use a Bukkit Player */
    fun usePlayer(player: Player): SkullBuilder {
        this.uuid = player.uniqueId
        this.name = player.name
        this.type = SkullType.UUID
        return this
    }

    /** Use an OfflinePlayer */
    fun usePlayer(player: OfflinePlayer): SkullBuilder {
        this.uuid = player.uniqueId
        this.name = player.name
        this.type = SkullType.UUID
        return this
    }

    /** Use a player name */
    fun usePlayer(name: String): SkullBuilder {
        this.name = name
        this.type = SkullType.NAME
        return this
    }

    /** Use a Mojang skin texture base64 string */
    fun useBase64(base64: String): SkullBuilder {
        this.base64 = base64
        this.type = SkullType.BASE64
        return this
    }

    /** Use a Mojang skin URL */
    fun useURL(url: String): SkullBuilder {
        this.url = url
        this.type = SkullType.URL
        return this
    }

    /** Use a UUID directly */
    fun useUUID(uuid: UUID): SkullBuilder {
        this.uuid = uuid
        this.type = SkullType.UUID
        return this
    }

    /** Build the final skull ItemStack */
    fun build(): ItemStack {
        // In 1.8, player skull is SKULL_ITEM with durability 3
        val item = ItemBuilder(XMaterial.PLAYER_HEAD.parseMaterial()!!, 1)
            .durability(3).build()
        val meta = item.itemMeta as SkullMeta

        when (this.type) {
            SkullType.BASE64 -> base64?.let { injectProfile(meta, makeProfile(it)) }
            SkullType.URL -> url?.let { injectProfile(meta, makeProfile(encodeURL(it))) }
            SkullType.UUID -> uuid?.let { meta.owner = Bukkit.getOfflinePlayer(it).name }
            SkullType.NAME -> name?.let { meta.owner = it }
            null -> meta.owner = "Steve"
        }

        item.itemMeta = meta
        return item
    }

    /** Turns a texture URL into a base64 string */
    private fun encodeURL(url: String): String {
        val json = "{\"textures\":{\"SKIN\":{\"url\":\"$url\"}}}"
        return Base64.getEncoder().encodeToString(json.toByteArray())
    }

    /** Make a GameProfile with the given base64 skin texture */
    private fun makeProfile(base64: String): GameProfile {
        val profile = GameProfile(UUID.randomUUID(), null)
        profile.properties.put("textures", Property("textures", base64))
        return profile
    }

    /** Inject the GameProfile into SkullMeta (1.8 reflection hack) */
    private fun injectProfile(meta: SkullMeta, profile: GameProfile) {
        try {
            val profileField: Field = meta.javaClass.getDeclaredField("profile")
            profileField.isAccessible = true
            profileField.set(meta, profile)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    private enum class SkullType {
        BASE64, URL, UUID, NAME
    }
}