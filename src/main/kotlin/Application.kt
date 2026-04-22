package com.provingground

import com.provingground.database.DatabaseFactory
import com.provingground.database.tables.ChallengesTable
import com.provingground.database.tables.ClubToUsersTable
import com.provingground.database.tables.ClubsTable
import com.provingground.database.tables.SubscriptionType
import com.provingground.database.tables.TeamsTable
import com.provingground.database.tables.TeamsToUsersTable
import com.provingground.database.tables.UserRole
import com.provingground.database.tables.UsersTable
import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

fun main() {
    embeddedServer(Netty, port = System.getenv("PORT")?.toInt() ?: SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureHTTP()
    configureSecurity()

    DatabaseFactory.init()

    configureRouting()

    // TEST
    seedInitialData()
}

// TEST
fun seedInitialData() {
    transaction {
        val now = System.currentTimeMillis()

        val adminUuid = UUID.fromString("d5065ef1-e027-4bcc-83ad-79c0d3ebf063")

        val existingAdmin = UsersTable
            .selectAll()
            .where { UsersTable.id eq adminUuid }
            .singleOrNull()

        if (existingAdmin == null) {
            val passwordHasher = PasswordHasher()
            UsersTable.insert {
                it[id] = adminUuid
                it[name] = "Evan"
                it[username] = "evan"
                it[password] = passwordHasher.hash("password")
                it[email] = "evan@test.com"
                it[phone] = "2032418463"
                it[role] = UserRole.ADMIN
                it[dob] = "05/16/1993"
                it[createdAt] = now
            }
            println("✅ Seeded admin: $adminUuid")
        }

        // 1) Seed club
        val clubAccessCode = "PGFA123"

        val existingClub = ClubsTable
            .selectAll()
            .where { ClubsTable.accessCode eq clubAccessCode }
            .singleOrNull()

        val clubId = existingClub?.get(ClubsTable.id) ?: UUID.fromString("82002f2a-9fb0-4bd5-b5dd-229dc4d7be82").also { newClubId ->
            ClubsTable.insert {
                it[id] = newClubId
                it[name] = "Proving Ground FC"
                it[logoUrl] = "https://example.com/logo.png"
                it[accessCode] = clubAccessCode
                it[primaryColor] = "#FF6600"
                it[accentColor] = "#000000"
                it[subscriptionType] = SubscriptionType.CLUB_PAID
                it[createdAt] = now
            }
            println("✅ Seeded club: $newClubId")
        }

        // 2) Seed team
        val teamName = "U12 Boys Black"

        val existingTeam = TeamsTable
            .selectAll()
            .where {
                (TeamsTable.clubId eq clubId) and
                        (TeamsTable.name eq teamName)
            }
            .singleOrNull()

        val teamId = existingTeam?.get(TeamsTable.id) ?: UUID.fromString("e86815a3-8966-4f3d-adf1-d58d27454382").also { newTeamId ->
            TeamsTable.insert {
                it[id] = newTeamId
                it[name] = teamName
                it[TeamsTable.clubId] = clubId
                it[lowerAgeRange] = 18
                it[upperAgeRange] = 40
                it[createdAt] = now
            }
            println("✅ Seeded team: $newTeamId")
        }

        // 3) Find seeded user by username
        val username = "mravert"

        val userRow = UsersTable
            .selectAll()
            .where { UsersTable.username eq username }
            .singleOrNull()

        if (userRow == null) {
            println("⚠️ No user found with username=$username, skipping user/team/club assignment")
            return@transaction
        }

        val userId = userRow[UsersTable.id]

        // 4) Add user to club if missing
        val existingClubMembership = ClubToUsersTable
            .selectAll()
            .where {
                (ClubToUsersTable.userId eq userId) and
                        (ClubToUsersTable.clubId eq clubId)
            }
            .singleOrNull()

        if (existingClubMembership == null) {
            ClubToUsersTable.insert {
                it[id] = UUID.randomUUID()
                it[ClubToUsersTable.userId] = userId
                it[ClubToUsersTable.clubId] = clubId
                it[ClubToUsersTable.createdAt] = now
            }
            println("✅ Added user to club")
        }

        // 5) Add user to team if missing
        val existingTeamMembership = TeamsToUsersTable
            .selectAll()
            .where {
                (TeamsToUsersTable.userId eq userId) and
                        (TeamsToUsersTable.teamId eq teamId)
            }
            .singleOrNull()

        if (existingTeamMembership == null) {
            TeamsToUsersTable.insert {
                it[id] = UUID.randomUUID()
                it[TeamsToUsersTable.userId] = userId
                it[TeamsToUsersTable.teamId] = teamId
                it[TeamsToUsersTable.createdAt] = now
            }
            println("✅ Added user to team")
        }

        println("🎉 Seed complete")
        println("clubId=$clubId")
        println("teamId=$teamId")
        println("userId=$userId")
    }
}
