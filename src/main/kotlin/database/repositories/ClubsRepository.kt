package com.provingground.database.repositories

import com.provingground.database.dbQuery
import com.provingground.database.tables.ClubToUsersTable
import com.provingground.database.tables.ClubsTable
import com.provingground.database.tables.UsersTable
import com.provingground.database.toClub
import com.provingground.datamodels.Club
import com.provingground.datamodels.User
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.UUID

class ClubsRepository {

    fun createTx(club: Club): Club {
        ClubsTable.insert {
            it[id] = club.id
            it[name] = club.name
            it[logoUrl] = club.logoUrl
            it[accessCode] = club.accessCode
            it[primaryColor] = club.primaryColor
            it[accentColor] = club.accentColor
            it[subscriptionType] = club.subscriptionType
            it[createdAt] = club.createdAt
        }
        return club
    }

    suspend fun create(club: Club): Club = dbQuery {
        createTx(club)
    }

    fun getByIdTx(id: UUID): Club? {
        return ClubsTable
            .selectAll()
            .where { ClubsTable.id eq id }
            .singleOrNull()
            ?.toClub()
    }

    suspend fun getById(id: UUID): Club? = dbQuery {
        getByIdTx(id)
    }

    fun getByAccessCodeTx(accessCode: String): Club? {
        return ClubsTable
            .selectAll()
            .where { ClubsTable.accessCode eq accessCode }
            .singleOrNull()
            ?.toClub()
    }

    suspend fun getByAccessCode(accessCode: String): Club? = dbQuery {
        getByAccessCodeTx(accessCode)
    }

    fun getAllTx(): List<Club> {
        return ClubsTable.selectAll().map { it.toClub() }
    }

    suspend fun getAll(): List<Club> = dbQuery {
        getAllTx()
    }

    fun updateTx(id: UUID, club: Club): Boolean {
        return ClubsTable.update({ ClubsTable.id eq id }) {
            it[name] = club.name
            it[logoUrl] = club.logoUrl
            it[accessCode] = club.accessCode
            it[primaryColor] = club.primaryColor
            it[accentColor] = club.accentColor
            it[subscriptionType] = club.subscriptionType
        } > 0
    }

    suspend fun update(id: UUID, club: Club): Boolean = dbQuery {
        updateTx(id, club)
    }

    fun deleteTx(id: UUID): Boolean {
        return ClubsTable.deleteWhere { ClubsTable.id eq id } > 0
    }

    suspend fun delete(id: UUID): Boolean = dbQuery {
        deleteTx(id)
    }

    fun addUserToClubTx(
        userId: UUID,
        clubId: UUID,
        relationshipId: UUID = UUID.randomUUID(),
        createdAt: Long = System.currentTimeMillis()
    ) {
        ClubToUsersTable.insert {
            it[id] = relationshipId
            it[ClubToUsersTable.userId] = userId
            it[ClubToUsersTable.clubId] = clubId
            it[ClubToUsersTable.createdAt] = createdAt
        }
    }

    suspend fun addUserToClub(
        userId: UUID,
        clubId: UUID,
        relationshipId: UUID = UUID.randomUUID(),
        createdAt: Long = System.currentTimeMillis()
    ) = dbQuery {
        addUserToClubTx(userId, clubId, relationshipId, createdAt)
    }

    fun removeUserFromClubTx(userId: UUID, clubId: UUID): Boolean {
        return ClubToUsersTable.deleteWhere {
            (ClubToUsersTable.userId eq userId) and (ClubToUsersTable.clubId eq clubId)
        } > 0
    }

    suspend fun removeUserFromClub(userId: UUID, clubId: UUID): Boolean = dbQuery {
        removeUserFromClubTx(userId, clubId)
    }

    fun isUserInClubTx(userId: UUID, clubId: UUID): Boolean {
        return ClubToUsersTable
            .selectAll()
            .where {
                (ClubToUsersTable.userId eq userId) and
                        (ClubToUsersTable.clubId eq clubId)
            }
            .any()
    }

    suspend fun isUserInClub(userId: UUID, clubId: UUID): Boolean = dbQuery {
        isUserInClubTx(userId, clubId)
    }

    fun getClubsForUserTx(userId: UUID): List<Club> {
        return (ClubsTable innerJoin ClubToUsersTable)
            .selectAll()
            .where { ClubToUsersTable.userId eq userId }
            .map { it.toClub() }
    }
}