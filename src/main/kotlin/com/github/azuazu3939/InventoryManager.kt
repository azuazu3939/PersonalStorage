package com.github.azuazu3939

import io.lumine.mythic.bukkit.MythicBukkit
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.*

class InventoryManager {
    
    companion object {
        private val playerInventoryLayer = mutableMapOf<UUID, InventoryLayer>()
        private val playerCurrentCategory = mutableMapOf<UUID, String>()
        private val playerCurrentMmid = mutableMapOf<UUID, String>()
        private val playerCurrentFirstSlot = mutableMapOf<UUID, Int>()
        private val playerCurrentDetailPage = mutableMapOf<UUID, Int>()
        private val playerCurrentItemIdPage = mutableMapOf<UUID, Int>()
        
        fun openMainCategoryInventory(player: Player, page: Int = 0) {
            player.closeInventory()
            playerInventoryLayer[player.uniqueId] = InventoryLayer.MAIN_CATEGORY
            val holder = MainCategoryHolder(page)

            player.playSound(player, Sound.ENTITY_CHICKEN_EGG, 0.5f, 1.8f)
            player.playSound(player, Sound.ENTITY_CHICKEN_EGG, 0.5f, 1.414f)
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.414f)
            PersonalStorage.runSyncDelay(runnable = {
                player.openInventory(holder.inventory)
            }, 2)
        }
        
        fun openItemIdInventory(player: Player, category: String, page: Int = 0) {
            player.closeInventory()
            playerInventoryLayer[player.uniqueId] = InventoryLayer.ITEM_ID
            playerCurrentCategory[player.uniqueId] = category
            playerCurrentItemIdPage[player.uniqueId] = page
            val holder = ItemIdHolder(page)
            val inv = holder.inventory

            setItemIdItem(player, category, page, inv) {
                player.playSound(player, Sound.ENTITY_CHICKEN_EGG, 0.5f, 1.8f)
                player.playSound(player, Sound.ENTITY_CHICKEN_EGG, 0.5f, 1.414f)
                player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.414f)
                PersonalStorage.runSyncDelay(runnable = {
                    player.openInventory(it)
                }, 2)
            }
        }
        
        fun openDetailItemInventory(player: Player, category: String, mmid: String, firstSlot: Int, page: Int = 0) {
            player.closeInventory()
            playerInventoryLayer[player.uniqueId] = InventoryLayer.DETAIL_ITEM
            playerCurrentCategory[player.uniqueId] = category
            playerCurrentMmid[player.uniqueId] = mmid
            playerCurrentFirstSlot[player.uniqueId] = firstSlot
            playerCurrentDetailPage[player.uniqueId] = page
            val holder = DetailItemHolder(mmid, page)
            val inv = holder.inventory

            setDetailItem(player, category, mmid, page, inv) {
                player.playSound(player, Sound.ENTITY_CHICKEN_EGG, 0.5f, 1.8f)
                player.playSound(player, Sound.ENTITY_CHICKEN_EGG, 0.5f, 1.414f)
                player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.414f)
                PersonalStorage.runSyncDelay(runnable = {
                    player.openInventory(it)
                }, 2)
            }
        }
        
        fun getCurrentLayer(player: Player): InventoryLayer? {
            return playerInventoryLayer[player.uniqueId]
        }
        
        fun getCurrentCategory(player: Player): String? {
            return playerCurrentCategory[player.uniqueId]
        }
        
        fun getCurrentMmid(player: Player): String? {
            return playerCurrentMmid[player.uniqueId]
        }
        
        fun getCurrentDetailPage(player: Player): Int {
            return playerCurrentDetailPage[player.uniqueId] ?: 0
        }

        fun getCurrentItemIdPage(player: Player): Int {
            return playerCurrentItemIdPage[player.uniqueId] ?: 0
        }

        fun getCurrentFirstSlot(player: Player): Int {
            return playerCurrentFirstSlot[player.uniqueId] ?: -1
        }
        
        fun clearPlayerData(player: Player) {
            playerInventoryLayer.remove(player.uniqueId)
            playerCurrentCategory.remove(player.uniqueId)
            playerCurrentMmid.remove(player.uniqueId)
            playerCurrentFirstSlot.remove(player.uniqueId)
            playerCurrentDetailPage.remove(player.uniqueId)
            playerCurrentItemIdPage.remove(player.uniqueId)
            InventoryClickHandler.removeEditMode(player.uniqueId)
        }

        private fun setItemIdItem(player: Player, category: String, page: Int, inv: Inventory, callback: (Inventory) -> Unit) {
            // アイテムIDを非同期で取得してインベントリを更新
            StorageDataManager.getItemIds(player, category) { itemIds ->
                if (hasNextPage(itemIds, page)) {
                    inv.setItem(53, getNextPageItem())
                }

                val startIndex = page * 45
                val endIndex = minOf(startIndex + 45, itemIds.size)

                var slot = 0
                for (i in startIndex until endIndex) {
                    if (slot > 44) break
                    val mmid = itemIds[i]

                    // カテゴリーがバニラの場合はMaterialから、そうでなければBARRIERを使用
                    val material = when (category) {
                        "minecraft" -> {
                            Material.getMaterial(mmid.uppercase()) ?: Material.BARRIER
                        }
                        "mythicmobs" -> {
                            val mythic = MythicBukkit.inst().itemManager.getItemStack(mmid)
                            mythic?.type ?: Material.BARRIER
                        }
                        else -> {
                            Material.BARRIER
                        }
                    }

                    val itemStack = ItemStack(material, 1)
                    val meta = itemStack.itemMeta!!

                    // 表示名を適切に設定
                    val displayName = if (category == "minecraft") {
                        mmid.replace("_", " ").split(" ").joinToString(" ") { word ->
                            word.replaceFirstChar { it.uppercase() }
                        }
                    } else {
                        mmid
                    }

                    meta.displayName(Component.text("§e§l$displayName"))
                    meta.lore(listOf(
                        Component.text("§flクリック§7で詳細を表示"),
                        Component.text("§8ID: $mmid")
                    ))
                    meta.persistentDataContainer.set(NamespacedKey.minecraft("mmid"), PersistentDataType.STRING, mmid)
                    itemStack.itemMeta = meta

                    inv.setItem(slot, itemStack)
                    slot++
                }
                callback.invoke(inv)
            }
        }

        private fun setDetailItem(player: Player, category: String, mmid: String, page: Int, inv: Inventory, callback: (Inventory) -> Unit) {
            // アイテムを非同期で取得してインベントリを更新
            StorageDataManager.hasNextPage(player, category, mmid) { it ->
                if ((page + 1) * 45 <= it) {
                    inv.setItem(53, getNextPageItem())
                }

                StorageDataManager.getDetailItems(player, category, mmid, page) { items ->
                    // アイテムを配置（ページング対応）
                    val itemList = items.toList().sortedBy { it.first }
                    val startIndex = page * 45
                    val endIndex = minOf(startIndex + 45, itemList.size)

                    var slot = 0
                    for (i in startIndex until endIndex) {
                        if (slot > 44) break
                        val (_, itemStack) = itemList[i]
                        inv.setItem(slot, itemStack)
                        slot++
                    }
                    callback.invoke(inv)
                }
            }
        }

        private fun getNextPageItem(): ItemStack {
            val item = ItemStack(Material.ARROW, 1)
            val meta = item.itemMeta!!
            meta.displayName(Component.text("§a§l次のページ"))
            meta.persistentDataContainer.set(NamespacedKey.minecraft("page_next"), PersistentDataType.BOOLEAN, true)
            item.itemMeta = meta
            return item
        }

        private fun hasNextPage(itemIds: List<String>, page: Int): Boolean {
            return (page + 1) * 45 < itemIds.size
        }
    }
    
    enum class InventoryLayer {
        MAIN_CATEGORY,
        ITEM_ID,
        DETAIL_ITEM
    }
}

