package com.github.azuazu3939

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class ItemCategoryManager {
    
    companion object {
        
        data class ItemInfo(
            val category: String,
            val mmid: String
        )
        
        fun analyzeItem(item: ItemStack): ItemInfo {
            val meta = item.itemMeta ?: // メタデータがない場合はバニラアイテムとして扱う
                return ItemInfo("minecraft", item.type.name.lowercase())
            val pdc = meta.persistentDataContainer
            
            // MythicMobsの特殊アイテムかチェック
            val mythic = NamespacedKey("mythicmobs", "type")
            var category = "minecraft"
            var mmid = item.type.name.lowercase()
            if (pdc.has(mythic)) {
                category = "mythicmobs"
                mmid = pdc.get(mythic, PersistentDataType.STRING) ?: mmid
                return ItemInfo(category, mmid)
            }
            // バニラアイテムの場合
            return ItemInfo(category, item.type.name.lowercase())
        }

        
        fun getDisplayName(item: ItemStack): String {
            val meta = item.itemMeta
            var string = ""
            if (meta?.hasCustomName() == true) {
                val name = meta.customName()
                if (name != null) {
                    string += PlainTextComponentSerializer.plainText().serialize(name)
                }
            }
            
            val info = analyzeItem(item)
            return when (info.category) {
                "minecraft" -> item.type.name.replace("_", " ").lowercase()
                    .split(" ").joinToString(" ") { word ->
                        word.replaceFirstChar { it.uppercase() }
                    }
                "mythicmobs", "event" -> "$string (" + info.mmid + ")"
                else -> item.type.name
            }
        }
    }
}