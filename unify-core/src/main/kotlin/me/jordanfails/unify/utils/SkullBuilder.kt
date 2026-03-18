package me.jordanfails.unify.utils

import com.cryptomorin.xseries.XMaterial
import me.jordanfails.unify.UnifyCore
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

class SkullBuilder {
    companion object {
        @Volatile
        private var loggedTextureFailure = false
    }

    private var base64: String? = null
    private var url: String? = null
    private var uuid: UUID? = null
    private var name: String? = null
    private var type: SkullType? = null

    fun usePlayer(player: Player): SkullBuilder {
        uuid = player.uniqueId
        name = player.name
        type = SkullType.UUID
        return this
    }

    fun usePlayer(player: OfflinePlayer): SkullBuilder {
        uuid = player.uniqueId
        name = player.name
        type = SkullType.UUID
        return this
    }

    fun usePlayer(name: String): SkullBuilder {
        this.name = name
        type = SkullType.NAME
        return this
    }

    fun useBase64(base64: String): SkullBuilder {
        this.base64 = base64
        type = SkullType.BASE64
        return this
    }

    fun useURL(url: String): SkullBuilder {
        this.url = url
        type = SkullType.URL
        return this
    }

    fun useUUID(uuid: UUID): SkullBuilder {
        this.uuid = uuid
        type = SkullType.UUID
        return this
    }

    fun build(): ItemStack {
        val item = ItemStack(XMaterial.PLAYER_HEAD.parseMaterial()!!)

        when (type) {
            SkullType.BASE64 -> base64?.let { applyCustomTexture(item, it) }

            SkullType.URL -> url?.let {
                val encoded = encodeURL(it)
                applyCustomTexture(item, encoded)
            }

            SkullType.UUID -> uuid?.let {
                val meta = item.itemMeta as SkullMeta
                meta.owningPlayer = Bukkit.getOfflinePlayer(it)
                item.itemMeta = meta
            }

            SkullType.NAME -> name?.let {
                val meta = item.itemMeta as SkullMeta
                meta.owningPlayer = Bukkit.getOfflinePlayer(it)
                item.itemMeta = meta
            }

            null -> {
                val meta = item.itemMeta as SkullMeta
                meta.owningPlayer = Bukkit.getOfflinePlayer("Steve")
                item.itemMeta = meta
            }
        }

        return item
    }

    private fun applyCustomTexture(item: ItemStack, base64Texture: String) {
        val meta = item.itemMeta as? SkullMeta ?: return
        try {
            val profileClass = Class.forName("com.mojang.authlib.GameProfile")
            val profile = profileClass
                .getConstructor(UUID::class.java, String::class.java)
                .newInstance(UUID.randomUUID(), "custom")

            val propertyMap = getPropertyMap(profileClass, profile)
                ?: throw IllegalStateException("Could not get property map from GameProfile")

            val propertyClass = Class.forName("com.mojang.authlib.properties.Property")
            val property = propertyClass
                .getConstructor(String::class.java, String::class.java)
                .newInstance("textures", base64Texture)
            propertyMap.javaClass
                .getMethod("put", Any::class.java, Any::class.java)
                .invoke(propertyMap, "textures", property)

            setProfileOnMeta(meta, profileClass, profile)
            item.itemMeta = meta
        } catch (e: Throwable) {
            if (!loggedTextureFailure) {
                loggedTextureFailure = true
                UnifyCore.instance.logger.warning("Failed to apply skull texture: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    private fun getPropertyMap(profileClass: Class<*>, profile: Any): Any? {
        return try {
            profileClass.getMethod("getProperties").invoke(profile)
        } catch (_: Throwable) {
            try {
                profileClass.getMethod("properties").invoke(profile)
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun setProfileOnMeta(meta: SkullMeta, profileClass: Class<*>, profile: Any) {
        val field = findField(meta.javaClass, "profile")
            ?: throw IllegalStateException("No 'profile' field found on ${meta.javaClass.name}")
        field.isAccessible = true

        if (field.type.isAssignableFrom(profileClass)) {
            field.set(meta, profile)
            return
        }

        try {
            val resolvableClass = Class.forName("net.minecraft.world.item.component.ResolvableProfile")
            if (field.type.isAssignableFrom(resolvableClass)) {
                val resolvable = resolvableClass.getConstructor(profileClass).newInstance(profile)
                field.set(meta, resolvable)
                return
            }
        } catch (_: Throwable) {}

        field.set(meta, profile)
    }

    private fun findField(clazz: Class<*>, name: String): java.lang.reflect.Field? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            try {
                return current.getDeclaredField(name)
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun encodeURL(url: String): String {
        val json = "{\"textures\":{\"SKIN\":{\"url\":\"$url\"}}}"
        return Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
    }

    private enum class SkullType {
        BASE64, URL, UUID, NAME
    }
}