class MainCategoryHolder(private val page: Int) : InventoryHolder {
    
    override fun getInventory(): Inventory {
        val inv = Bukkit.createInventory(this, 54, Component.text("§9§l個人倉庫 - メインカテゴリ (${page + 1}ページ)"))
        
        val categories = getCategoriesList()
        val startIndex = page * 45
        val endIndex = minOf(startIndex + 45, categories.size)
        
        var slot = 0
        for (i in startIndex until endIndex) {
            if (slot > 45) break
            val category = categories[i]
            val categoryItem = createCategoryItem(category)
            inv.setItem(slot, categoryItem)
            slot++
        }
        
        return inv
    }
    
    private fun getCategoriesList(): List<CategoryInfo> {
        return listOf(
            CategoryInfo("minecraft", Material.GRASS_BLOCK, "§a§lバニラアイテム類", "§7Minecraftの標準アイテムを管理します"),
            CategoryInfo("mythicmobs", Material.NETHER_STAR, "§d§l特殊アイテム類", "§7外部プラグインの特殊アイテムを管理します"),
            CategoryInfo("event", Material.DRAGON_EGG, "§6§lイベントアイテム類", "§7イベント限定アイテムを管理します")
        )
    }
    
    private fun createCategoryItem(category: CategoryInfo): ItemStack {
        val item = ItemStack(category.material, 1)
        val meta = item.itemMeta!!
        meta.displayName(Component.text(category.displayName))
        meta.lore(listOf(Component.text(category.description)))
        meta.persistentDataContainer.set(NamespacedKey.minecraft("category"), PersistentDataType.STRING, category.id)
        item.itemMeta = meta
        return item
    }
    
