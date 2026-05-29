package com.provingground.service

import com.provingground.database.repositories.ClubsRepository
import com.provingground.database.repositories.TeamsRepository
import com.provingground.database.repositories.UsersRepository
import com.provingground.database.tables.UserRole
import com.provingground.datamodels.response.ChildUserSummaryResponse
import com.provingground.datamodels.response.SearchUsersResponse
import com.provingground.datamodels.response.UserDetailsResponse
import com.provingground.datamodels.response.UserSearchResultResponse
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

class UserService(
    private val usersRepository: UsersRepository,
    private val clubsRepository: ClubsRepository,
    private val teamsRepository: TeamsRepository
) {
    suspend fun searchUsers(
        actingUserId: UUID,
        query: String?,
        clubId: String?,
        role: String?,
        limit: Int = 10
    ): SearchUsersResponse = newSuspendedTransaction(Dispatchers.IO) {
        if (limit <= 0) {
            throw IllegalArgumentException("limit must be greater than 0")
        }

        val actingUser = usersRepository.getByIdTx(actingUserId)
            ?: throw IllegalArgumentException("User not found")

        val requestedClubId = clubId?.takeIf { it.isNotBlank() }?.let {
            try {
                UUID.fromString(it)
            } catch (_: Exception) {
                throw IllegalArgumentException("Invalid clubId")
            }
        }

        val requestedRole = role?.takeIf { it.isNotBlank() }?.let {
            try {
                UserRole.valueOf(it.uppercase())
            } catch (_: Exception) {
                throw IllegalArgumentException("Invalid role")
            }
        }

        val scopedUsers = when (actingUser.role) {
            UserRole.SUPERADMIN -> {
                if (requestedClubId != null) {
                    usersRepository.getUsersForClubIdsTx(listOf(requestedClubId))
                } else {
                    usersRepository.getAllTx()
                }
            }

            UserRole.ADMIN -> {
                val adminClubIds = clubsRepository.getClubIdsForAdminTx(actingUser.id).toSet()
                if (adminClubIds.isEmpty()) {
                    throw IllegalArgumentException("Admin is not assigned to a club")
                }

                val clubIds = if (requestedClubId != null) {
                    if (requestedClubId !in adminClubIds) {
                        throw IllegalArgumentException("Admin may only search users in assigned clubs")
                    }
                    listOf(requestedClubId)
                } else {
                    adminClubIds.toList()
                }

                usersRepository.getUsersForClubIdsTx(clubIds)
            }

            UserRole.COACH -> {
                val coachedTeams = teamsRepository.getTeamsForUserTx(actingUser.id)
                    .filter { team -> requestedClubId == null || team.clubId == requestedClubId }

                coachedTeams
                    .flatMap { team -> teamsRepository.getAthletesForTeamTx(team.id) }
                    .distinctBy { user -> user.id }
            }

            UserRole.ATHLETE, UserRole.PARENT -> {
                throw IllegalArgumentException("Only super admins, admins, and coaches can search users")
            }
        }

        val normalizedQuery = query?.trim()?.takeIf { it.isNotBlank() }?.lowercase()

        val filteredUsers = scopedUsers
            .asSequence()
            .filter { user -> requestedRole == null || user.role == requestedRole }
            .filter { user ->
                normalizedQuery == null ||
                        user.name.lowercase().contains(normalizedQuery) ||
                        user.username.lowercase().contains(normalizedQuery) ||
                        user.email?.lowercase()?.contains(normalizedQuery) == true ||
                        user.phone?.lowercase()?.contains(normalizedQuery) == true
            }
            .sortedWith(compareBy({ it.name.lowercase() }, { it.username.lowercase() }))
            .take(limit)
            .toList()

        SearchUsersResponse(
            query = normalizedQuery,
            limit = limit,
            users = filteredUsers.map { user ->
                UserSearchResultResponse(
                    id = user.id.toString(),
                    name = user.name,
                    username = user.username,
                    role = user.role,
                    gender = user.gender,
                    email = user.email,
                    phone = user.phone,
                    avatarUrl = user.avatarUrl,
                    position = user.position,
                    clubIds = clubsRepository.getClubsForUserTx(user.id).map { it.id.toString() }
                )
            }
        )
    }

    suspend fun getUserDetails(
        actingUserId: UUID,
        requestedUserId: String
    ): UserDetailsResponse = newSuspendedTransaction(Dispatchers.IO) {
        val requestedUuid = try {
            UUID.fromString(requestedUserId)
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid userId")
        }

        if (actingUserId != requestedUuid) {
            throw IllegalArgumentException("Users may only retrieve their own details")
        }

        val user = usersRepository.getByIdTx(requestedUuid)
            ?: throw IllegalArgumentException("User not found")

        val children = if (user.role == UserRole.PARENT) {
            usersRepository.getChildrenForParentTx(user.id).map { child ->
                ChildUserSummaryResponse(
                    id = child.id.toString(),
                    name = child.name,
                    username = child.username,
                    dob = child.dob,
                    gender = child.gender,
                    position = child.position
                )
            }
        } else {
            emptyList()
        }

        UserDetailsResponse(
            id = user.id.toString(),
            name = user.name,
            username = user.username,
            role = user.role,
            dob = user.dob,
            gender = user.gender,
            email = user.email,
            phone = user.phone,
            avatarUrl = user.avatarUrl,
            position = user.position,
            children = children
        )
    }
}
