package me.jordanfails.unify.utils

import com.cryptomorin.xseries.XMaterial
import me.jordanfails.unify.UnifyCore
import me.jordanfails.unify.nms.NMSHandlerFactory
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
        @Volatile private var loggedOwnerFailure = false
        @Volatile private var loggedTextureFailure = false
    }

    private var base64: String? = null
    private var uuid: UUID? = null
    private var name: String? = null
    private var type: SkullType? = null

    fun usePlayer(player: Player) = apply { uuid = player.uniqueId; name = player.name; type = SkullType.UUID }
    fun usePlayer(player: OfflinePlayer) = apply { uuid = player.uniqueId; name = player.name; type = SkullType.UUID }
    fun usePlayer(name: String) = apply { this.name = name; type = SkullType.NAME }
    fun useBase64(base64: String) = apply { this.base64 = base64; type = SkullType.BASE64 }
    fun useUUID(uuid: UUID) = apply { this.uuid = uuid; type = SkullType.UUID }

    fun useURL(url: String) = apply {
        base64 = Base64.getEncoder().encodeToString(
            "{\"textures\":{\"SKIN\":{\"url\":\"$url\"}}}".toByteArray(StandardCharsets.UTF_8)
        )
        type = SkullType.BASE64
    }

    fun build(): ItemStack {
        val item = XMaterial.PLAYER_HEAD.parseItem()?.clone()
            ?: ItemStack(XMaterial.PLAYER_HEAD.parseMaterial()!!)

        when (type) {
            SkullType.BASE64 -> base64?.let { applyTexture(item, it) }
            SkullType.UUID -> applyOwner(item, uuid, name)
            SkullType.NAME -> applyOwner(item, ownerName = name)
            null -> applyOwner(item, ownerName = "Steve")
        }

        return item
    }

    private fun applyOwner(item: ItemStack, ownerUuid: UUID? = null, ownerName: String? = null) {
        if (NMSHandlerFactory.getHandler()?.applySkullOwner(item, ownerUuid, ownerName) == true) {
            return
        }

        val meta = item.itemMeta as? SkullMeta ?: return
        try {
            if (ownerUuid != null && applyOwningPlayer(meta, Bukkit.getOfflinePlayer(ownerUuid))) {
                item.itemMeta = meta
                return
            }

            val resolvedName = ownerName?.takeIf { it.isNotBlank() }
                ?: ownerUuid?.let { Bukkit.getOfflinePlayer(it).name }?.takeIf { it.isNotBlank() }
                ?: return

            if (applyOwningPlayer(meta, Bukkit.getOfflinePlayer(resolvedName)) || applyLegacyOwner(meta, resolvedName)) {
                item.itemMeta = meta
            }
        } catch (e: Throwable) {
            if (!loggedOwnerFailure) {
                loggedOwnerFailure = true
                UnifyCore.instance.logger.warning("Failed to apply skull owner: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    private fun applyTexture(item: ItemStack, base64Texture: String) {
        if (NMSHandlerFactory.getHandler()?.applySkullTexture(item, base64Texture) == true) {
            return
        }

        val meta = item.itemMeta as? SkullMeta ?: return
        try {
            val profileClass = Class.forName("com.mojang.authlib.GameProfile")
            val profile = profileClass
                .getConstructor(UUID::class.java, String::class.java)
                .newInstance(UUID.randomUUID(), "custom")

            val propertyMap = runCatching { profileClass.getMethod("getProperties").invoke(profile) }
                .getOrElse { profileClass.getMethod("properties").invoke(profile) }

            val property = Class.forName("com.mojang.authlib.properties.Property")
                .getConstructor(String::class.java, String::class.java)
                .newInstance("textures", base64Texture)
            propertyMap.javaClass.getMethod("put", Any::class.java, Any::class.java)
                .invoke(propertyMap, "textures", property)

            val field = generateSequence(meta.javaClass as Class<*>?) { it.superclass }
                .firstNotNullOfOrNull { runCatching { it.getDeclaredField("profile") }.getOrNull() }
                ?: throw IllegalStateException("No 'profile' field on ${meta.javaClass.name}")
            field.isAccessible = true

            val value = runCatching {
                val resolvable = Class.forName("net.minecraft.world.item.component.ResolvableProfile")
                if (field.type.isAssignableFrom(resolvable)) {
                    resolvable.getConstructor(profileClass).newInstance(profile)
                } else {
                    profile
                }
            }.getOrDefault(profile)

            field.set(meta, value)
            item.itemMeta = meta
        } catch (e: Throwable) {
            if (!loggedTextureFailure) {
                loggedTextureFailure = true
                UnifyCore.instance.logger.warning("Failed to apply skull texture: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    private fun applyOwningPlayer(meta: SkullMeta, player: OfflinePlayer): Boolean {
        val result = runCatching {
            meta.javaClass.getMethod("setOwningPlayer", OfflinePlayer::class.java).invoke(meta, player)
        }.getOrElse { return false }
        return result as? Boolean ?: true
    }

    private fun applyLegacyOwner(meta: SkullMeta, ownerName: String): Boolean {
        val result = runCatching {
            meta.javaClass.getMethod("setOwner", String::class.java).invoke(meta, ownerName)
        }.getOrElse { return false }
        return result as? Boolean ?: true
    }

    private enum class SkullType { BASE64, UUID, NAME }
}
