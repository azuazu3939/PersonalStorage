package com.github.azuazu3939

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.plugin.java.JavaPlugin
import org.mariadb.jdbc.Driver
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException

class DBCon {

    companion object {

        private lateinit var dataSource: HikariDataSource

        private const val ITEM_DATA: String = "az_item_data"
        private const val AUTO_PICKUP: String = "az_auto_pickup"

        fun init() {
            Driver()
            val config = HikariConfig()
            val plugin: JavaPlugin = JavaPlugin.getPlugin(PersonalStorage::class.java)
            val host: String? = plugin.config.getString("Database.host", "host")
            val port: Int = plugin.config.getInt("Database.port", 3306)
            val database: String? = plugin.config.getString("Database.database", "database")
            val username: String? = plugin.config.getString("Database.username", "user")
            val password: String? = plugin.config.getString("Database.password", "password")
            val schem: String? = plugin.config.getString("Database.scheme", "jdbc:mariadb")

            config.jdbcUrl = "$schem://$host:$port/$database?useUnicode=true&characterEncoding=UTF-8&useSSL=false"
            config.connectionTimeout = 30000
            config.maximumPoolSize = 20
            config.username = username
            config.password = password
            config.addDataSourceProperty("useUnicode", "true")
            config.addDataSourceProperty("characterEncoding", "UTF-8")

            dataSource = HikariDataSource(config)
            createTable()
        }

        fun close() {
            dataSource.close()
        }

        @Throws(SQLException::class)
        fun runPrepareStatement(sql: String, action: SQLThrowableConsumer<PreparedStatement>) {
            use { connection ->
                connection.prepareStatement(sql).use { preparedStatement ->
                    action.accept(preparedStatement)
                }
            }
        }

        fun interface SQLThrowableConsumer<T> {
            @Throws(SQLException::class)
            fun accept(t: T)
        }

        private fun getDataSource(): HikariDataSource {
            return dataSource
        }

        @Throws(SQLException::class)
        private fun use(action: SQLThrowableConsumer<Connection>) {
            connection.use { con ->
                action.accept(con)
            }
        }

        @get:Throws(SQLException::class)
        private val connection: Connection
            get() = getDataSource().connection

        @Throws(SQLException::class)
        private fun createTable() {
            runPrepareStatement("CREATE TABLE IF NOT EXISTS `" + ITEM_DATA +
                    "` (\n" +
                    "`uuid` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL, \n" +
                    "`type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL, \n" +
                    "`mmid` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL, \n" +
                    "`f_slot` int, \n" +
                    "`s_slot` int, \n" +
                    "`item` mediumblob, \n" +
                    "PRIMARY KEY (`uuid`, `type`, `f_slot`, `s_slot`)\n" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", PreparedStatement::execute)
            runPrepareStatement("CREATE TABLE IF NOT EXISTS `" + AUTO_PICKUP +
                    "` (\n" +
                    "`name` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL, \n" +
                    "`isBoolean` tinyint(2) DEFAULT false,  \n" +
                    "PRIMARY KEY (`name`)\n" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", PreparedStatement::execute)
        }
    }
}