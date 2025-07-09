@file:Suppress("DEPRECATION")

package com.github.azuazu3939

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

class InventoryClickHandler {

    companion object {
        private val editMode = mutableSetOf<UUID>()
        private val oldSlotCache = mutableMapOf<UUID, Int>()

        fun removeEditMode(uuid: UUID) {
            editMode.remove(uuid)
        }

        fun handleInventoryClick(event: InventoryClickEvent) {
            val player = event.whoClicked as Player
            val currentLayer = InventoryManager.getCurrentLayer(player) ?: return

            if (currentLayer == InventoryManager.InventoryLayer.MAIN_CATEGORY ||
                currentLayer == InventoryManager.InventoryLayer.ITEM_ID
            ) {
                event.isCancelled = true
            }

            val clickedItem = event.currentItem
            val meta = clickedItem?.itemMeta
            val pdc = meta?.persistentDataContainer

            when (currentLayer) {
                InventoryManager.InventoryLayer.MAIN_CATEGORY -> {
                    if (pdc != null) {
                        handleMainCategoryClick(event, player, pdc)
                    }
                }

                InventoryManager.InventoryLayer.ITEM_ID -> {
                    if (pdc != null) {
                        handleItemIdClick(event, player, pdc)
                    }
                }

                InventoryManager.InventoryLayer.DETAIL_ITEM -> {
                    if (pdc != null) {
                        handleDetailItemClick(event, player, pdc)
                    }
                }
            }
        }


        private fun handleMainCategoryClick(event: InventoryClickEvent, player: Player, pdc: PersistentDataContainer) {
            event.isCancelled = true

            if (pdc.has(NamespacedKey.minecraft("category"))) {
                val category = pdc.get(NamespacedKey.minecraft("category"), PersistentDataType.STRING) ?: return
                InventoryManager.openItemIdInventory(player, category)
            }
        }

        private fun handleItemIdClick(event: InventoryClickEvent, player: Player, pdc: PersistentDataContainer) {
            // アイテムID層では全てのクリックをキャンセル（アイテムの出し入れを完全に禁止）
            event.isCancelled = true
            val category = InventoryManager.getCurrentCategory(player) ?: return

            if (editMode.contains(player.uniqueId)) {
                handleEdit(event, player, category)
                return
            }

            if (pdc.has(NamespacedKey.minecraft("panel"))) {
                return
            }

            if (pdc.has(NamespacedKey.minecraft("page_back"))) {
                val currentPage = InventoryManager.getCurrentItemIdPage(player)
                if (currentPage > 0) {
                    InventoryManager.openItemIdInventory(player, category, currentPage - 1)
                }
                return
            }

            if (pdc.has(NamespacedKey.minecraft("page_next"))) {
                val currentPage = InventoryManager.getCurrentItemIdPage(player)
                InventoryManager.openItemIdInventory(player, category, currentPage + 1)
                return
            }

            if (pdc.has(NamespacedKey.minecraft("back"))) {
                InventoryManager.openMainCategoryInventory(player)
                return
            }

            if (pdc.has(NamespacedKey.minecraft("edit_mode"))) {
                event.isCancelled = true
                val currentEditMode = editMode.contains(player.uniqueId)
                player.sendMessage(if (!currentEditMode) "§a編集モードを有効にしました" else "§c編集モードを無効にしました")
                if (currentEditMode) {
                    editMode.remove(player.uniqueId)
                } else {
                    editMode.add(player.uniqueId)
                }
                player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 0.25f, 1f)
                return
            }

            if (pdc.has(NamespacedKey.minecraft("mmid"))) {
                val mmid = pdc.get(NamespacedKey.minecraft("mmid"), PersistentDataType.STRING) ?: return
                InventoryManager.openDetailItemInventory(player, category, mmid, event.slot)
            }

            // その他のクリック（空のスロット等）は何もしない（ただしキャンセル）
        }

