package com.github.azuazu3939

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

class PersonalStorage : JavaPlugin() {

    override fun onEnable() {
        saveDefaultConfig()
        DBCon.init()

        val pm = server.pluginManager
        pm.registerEvents(ItemBoxListener(), this)
        getCommand("autopickup")?.setExecutor(AutoPickUpCommand())

        Bukkit.getOnlinePlayers().forEach {
            AutoPickUpManager.get(it)
        }
    }

    override fun onDisable() {
        DBCon.close()
    }

    companion object {
        fun runAsync(runnable: Runnable) {
            Bukkit.getScheduler().runTaskAsynchronously(getPlugin(PersonalStorage::class.java), runnable)
        }

        fun runSync(runnable: Runnable) {
            Bukkit.getScheduler().runTask(getPlugin(PersonalStorage::class.java), runnable)
        }

        fun runSyncDelay(runnable: Runnable, delay: Long) {
            Bukkit.getScheduler().runTaskLater(getPlugin(PersonalStorage::class.java), runnable, delay)
        }
    }
}
