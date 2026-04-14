package com.provingground.database

import com.provingground.database.tables.ChallengeDemoUploadIntentsTable
import com.provingground.database.tables.ChallengeSubmissionsTable
import com.provingground.database.tables.ChallengeToClubsTable
import com.provingground.database.tables.ChallengeUploadIntentsTable
import com.provingground.database.tables.ChallengesTable
import com.provingground.database.tables.ClubToUsersTable
import com.provingground.database.tables.ClubsTable
import com.provingground.database.tables.ConsentsTable
import com.provingground.database.tables.ParentToChildrenTable
import com.provingground.database.tables.TeamsTable
import com.provingground.database.tables.TeamsToUsersTable
import com.provingground.database.tables.UsersTable
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.net.URI

object DatabaseFactory {
    fun init() {
        val cfg = dbConfigFromEnv()

        val config = HikariConfig().apply {
            jdbcUrl = cfg.jdbcUrl
            username = cfg.user
            password = cfg.password
            maximumPoolSize = (System.getenv("DB_MAX_POOL_SIZE") ?: "10").toInt()
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"

            // Helpful timeouts
            connectionTimeout = 10_000
            idleTimeout = 60_000
            maxLifetime = 30 * 60_000
        }

        val dataSource = HikariDataSource(config)
        Database.connect(dataSource)

        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                ClubsTable,
                TeamsTable,
                UsersTable,
                ClubToUsersTable,
                TeamsToUsersTable,
                ChallengesTable,
                ChallengeSubmissionsTable,
                ConsentsTable,
                ChallengeUploadIntentsTable,
                ParentToChildrenTable,
                ChallengeToClubsTable,
                ChallengeDemoUploadIntentsTable,
            )
        }
    }
}

data class DbConfig(
    val jdbcUrl: String,
    val user: String,
    val password: String
)

fun dbConfigFromEnv(): DbConfig {
    val raw = System.getenv("DATABASE_URL")
        ?: "postgres://postgres:password@localhost:5432/localprovinggrounddb"

    if (raw.startsWith("postgres://") || raw.startsWith("postgresql://")) {
        // Parse libpq-style URL (postgres://user:pass@host:port/db)
        val uri = URI(raw)
        val userInfo = uri.userInfo.split(":")
        val rUsername = userInfo[0]
        val rPassword = userInfo.getOrElse(1) { "" }
        val host = uri.host
        val port = if (uri.port == -1) 5432 else uri.port
        val db = uri.path.trimStart('/')
        val jdbcAddedUrl = "jdbc:postgresql://$host:$port/$db"

        return DbConfig(jdbcAddedUrl, rUsername, rPassword)
    } else {
        val uri = URI(raw)
        val (user, pass) = (uri.userInfo ?: "").split(":", limit = 2).let {
            it[0] to (it.getOrNull(1) ?: "")
        }

        val jdbcUrl = buildString {
            append("jdbc:postgresql://")
            append(uri.host)
            if (uri.port != -1) append(":${uri.port}")
            append(uri.path) // includes /dbname
            // SSL flags (safe defaults; adjust if you know internal URL doesn’t need it)
            append("?sslmode=require")
        }

        return DbConfig(jdbcUrl, user, pass)
    }
}

suspend fun <T> dbQuery(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }