package com.github.azuazu3939

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.sql.SQLException

class StorageDataManager {
    
    companion object {
        
        fun saveItem(player: Player, category: String, mmid: String, fSlot: Int, sSlot: Int, item: ItemStack?, callback: (() -> Unit)? = null) {
            if (item != null && item.itemMeta.persistentDataContainer.has(NamespacedKey.minecraft("mmid"))) {
                item.amount = 0
            }
            PersonalStorage.runAsync(runnable = {
                try {
                    if (item == null) {
                        DBCon.runPrepareStatement(
                            "INSERT INTO az_item_data (uuid, type, mmid, f_slot, s_slot, item) VALUES (?, ?, ?, ?, ?, ?) " +
                                    "ON DUPLICATE KEY UPDATE item = VALUES(item)"
                        ) { ps ->
                            ps.setString(1, player.uniqueId.toString())
                            ps.setString(2, category)
                            ps.setString(3, mmid)
                            ps.setInt(4, fSlot)
                            ps.setInt(5, sSlot)
                            ps.setBytes(6, null)
                            ps.executeUpdate()

                            PersonalStorage.runSync(runnable = {
                                callback?.invoke()
                            })
                        }
                       // println("[PersonalStorage] アイテムNull保存: ${player.name} - $mmid (f_slot: $fSlot, s_slot: $sSlot)")

                    } else {
                        val itemBytes = itemStackToByteArray(item) ?: return@runAsync
                        DBCon.runPrepareStatement(
                            "INSERT INTO az_item_data (uuid, type, mmid, f_slot, s_slot, item) VALUES (?, ?, ?, ?, ?, ?) " +
                                    "ON DUPLICATE KEY UPDATE item = VALUES(item)"
                        ) { ps ->
                            ps.setString(1, player.uniqueId.toString())
                            ps.setString(2, category)
                            ps.setString(3, mmid)
                            ps.setInt(4, fSlot)
                            ps.setInt(5, sSlot)
                            ps.setBytes(6, itemBytes)
                            ps.executeUpdate()

                            PersonalStorage.runSync(runnable = {
                                callback?.invoke()
                            })
                        }
                        println("[PersonalStorage] アイテム保存: ${player.name} - $mmid (f_slot: $fSlot, s_slot: $sSlot)")
                    }
                } catch (e: SQLException) {
                    println("[PersonalStorage] アイテム保存エラー: ${e.message}")
                }
            })
        }
        
        fun getItemIds(player: Player, category: String, callback: (List<String>) -> Unit) {
            PersonalStorage.runAsync(runnable = {
                val itemIds = mutableListOf<String>()
                try {
                    DBCon.runPrepareStatement(
                        "SELECT DISTINCT mmid FROM az_item_data WHERE uuid = ? AND type = ?"
                    ) { ps ->
                        ps.setString(1, player.uniqueId.toString())
                        ps.setString(2, category)
                        val rs = ps.executeQuery()
                        while (rs.next()) {
                            itemIds.add(rs.getString("mmid"))
                        }
                    }
                } catch (e: SQLException) {
                    println("[PersonalStorage] アイテムID取得エラー: ${e.message}")
                }
                PersonalStorage.runSync(runnable = {
                    callback.invoke(itemIds)
                })
            })
        }

        fun hasNextPage(player: Player, category: String, mmid: String, callback: (Int) -> Unit) {
            PersonalStorage.runAsync(runnable = {
                try {
                    val lists = mutableListOf<Int>()
                    DBCon.runPrepareStatement(
                        "SELECT s_slot FROM az_item_data WHERE uuid = ? AND type = ? AND mmid = ?"
                    ) { ps ->
                        ps.setString(1, player.uniqueId.toString())
                        ps.setString(2, category)
                        ps.setString(3, mmid)
                        val rs = ps.executeQuery()
                        while (rs.next()) {
                            lists.add(rs.getInt("s_slot"))
                        }
                        PersonalStorage.runSync(runnable = {
                            callback.invoke(lists.maxOf { it })
                        })
                    }
                } catch (e: SQLException) {
                    println("[PersonalStorage] 次のページ取得エラー: ${e.message}")
                }
            })
        }
        
        fun getDetailItems(player: Player, category: String, mmid: String, page: Int, callback: (Map<Int, ItemStack>) -> Unit) {
            PersonalStorage.runAsync(runnable = {
                val items = mutableMapOf<Int, ItemStack>()
                try {
                    for (i in 0..< (page + 1) * 45) {
                        items[i] = ItemStack(Material.AIR)
                    }
                    DBCon.runPrepareStatement(
                        "SELECT s_slot, item FROM az_item_data WHERE uuid = ? AND type = ? AND mmid = ? AND s_slot >= ? AND s_slot <= ?"
                    ) { ps ->
                        ps.setString(1, player.uniqueId.toString())
                        ps.setString(2, category)
                        ps.setString(3, mmid)
                        ps.setInt(4, page * 45)
                        ps.setInt(5, (page + 1) * 45 - 1)
                        val rs = ps.executeQuery()
                        while (rs.next()) {
                            val sSlot = rs.getInt("s_slot")
                            val itemBytes = rs.getBytes("item")

                            if (itemBytes != null) {
                                val itemStack = byteArrayToItemStack(itemBytes)
                                if (itemStack != null) {
                                    items[sSlot] = itemStack
                                }
                            }
                        }
                    }
                } catch (e: SQLException) {
                    println("[PersonalStorage] 詳細アイテム取得エラー: ${e.message}")
                }
                PersonalStorage.runSync(runnable = {
                    callback.invoke(items)
                })
            })
        }
        
        fun removeItem(player: Player, category: String, mmid: String, page: Int, fSlot: Int, sSlot: Int, callback: (() -> Unit)? = null) {
            PersonalStorage.runAsync(runnable = {
                try {
                    DBCon.runPrepareStatement(
                        "DELETE FROM az_item_data WHERE uuid = ? AND type = ? AND mmid = ? AND f_slot = ? AND s_slot = ?"
                    ) { ps ->
                        ps.setString(1, player.uniqueId.toString())
                        ps.setString(2, category)
                        ps.setString(3, mmid)
                        ps.setInt(4, fSlot)
                        ps.setInt(5, sSlot + page * 45)
                        ps.executeUpdate()

                        println("[PersonalStorage] アイテム削除: ${player.name} - $mmid (s_slot: $sSlot)")
                        PersonalStorage.runSync(runnable = {
                            callback?.invoke()
                        })
                    }
                } catch (e: SQLException) {
                    println("[PersonalStorage] アイテム削除エラー: ${e.message}")
                }
            })
        }

        fun swapSlots(player: Player, category: String, mmid1: String, mmid2: String, slot1: Int, slot2: Int, callback: (() -> Unit)? = null) {
            PersonalStorage.runAsync(runnable = {
                try {
                    // 一時的なスロット番号を使用してアイテムを交換
                    val tempSlot = -1

                    // slot1のアイテムをtempSlotに移動
                    DBCon.runPrepareStatement(
                        "UPDATE az_item_data SET f_slot = ? WHERE uuid = ? AND type = ? AND mmid = ?"
                    ) { ps ->
                        ps.setInt(1, tempSlot)
                        ps.setString(2, player.uniqueId.toString())
                        ps.setString(3, category)
                        ps.setString(4, mmid1)
                        ps.executeUpdate()
                    }

                    // slot2のアイテムをslot1に移動
                    DBCon.runPrepareStatement(
                        "UPDATE az_item_data SET f_slot = ? WHERE uuid = ? AND type = ? AND mmid = ?"
                    ) { ps ->
                        ps.setInt(1, slot1)
                        ps.setString(2, player.uniqueId.toString())
                        ps.setString(3, category)
                        ps.setString(4, mmid2)
                        ps.executeUpdate()
                    }

                    // tempSlotのアイテムをslot2に移動
                    DBCon.runPrepareStatement(
                        "UPDATE az_item_data SET f_slot = ? WHERE uuid = ? AND type = ? AND mmid = ?"
                    ) { ps ->
                        ps.setInt(1, slot2)
                        ps.setString(2, player.uniqueId.toString())
                        ps.setString(3, category)
                        ps.setString(4, mmid1)
                        ps.executeUpdate()
                    }

                    PersonalStorage.runSync(runnable = {
                        callback?.invoke()
                    })

                    println("[PersonalStorage] スロット交換: ${player.name} - ($mmid1 $slot1 <-> $slot2 $mmid2)")
                } catch (e: SQLException) {
                    println("[PersonalStorage] スロット交換エラー: ${e.message}")
                }
            })
        }

        fun getMMIdSlot(player: Player, category: String, mmid: String, callback: (Int, Boolean) -> Unit) {
            PersonalStorage.runAsync(runnable = {
                var availableSlot = -1
                var bo = false
                try {
                    DBCon.runPrepareStatement(
                        "SELECT COUNT(DISTINCT mmid) FROM az_item_data WHERE uuid = ? AND type = ?"
                    ) { ps ->
                        ps.setString(1, player.uniqueId.toString())
                        ps.setString(2, category)
                        val rs = ps.executeQuery()

                        if (rs.next()) {
                            availableSlot = rs.getInt(1)
                            bo = true
                        }

                        DBCon.runPrepareStatement(
                            "SELECT f_slot FROM az_item_data WHERE uuid = ? AND type = ? AND mmid = ?"
                        ) { pss ->
                            pss.setString(1, player.uniqueId.toString())
                            pss.setString(2, category)
                            pss.setString(3, mmid)
                            val rss = pss.executeQuery()

                            if (rss.next()) {
                                availableSlot = rss.getInt("f_slot")
                                bo = false
                            }
                        }
                    }
                } catch (e: SQLException) {
                    println("[PersonalStorage] 利用済みのMMIDスロット取得エラー: ${e.message}")
                }
                PersonalStorage.runSync(runnable = {
                    callback.invoke(availableSlot, bo)
                })
            })
        }
        
        fun getNextAvailableSlot(player: Player, category: String, mmid: String, callback: (Int) -> Unit) {
            PersonalStorage.runAsync(runnable = {
                val usedSlots = mutableListOf<Int>()
                var slot = 0
                try {
                    DBCon.runPrepareStatement(
                        "SELECT s_slot, item FROM az_item_data WHERE uuid = ? AND type = ? AND mmid = ?"
                    ) { ps ->
                        ps.setString(1, player.uniqueId.toString())
                        ps.setString(2, category)
                        ps.setString(3, mmid)
                        val rs = ps.executeQuery()
                        while (rs.next()) {
                            val data = rs.getBytes("item")
                            slot = rs.getInt("s_slot")
                            if (data != null) {
                                usedSlots.add(rs.getInt("s_slot"))
                            } else {
                                return@runPrepareStatement
                            }
                        }
                        if (usedSlots.isNotEmpty()) {
                            slot = usedSlots.maxOf { it + 1 }
                        }
                    }
                } catch (e: SQLException) {
                    println("[PersonalStorage] 利用可能スロット取得エラー: ${e.message}")
                }
                PersonalStorage.runSync(runnable = {
                    callback.invoke(slot)
                })
            })
        }
        
        fun clearDetailItems(player: Player, category: String, mmid: String, fSlot: Int, page: Int, callback: (() -> Unit)? = null) {
            PersonalStorage.runAsync(runnable = {
                try {
                    DBCon.runPrepareStatement(
                        "DELETE FROM az_item_data WHERE uuid = ? AND type = ? AND mmid = ? AND f_slot = ? AND s_slot >= ? AND s_slot <= ?"
                    ) { ps ->
                        ps.setString(1, player.uniqueId.toString())
                        ps.setString(2, category)
                        ps.setString(3, mmid)
                        ps.setInt(4, fSlot)
                        ps.setInt(5, page * 45)
                        ps.setInt(6, (page + 1) * 45 - 1)
                        ps.executeUpdate()
                    }
                    println("[PersonalStorage] 詳細アイテムクリア: ${player.name} - $mmid - ${page * 45} ~ ${(page + 1) * 45 - 1}")
                } catch (e: SQLException) {
                    println("[PersonalStorage] 詳細アイテムクリアエラー: ${e.message}")
                }
                PersonalStorage.runSync(runnable = {
                    callback?.invoke()
                })
            })
        }
        
        private fun itemStackToByteArray(item: ItemStack): ByteArray? {
            if (item.type.isAir) return null
            return item.serializeAsBytes()
        }
        
        private fun byteArrayToItemStack(bytes: ByteArray): ItemStack? {
            return try {
                ItemStack.deserializeBytes(bytes)
            } catch (e: Exception) {
                println("[PersonalStorage] ItemStack変換エラー: ${e.message}")
                null
            }
        }
    }
}