    data class CategoryInfo(
        val id: String,
        val material: Material,
        val displayName: String,
        val description: String
    )
}

class ItemIdHolder(private val page: Int) : InventoryHolder {
    
    override fun getInventory(): Inventory {
        val inv = Bukkit.createInventory(this, 54, Component.text("§9§l個人倉庫 - アイテムID (${page + 1}ページ)"))
        
        // 45-53スロットを全てstained_paneで埋める
        for (i in 45..53) {
            inv.setItem(i, getPanel())
        }
        
        // ページング用アイテムを設置
        if (page > 0) {
            inv.setItem(45, getBackPageItem())
        }
        
        // 戻るボタンを設置
        val backItem = ItemStack(Material.BARRIER, 1)
        val backMeta = backItem.itemMeta!!
        backMeta.displayName(Component.text("§c§l戻る"))
        backMeta.persistentDataContainer.set(NamespacedKey.minecraft("back"), PersistentDataType.BOOLEAN, true)
        backItem.itemMeta = backMeta
        inv.setItem(49, backItem)

        // 編集モードボタンを設置
        val editItem = ItemStack(Material.REDSTONE, 1)
        val editMeta = editItem.itemMeta!!
        editMeta.displayName(Component.text("§d§l編集モード"))
        editMeta.lore(listOf(Component.text("§7クリックして編集モードを切り替え")))
        editMeta.persistentDataContainer.set(NamespacedKey.minecraft("edit_mode"), PersistentDataType.BOOLEAN, true)
        editItem.itemMeta = editMeta
        inv.setItem(50, editItem)
        
        // 他のスロットを空にする
        for (i in 0..44) {
            inv.setItem(i, ItemStack(Material.AIR))
        }
        return inv
    }
    
    private fun getPanel(): ItemStack {
        val item = ItemStack(Material.WHITE_STAINED_GLASS_PANE, 1)
        val meta = item.itemMeta!!
        meta.displayName(Component.text(""))
        meta.persistentDataContainer.set(NamespacedKey.minecraft("panel"), PersistentDataType.BOOLEAN, true)
        item.itemMeta = meta
        return item
    }
    
    private fun getBackPageItem(): ItemStack {
        val item = ItemStack(Material.ARROW, 1)
        val meta = item.itemMeta!!
        meta.displayName(Component.text("§a§l前のページ"))
        meta.persistentDataContainer.set(NamespacedKey.minecraft("page_back"), PersistentDataType.BOOLEAN, true)
        item.itemMeta = meta
        return item
    }
}

class DetailItemHolder(
    private val mmid: String,
    private val page: Int
) : InventoryHolder {
    
    override fun getInventory(): Inventory {
        val inv = Bukkit.createInventory(this, 54, Component.text("§9§l個人倉庫 - $mmid (${page + 1}ページ)"))
        
        // 45-53スロットを全てstained_paneで埋める
        for (i in 45..53) {
            inv.setItem(i, getPanel())
        }
        
        // ページング用アイテムを設置
        if (page > 0) {
            inv.setItem(45, getBackPageItem())
        }
        
        // 戻るボタンを設置
        val backItem = ItemStack(Material.BARRIER, 1)
        val backMeta = backItem.itemMeta!!
        backMeta.displayName(Component.text("§c§l戻る"))
        backMeta.persistentDataContainer.set(NamespacedKey.minecraft("back"), PersistentDataType.BOOLEAN, true)
        backItem.itemMeta = backMeta
        inv.setItem(49, backItem)
        
        // 他のスロットを空にする
        for (i in 0..44) {
            inv.setItem(i, ItemStack(Material.AIR))
        }
        return inv
    }
    
    private fun getPanel(): ItemStack {
        val item = ItemStack(Material.WHITE_STAINED_GLASS_PANE, 1)
        val meta = item.itemMeta!!
        meta.displayName(Component.text(""))
        meta.persistentDataContainer.set(NamespacedKey.minecraft("panel"), PersistentDataType.BOOLEAN, true)
        item.itemMeta = meta
        return item
    }
    
    private fun getBackPageItem(): ItemStack {
        val item = ItemStack(Material.ARROW, 1)
        val meta = item.itemMeta!!
        meta.displayName(Component.text("§a§l前のページ"))
        meta.persistentDataContainer.set(NamespacedKey.minecraft("page_back"), PersistentDataType.BOOLEAN, true)
        item.itemMeta = meta
        return item
    }
}