        private fun handleDetailItemClick(
            event: InventoryClickEvent,
            player: Player,
            pdc: PersistentDataContainer
        ) {
            val category = InventoryManager.getCurrentCategory(player) ?: return
            val mmid = InventoryManager.getCurrentMmid(player) ?: return

            if (pdc.has(NamespacedKey.minecraft("panel"))) {
                event.isCancelled = true
                return
            }

            if (pdc.has(NamespacedKey.minecraft("page_back"))) {
                event.isCancelled = true
                val currentPage = InventoryManager.getCurrentDetailPage(player)
                if (currentPage > 0) {
                    InventoryManager.openDetailItemInventory(
                        player,
                        category,
                        mmid,
                        InventoryManager.getCurrentFirstSlot(player),
                        currentPage - 1
                    )
                }
                return
            }

            if (pdc.has(NamespacedKey.minecraft("page_next"))) {
                event.isCancelled = true
                val currentPage = InventoryManager.getCurrentDetailPage(player)
                InventoryManager.openDetailItemInventory(
                    player,
                    category,
                    mmid,
                    InventoryManager.getCurrentFirstSlot(player),
                    currentPage + 1
                )
                return
            }

            if (pdc.has(NamespacedKey.minecraft("back"))) {
                event.isCancelled = true
                val currentCategory = InventoryManager.getCurrentCategory(player) ?: return
                InventoryManager.openItemIdInventory(player, currentCategory)
                return
            }

            handle(event, player, category, mmid)
        }

        private fun handleEdit(event: InventoryClickEvent, player: Player, category: String) {
            val clickedSlot = event.rawSlot
            if (clickedSlot < 0 || clickedSlot >= 54) return

            val clickedItem = event.currentItem
            val cursor = event.cursor
            if (clickedItem != null) {
                val pdc = clickedItem.itemMeta.persistentDataContainer
                if (pdc.has(NamespacedKey.minecraft("panel")) ||
                    pdc.has(NamespacedKey.minecraft("page_back")) ||
                    pdc.has(NamespacedKey.minecraft("page_next")) ||
                    pdc.has(NamespacedKey.minecraft("back")) ||
                    pdc.has(NamespacedKey.minecraft("category"))
                    ) {
                    oldSlotCache.remove(player.uniqueId)
                    return
                }

                if (pdc.has(NamespacedKey.minecraft("edit_mode"))) {
                    player.sendMessage(Component.text("§c編集モードを無効にしました"))
                    editMode.remove(player.uniqueId)
                    player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 0.25f, 0.5f)
                    return
                }

                if (cursor.type.isAir) {
                    oldSlotCache[player.uniqueId] = clickedSlot
                    event.setCursor(clickedItem)

                    clickedItem.type = Material.BARRIER
                    event.currentItem = clickedItem
                    player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 0.25f, 1f)

                } else {
                    if (!oldSlotCache.contains(player.uniqueId)) return
                    val o = oldSlotCache[player.uniqueId] ?: return

                    oldSlotCache.remove(player.uniqueId)
                    StorageDataManager.swapSlots(
                        player,
                        category,
                        ItemCategoryManager.analyzeItem(cursor).mmid,
                        ItemCategoryManager.analyzeItem(clickedItem).mmid,
                        o,
                        clickedSlot
                    ) {
                        event.currentItem = cursor
                        event.inventory.setItem(o, clickedItem)
                        event.setCursor(null)
                        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 0.25f, 2f)
                    }
                }
            }
        }

        private fun handle(event: InventoryClickEvent, player: Player, category: String, mmid: String) {
            val clickedSlot = event.rawSlot
            if (clickedSlot < 0 || clickedSlot >= 54) return

            val clickedItem = event.currentItem
            if (clickedItem == null || clickedItem.type.isAir) {
                event.isCancelled = true
                return
            }

            event.isCancelled = false
            val page = InventoryManager.getCurrentDetailPage(player)
            if (event.isShiftClick) {
                event.isCancelled = true
                val playerInventory = player.inventory
                val firstEmpty = playerInventory.firstEmpty()
                if (firstEmpty != -1) {
                    playerInventory.setItem(firstEmpty, clickedItem.clone())
                    StorageDataManager.removeItem(
                        player,
                        category,
                        mmid,
                        page,
                        firstEmpty,
                        clickedSlot
                    )
                    event.inventory.setItem(clickedSlot, null)
                } else {
                    player.sendMessage("§cインベントリがいっぱいです")
                }
            } else if (event.cursor.type.isAir) {
                event.isCancelled = true
                player.setItemOnCursor(clickedItem.clone())
                StorageDataManager.removeItem(
                    player,
                    category,
                    mmid,
                    page,
                    InventoryManager.getCurrentFirstSlot(player),
                    clickedSlot
                )
                event.inventory.setItem(clickedSlot, null)
            }
        }
    }
}