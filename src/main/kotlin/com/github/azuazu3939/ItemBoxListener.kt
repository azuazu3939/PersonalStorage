package com.github.azuazu3939

import net.kyori.adventure.text.Component
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.*
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.*

internal class ItemBoxListener : Listener {

    companion object {
        val willDelete = mutableSetOf<UUID>()
        val syncSlot = mutableListOf<Pair<UUID, Int>>()
        val ctContainer = mutableSetOf<UUID>()
    }

    @EventHandler
    fun onJoin(e: PlayerJoinEvent) {
        val player = e.player
        player.inventory.setItem(8, getBoxItemStack())
        AutoPickUpManager.get(player)
    }

    private fun getBoxItemStack(): ItemStack {
        val item = ItemStack(Material.CHEST, 1)
        val meta = item.itemMeta
        meta.displayName(Component.text("§f§lプレイヤー個人倉庫"))
        meta.persistentDataContainer.set(NamespacedKey.minecraft("no_drop"), PersistentDataType.BOOLEAN, true)
        meta.lore(
            mutableListOf(
                Component.text("§f格納したいアイテムをドロップで即時格納。"),
                Component.text("§f右クリックで格納済みのアイテムを、"),
                Component.text("§f取り出すウィンドウを開きます。")
            )
        )
        item.itemMeta = meta
        return item
    }

    private fun openWindow(e: Player) {
        e.playSound(e, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 2f)
        PersonalStorage.runSyncDelay(runnable = {
            e.playSound(e, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 2f)
        }, 5)
        PersonalStorage.runSyncDelay(runnable = {
            e.closeInventory()
            e.playSound(e, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.5f)
            InventoryManager.openMainCategoryInventory(e)
        }, 10)
    }

