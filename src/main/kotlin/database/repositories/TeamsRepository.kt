package com.provingground.database.repositories

import com.provingground.database.dbQuery
import com.provingground.database.tables.TeamsTable
import com.provingground.database.tables.TeamsToUsersTable
import com.provingground.database.tables.UserRole
import com.provingground.database.tables.UsersTable
import com.provingground.database.toTeam
import com.provingground.database.toUser
import com.provingground.datamodels.Team
import com.provingground.datamodels.UpdateTeamRequest
import com.provingground.datamodels.User
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.UUID

class TeamsRepository {

    fun createTx(team: Team): Team {
        TeamsTable.insert {
            it[id] = team.id
            it[name] = team.name
            it[clubId] = team.clubId
            it[lowerAgeRange] = team.lowerAgeRange
            it[upperAgeRange] = team.upperAgeRange
            it[createdAt] = team.createdAt
        }
        return team
    }

    suspend fun create(team: Team): Team = dbQuery {
        createTx(team)
    }

    fun getByIdTx(id: UUID): Team? {
        return TeamsTable
            .selectAll()
            .where { TeamsTable.id eq id }
            .singleOrNull()
            ?.toTeam()
    }

    suspend fun getById(id: UUID): Team? = dbQuery {
        getByIdTx(id)
    }

    fun getByIdsTx(ids: List<UUID>): List<Team> {
        if (ids.isEmpty()) return emptyList()

        return TeamsTable
            .selectAll()
            .where { TeamsTable.id inList ids }
            .map { it.toTeam() }
    }

    fun getAllTx(): List<Team> {
        return TeamsTable.selectAll().map { it.toTeam() }
    }

    suspend fun getAll(): List<Team> = dbQuery {
        getAllTx()
    }

    fun getByClubIdTx(clubId: UUID): List<Team> {
        return TeamsTable
            .selectAll()
            .where { TeamsTable.clubId eq clubId }
            .map { it.toTeam() }
    }

    suspend fun getByClubId(clubId: UUID): List<Team> = dbQuery {
        getByClubIdTx(clubId)
    }

    fun updateTx(id: UUID, request: UpdateTeamRequest): Boolean {
        val clubUuid = UUID.fromString(request.clubId)

        return TeamsTable.update({ TeamsTable.id eq id }) {
            it[name] = request.name
            it[clubId] = clubUuid
            it[lowerAgeRange] = request.lowerAgeRange
            it[upperAgeRange] = request.upperAgeRange
        } > 0
    }

    fun deleteTx(id: UUID): Boolean {
        return TeamsTable.deleteWhere { TeamsTable.id eq id } > 0
    }

    suspend fun delete(id: UUID): Boolean = dbQuery {
        deleteTx(id)
    }

    fun addUserToTeamTx(
        userId: UUID,
        teamId: UUID,
        relationshipId: UUID = UUID.randomUUID(),
        createdAt: Long = System.currentTimeMillis()
    ) {
        TeamsToUsersTable.insert {
            it[id] = relationshipId
            it[TeamsToUsersTable.userId] = userId
            it[TeamsToUsersTable.teamId] = teamId
            it[TeamsToUsersTable.createdAt] = createdAt
        }
    }

    suspend fun addUserToTeam(
        userId: UUID,
        teamId: UUID,
        relationshipId: UUID = UUID.randomUUID(),
        createdAt: Long = System.currentTimeMillis()
    ) = dbQuery {
        addUserToTeamTx(userId, teamId, relationshipId, createdAt)
    }

    fun getTeamsForUserTx(userId: UUID): List<Team> {
        return TeamsTable
            .join(
                otherTable = TeamsToUsersTable,
                joinType = JoinType.INNER,
                onColumn = TeamsTable.id,
                otherColumn = TeamsToUsersTable.teamId
            )
            .selectAll()
            .where { TeamsToUsersTable.userId eq userId }
            .map { it.toTeam() }
    }

    fun removeUserFromAllTeamsTx(userId: UUID): Boolean {
        return TeamsToUsersTable.deleteWhere { TeamsToUsersTable.userId eq userId } > 0
    }

    suspend fun removeUserFromAllTeams(userId: UUID): Boolean = dbQuery {
        removeUserFromAllTeamsTx(userId)
    }

    fun removeUserFromTeamTx(userId: UUID, teamId: UUID): Boolean {
        return TeamsToUsersTable.deleteWhere {
            (TeamsToUsersTable.userId eq userId) and (TeamsToUsersTable.teamId eq teamId)
        } > 0
    }

    suspend fun removeUserFromTeam(userId: UUID, teamId: UUID): Boolean = dbQuery {
        removeUserFromTeamTx(userId, teamId)
    }

    fun isUserOnTeamTx(userId: UUID, teamId: UUID): Boolean {
        return TeamsToUsersTable
            .selectAll()
            .where {
                (TeamsToUsersTable.userId eq userId) and
                        (TeamsToUsersTable.teamId eq teamId)
            }
            .any()
    }

    suspend fun isUserOnTeam(userId: UUID, teamId: UUID): Boolean = dbQuery {
        isUserOnTeamTx(userId, teamId)
    }

    fun getPrimaryTeamForUserTx(userId: java.util.UUID): Team? {
        return getTeamsForUserTx(userId).firstOrNull()
    }

    fun getAthletesForTeamTx(teamId: java.util.UUID): List<User> {
        return (UsersTable innerJoin TeamsToUsersTable)
            .selectAll()
            .where {
                (TeamsToUsersTable.teamId eq teamId) and
                        (TeamsToUsersTable.userId eq UsersTable.id) and
                        (UsersTable.role eq UserRole.ATHLETE)
            }
            .map { it.toUser() }
    }
}