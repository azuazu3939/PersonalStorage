package com.github.azuazu3939

import org.bukkit.entity.Player
import java.sql.SQLException

class AutoPickUpManager {

    companion object {

        fun save(player: Player, isBoolean: Boolean) {
            PersonalStorage.runAsync(runnable = {
                try {
                    DBCon.runPrepareStatement(
                        "INSERT INTO az_auto_pickup (`name`, `isBoolean`) VALUES (?, ?) " +
                                "ON DUPLICATE KEY UPDATE `isBoolean` = VALUES(`isBoolean`)"
                    ) { ps ->
                        ps.setString(1, player.uniqueId.toString())
                        ps.setBoolean(2, isBoolean)
                        ps.executeUpdate()
                    }
                } catch (e: SQLException) {
                    println("[PersonalStorage] 自動取得保存エラー: ${e.message}")
                }
            })
        }

        fun get(player: Player) {
            PersonalStorage.runAsync(runnable = {
                try {
                    DBCon.runPrepareStatement(
                        "SELECT * FROM az_auto_pickup WHERE `name` = ?"
                    ) { ps ->
                        ps.setString(1, player.uniqueId.toString())
                        val rs = ps.executeQuery()
                        if (rs.next()) {
                            val isBoolean = rs.getBoolean(1)
                            if (isBoolean) {
                                AutoPickUpCommand.enableAutoPickUp(player)
                            } else {
                                AutoPickUpCommand.disableAutoPickUp(player)
                            }
                        }
                    }
                } catch (e: SQLException) {
                    println("[PersonalStorage] 自動取得取得エラー: ${e.message}")
                }
            })
        }
    }
}