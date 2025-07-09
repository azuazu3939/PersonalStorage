package com.github.azuazu3939

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.*

class AutoPickUpCommand : CommandExecutor {

    override fun onCommand(p0: CommandSender, p1: Command, p2: String, p3: Array<out String>): Boolean {
        if (p3.isNotEmpty()) {
            val s1 = p3[0]

            if (s1.equals("true", ignoreCase = true) && p0 is Player) {
                enableAutoPickUp(p0)
                return true
            }
            if (s1.equals("false", ignoreCase = true) && p0 is Player) {
                disableAutoPickUp(p0)
                return true
            }

            val player = Bukkit.getPlayer(s1)
            if (player != null) {

                if (p3.size < 2) return false
                val s2 = p3[2]

                if (s2.equals("true", ignoreCase = true)) {
                    enableAutoPickUp(player)
                    return true
                }
                if (s2.equals("false", ignoreCase = true)) {
                    disableAutoPickUp(player)
                    return true
                }
            }
        }
        if (p0 is Player) {
            if (autoPickUp.contains(p0.uniqueId)) {
                disableAutoPickUp(p0)
            } else {
                autoPickUp.add(p0.uniqueId)
                enableAutoPickUp(p0)
            }
        }
        return false
    }

    companion object {
        private val autoPickUp = mutableSetOf<UUID>()


        fun isAutoPickUp(player: Player): Boolean {
            return autoPickUp.contains(player.uniqueId)
        }

        fun enableAutoPickUp(player: Player) {
            autoPickUp.add(player.uniqueId)
            player.sendMessage(Component.text("自動回収を有効にしました", NamedTextColor.GREEN))
            AutoPickUpManager.save(player, true)
        }

        fun disableAutoPickUp(player: Player) {
            autoPickUp.remove(player.uniqueId)
            player.sendMessage(Component.text("自動回収を無効にしました", NamedTextColor.RED))
            AutoPickUpManager.save(player, false)
        }
    }
}