    @EventHandler
    fun onClick(e: InventoryClickEvent) {
        if (e.currentItem?.itemMeta?.persistentDataContainer?.has(NamespacedKey.minecraft("no_drop")) == true) {
            e.isCancelled = true
            return
        }

        if (e.inventory.holder is MainCategoryHolder ||
            e.inventory.holder is ItemIdHolder ||
            e.inventory.holder is DetailItemHolder
        ) {
            InventoryClickHandler.handleInventoryClick(e)
            return
        }
    }

    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        if (e.item?.itemMeta?.persistentDataContainer?.has(NamespacedKey.minecraft("no_drop")) == true && e.action.isRightClick) {
            e.isCancelled = true
            openWindow(e.player)
        }
    }

    @EventHandler
    fun onItemPickup(e: EntityPickupItemEvent) {
        if (e.entity !is Player) return
        val player = e.entity as Player

        if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return

        val item = e.item.itemStack.clone()
        if (item.itemMeta?.persistentDataContainer?.has(NamespacedKey.minecraft("no_drop")) == true) {
            return
        }

        if (!AutoPickUpCommand.isAutoPickUp(player)) return
        e.isCancelled = true
        val entity = e.item.copy() as Item
        e.item.remove()

        if (willDelete.contains(player.uniqueId)) {
            PersonalStorage.runSyncDelay(runnable = {
                EntityPickupItemEvent(player, entity, e.remaining).callEvent()
            }, 5)
            return
        }

        if (ctContainer.contains(player.uniqueId)) {
            PersonalStorage.runSyncDelay(runnable = {
                EntityPickupItemEvent(player, entity, e.remaining).callEvent()
            }, ctContainer.size * 6L)
            return
        }
        ctContainer.add(player.uniqueId)
        PersonalStorage.runSyncDelay(runnable = {
            ctContainer.remove(player.uniqueId)
        }, 5L)

        val itemInfo = ItemCategoryManager.analyzeItem(item)
        StorageDataManager.getMMIdSlot(player, itemInfo.category, itemInfo.mmid) { fSlot, bo ->
            if (fSlot == -1) return@getMMIdSlot

            //同時に複数所拾うとデータ保存先が被る修正: Start
            if (bo) {
                if (syncSlot.contains(Pair(player.uniqueId, fSlot))) {

                    PersonalStorage.runSyncDelay(runnable = {
                        EntityPickupItemEvent(player, entity, e.remaining).callEvent()
                    }, 4L * syncSlot.size )
                    return@getMMIdSlot
                }
                syncSlot.add(Pair(player.uniqueId, fSlot))
            }

            StorageDataManager.getNextAvailableSlot(player, itemInfo.category, itemInfo.mmid) { availableSlot ->

                //通常処理
                val inv = player.openInventory.topInventory
                var set = availableSlot - InventoryManager.getCurrentDetailPage(player) * 45
                if (inv.firstEmpty() != -1) {
                    set = minOf(inv.firstEmpty(), set)
                }
                if (inv.holder is DetailItemHolder &&
                    InventoryManager.getCurrentCategory(player) == itemInfo.category &&
                    InventoryManager.getCurrentMmid(player) == itemInfo.mmid
                ) {
                    set += InventoryManager.getCurrentDetailPage(player) * 45

                    //開いているInvが埋まっているときのその埋めてるアイテムをドロップすると45スロット目以降に入る問題の修正
                    if (inv.firstEmpty() != -1) {
                        inv.setItem(set, item)
                    }
                }

                //保存
                StorageDataManager.saveItem(
                    player,
                    itemInfo.category,
                    itemInfo.mmid,
                    fSlot,
                    set + InventoryManager.getCurrentDetailPage(player) * 45,
                    item
                ) {
                    val displayName = ItemCategoryManager.getDisplayName(item)
                    player.sendActionBar(Component.text("§a$displayName x${item.amount} を個人倉庫に格納しました"))
                    player.playSound(player, Sound.ENTITY_ITEM_PICKUP, 0.2f, 1f)

                    //同時に複数所拾うとデータ保存先が被る修正: End
                    syncSlot.remove(Pair(player.uniqueId, fSlot))
                }
            }
        }
    }

    @EventHandler
    fun onItemDrop(e: PlayerDropItemEvent) {
        val player = e.player
        if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return

        e.isCancelled = true

        val item = e.itemDrop.itemStack.clone()
        if (item.itemMeta?.persistentDataContainer?.has(NamespacedKey.minecraft("no_drop")) == true) {
            return
        }
        val entity = e.itemDrop.copy() as Item
        e.itemDrop.itemStack.amount = 0

        if (willDelete.contains(player.uniqueId)) {
            PersonalStorage.runSyncDelay(runnable = {
                PlayerDropItemEvent(player, entity).callEvent()
            }, 5)
            return
        }

        if (ctContainer.contains(player.uniqueId)) {
            PersonalStorage.runSyncDelay(runnable = {
                PlayerDropItemEvent(player, entity).callEvent()
            }, ctContainer.size * 6L)
            return
        }
        ctContainer.add(player.uniqueId)
        PersonalStorage.runSyncDelay(runnable = {
            ctContainer.remove(player.uniqueId)
        }, 5L)

        val itemInfo = ItemCategoryManager.analyzeItem(item)

        StorageDataManager.getMMIdSlot(player, itemInfo.category, itemInfo.mmid) { fSlot, bo ->
            if (fSlot == -1) return@getMMIdSlot

            //同時に複数所拾うとデータ保存先が被る修正: Start
            if (bo) {
                if (syncSlot.contains(Pair(player.uniqueId, fSlot))) {

                    PersonalStorage.runSyncDelay(runnable = {
                        PlayerDropItemEvent(player, entity).callEvent()
                    }, 4L * syncSlot.size)
                    return@getMMIdSlot
                }
                syncSlot.add(Pair(player.uniqueId, fSlot))
            }

            StorageDataManager.getNextAvailableSlot(player, itemInfo.category, itemInfo.mmid) { availableSlot ->

                //通常処理
                val inv = player.openInventory.topInventory
                var set = availableSlot - InventoryManager.getCurrentDetailPage(player) * 45
                if (inv.firstEmpty() != -1) {
                    set = minOf(inv.firstEmpty(), set)
                }

                if (inv.holder is DetailItemHolder &&
                    InventoryManager.getCurrentCategory(player) == itemInfo.category &&
                    InventoryManager.getCurrentMmid(player) == itemInfo.mmid
                ) {
                    set += InventoryManager.getCurrentDetailPage(player) * 45

                    //開いているInvが埋まっているときのその埋めてるアイテムをドロップすると45スロット目以降に入る問題の修正
                    if (inv.firstEmpty() != -1) {
                        inv.setItem(set, item)
                    }
                }

                //保存
                StorageDataManager.saveItem(
                    player,
                    itemInfo.category,
                    itemInfo.mmid,
                    fSlot,
                    set,
                    item
                ) {
                    val displayName = ItemCategoryManager.getDisplayName(item)
                    player.sendActionBar(Component.text("§a$displayName x${item.amount} を個人倉庫に格納しました"))

                    //同時に複数所拾うとデータ保存先が被る修正: End
                    syncSlot.remove(Pair(player.uniqueId, fSlot))
                }
            }
        }
    }


    @EventHandler
    fun onInventoryClose(e: InventoryCloseEvent) {
        val player = e.player as Player

        //Editモードで選択中にInvを閉じると取り出せる問題の修正
        if (InventoryClickHandler.isEditMode(player.uniqueId)) {
            e.view.setCursor(null)
        }
        
        // 詳細アイテム層の場合、インベントリの変更をデータベースに保存
        if (e.inventory.holder is DetailItemHolder) {
            val category = InventoryManager.getCurrentCategory(player)
            val mmid = InventoryManager.getCurrentMmid(player)
            
            if (category != null && mmid != null) {

                val f = InventoryManager.getCurrentFirstSlot(player)
                if (f == -1) return
                val page = InventoryManager.getCurrentDetailPage(player)
                val fPage = InventoryManager.getCurrentItemIdPage(player)

                StorageDataManager.clearDetailItems(player, category, mmid, f, page) {

                    for (slot in 0..44) {
                        StorageDataManager.saveItem(player, category, mmid, f + fPage * 45, slot + page * 45, e.inventory.getItem(slot)) {
                            willDelete.add(player.uniqueId)

                            PersonalStorage.runSyncDelay(runnable = {
                                if (willDelete.contains(player.uniqueId)) {
                                    willDelete.remove(player.uniqueId)
                                    InventoryManager.clearPlayerData(player)
                                }
                            }, 4)
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    fun onInventoryOpen(e: InventoryOpenEvent) {
        val holder = e.inventory.holder
        if (holder is DetailItemHolder ||
            holder is ItemIdHolder ||
            holder is MainCategoryHolder) {

            willDelete.remove(e.player.uniqueId)
        }

        if (!(holder is MainCategoryHolder ||
            holder is ItemIdHolder ||
            holder is DetailItemHolder
        )) {
            InventoryManager.clearPlayerData(e.player as Player)
            willDelete.remove(e.player.uniqueId)
        }
    }

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        InventoryManager.clearPlayerData(e.player)
        willDelete.remove(e.player.uniqueId)
    }

    @EventHandler
    fun onDeath(e: PlayerDeathEvent) {
        val chest = e.player.inventory.getItem(8)
        if (chest != null) {
            if (chest.itemMeta?.persistentDataContainer?.has(NamespacedKey.minecraft("no_drop")) == true)
                chest.amount = 0
        }
    }

    @EventHandler
    fun onRespawn(e: PlayerRespawnEvent) {
        e.player.inventory.setItem(8, getBoxItemStack())
    }
}
