package io.github.nocomment1105.modmailbot

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.DriverManager

private const val URL = "jdbc:sqlite:./data/database.db"
object DatabaseManager {
	private val logger = KotlinLogging.logger("Database Manager")
	private val config = HikariConfig()
	private var dataSource: HikariDataSource

	init {
		config.jdbcUrl = URL
		config.connectionTestQuery = "SELECT 1"
		config.addDataSourceProperty("cachePrepStmts", true)
		config.addDataSourceProperty("prepStmtCacheSize", 250)
		config.addDataSourceProperty("prepStmtCacheSqlLimit", 2048)

	    dataSource = HikariDataSource(config)
		Database.connect(dataSource)

		logger.info("Connected to database")
	}

	fun startDatabase() {
		DriverManager.getConnection(URL)

		transaction {
			// SchemaUtils.createMissingTablesAndColumns()
		}
	}
}
