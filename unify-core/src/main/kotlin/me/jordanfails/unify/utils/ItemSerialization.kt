package me.jordanfails.unify.utils

import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.*


object ItemSerialization {
    @JvmStatic
    fun serialize(item: ItemStack?): String? {
        try {
            ByteArrayOutputStream().use { io ->
                BukkitObjectOutputStream(io).use { os ->
                    os.writeObject(item)
                    os.flush()

                    val serializedObject: ByteArray = io.toByteArray()
                    return Base64.getEncoder().encodeToString(serializedObject)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
    }

    @JvmStatic
    fun deserialize(serialized: String?): ItemStack? {
        try {
            val decodedBytes: ByteArray = Base64.getDecoder().decode(serialized)

            ByteArrayInputStream(decodedBytes).use { inputStream ->
                BukkitObjectInputStream(inputStream).use { objectInputStream ->
                    return objectInputStream.readObject() as ItemStack
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
        }

        return null
    }
}