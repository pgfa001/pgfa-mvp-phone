package com.provingground.service

import com.provingground.database.repositories.ClubsRepository
import com.provingground.database.repositories.TeamsRepository
import com.provingground.database.repositories.UsersRepository
import com.provingground.database.tables.UserRole
import com.provingground.datamodels.CreateTeamRequest
import com.provingground.datamodels.GetTeamsResponse
import com.provingground.datamodels.JoinTeamsRequest
import com.provingground.datamodels.Team
import com.provingground.datamodels.TeamResponse
import com.provingground.datamodels.UpdateTeamRequest
import com.provingground.datamodels.response.GetClubTeamsResponse
import com.provingground.datamodels.response.JoinTeamsResponse
import com.provingground.datamodels.response.JoinedTeamAssignmentResponse
import com.provingground.datamodels.response.TeamSummaryResponse
import com.provingground.utils.AgeUtils
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

class TeamsService(
    private val clubsRepository: ClubsRepository,
    private val teamsRepository: TeamsRepository,
    private val usersRepository: UsersRepository
) {
    suspend fun getTeamsForClub(clubId: String): GetClubTeamsResponse {
        val clubUuid = try {
            UUID.fromString(clubId)
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid clubId")
        }

        val club = clubsRepository.getById(clubUuid)
            ?: throw IllegalArgumentException("Club not found")

        val teams = teamsRepository.getByClubId(club.id)

        return GetClubTeamsResponse(
            teams = teams.map { team ->
                TeamSummaryResponse(
                    id = team.id.toString(),
                    name = team.name,
                    lowerAgeRange = team.lowerAgeRange,
                    upperAgeRange = team.upperAgeRange
                )
            }
        )
    }

    suspend fun getAllTeams(actingUserId: UUID): GetTeamsResponse =
        newSuspendedTransaction(Dispatchers.IO) {
            val actingUser = usersRepository.getByIdTx(actingUserId)
                ?: throw IllegalArgumentException("User not found")

            if (actingUser.role != UserRole.ADMIN) {
                throw IllegalArgumentException("Only admins can view all teams")
            }

            val teams = teamsRepository.getAllTx()

            GetTeamsResponse(
                teams = teams.map { team ->
                    TeamResponse(
                        id = team.id.toString(),
                        name = team.name,
                        clubId = team.clubId.toString(),
                        lowerAgeRange = team.lowerAgeRange,
                        upperAgeRange = team.upperAgeRange,
                        createdAt = team.createdAt
                    )
                }
            )
        }

    suspend fun createTeam(
        actingUserId: UUID,
        request: CreateTeamRequest
    ): TeamResponse = newSuspendedTransaction(Dispatchers.IO) {
        val actingUser = usersRepository.getByIdTx(actingUserId)
            ?: throw IllegalArgumentException("User not found")

        if (actingUser.role != UserRole.ADMIN) {
            throw IllegalArgumentException("Only admins can create teams")
        }

        val clubUuid = try {
            UUID.fromString(request.clubId)
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid clubId")
        }

        val club = clubsRepository.getByIdTx(clubUuid)
            ?: throw IllegalArgumentException("Club not found")

        if (request.name.isBlank()) {
            throw IllegalArgumentException("Team name is required")
        }

        if (request.lowerAgeRange > request.upperAgeRange) {
            throw IllegalArgumentException("lowerAgeRange cannot be greater than upperAgeRange")
        }

        val team = Team(
            id = UUID.randomUUID(),
            name = request.name.trim(),
            clubId = club.id,
            lowerAgeRange = request.lowerAgeRange,
            upperAgeRange = request.upperAgeRange,
            createdAt = System.currentTimeMillis()
        )

        teamsRepository.createTx(team)

        TeamResponse(
            id = team.id.toString(),
            name = team.name,
            clubId = team.clubId.toString(),
            lowerAgeRange = team.lowerAgeRange,
            upperAgeRange = team.upperAgeRange,
            createdAt = team.createdAt
        )
    }

    suspend fun updateTeam(
        actingUserId: UUID,
        teamId: String,
        request: UpdateTeamRequest
    ): TeamResponse = newSuspendedTransaction(Dispatchers.IO) {
        val actingUser = usersRepository.getByIdTx(actingUserId)
            ?: throw IllegalArgumentException("User not found")

        if (actingUser.role != UserRole.ADMIN) {
            throw IllegalArgumentException("Only admins can update teams")
        }

        val teamUuid = try {
            UUID.fromString(teamId)
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid teamId")
        }

        val clubUuid = try {
            UUID.fromString(request.clubId)
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid clubId")
        }

        val existingTeam = teamsRepository.getByIdTx(teamUuid)
            ?: throw IllegalArgumentException("Team not found")

        val club = clubsRepository.getByIdTx(clubUuid)
            ?: throw IllegalArgumentException("Club not found")

        if (request.name.isBlank()) {
            throw IllegalArgumentException("Team name is required")
        }

        if (request.lowerAgeRange > request.upperAgeRange) {
            throw IllegalArgumentException("lowerAgeRange cannot be greater than upperAgeRange")
        }

        val updated = teamsRepository.updateTx(teamUuid, request)
        if (!updated) {
            throw IllegalArgumentException("Failed to update team")
        }

        TeamResponse(
            id = existingTeam.id.toString(),
            name = request.name.trim(),
            clubId = club.id.toString(),
            lowerAgeRange = request.lowerAgeRange,
            upperAgeRange = request.upperAgeRange,
            createdAt = existingTeam.createdAt
        )
    }

    suspend fun joinTeams(
        actingUserId: UUID,
        request: JoinTeamsRequest
    ): JoinTeamsResponse = newSuspendedTransaction(Dispatchers.IO) {
        val actingUser = usersRepository.getByIdTx(actingUserId)
            ?: throw IllegalArgumentException("Acting user not found")

        if (request.assignments.isEmpty()) {
            throw IllegalArgumentException("At least one assignment is required")
        }

        val joinedAssignments = mutableListOf<JoinedTeamAssignmentResponse>()

        request.assignments.forEach { assignment ->
            val targetUserId = try {
                UUID.fromString(assignment.userId)
            } catch (_: Exception) {
                throw IllegalArgumentException("Invalid userId: ${assignment.userId}")
            }

            if (assignment.teamIds.isEmpty()) {
                throw IllegalArgumentException("At least one teamId is required for user ${assignment.userId}")
            }

            val targetUser = usersRepository.getByIdTx(targetUserId)
                ?: throw IllegalArgumentException("Target user not found: ${assignment.userId}")

            when (actingUser.role) {
                UserRole.ATHLETE -> {
                    if (targetUserId != actingUser.id) {
                        throw IllegalArgumentException("Athletes can only assign teams for themselves")
                    }
                    if (request.assignments.size != 1) {
                        throw IllegalArgumentException("Athletes may only submit one assignment")
                    }
                    if (assignment.teamIds.size != 1) {
                        throw IllegalArgumentException("Athletes may only join one team")
                    }
                }

                UserRole.PARENT -> {
                    val isOwnChild = usersRepository.isParentOfChildTx(
                        parentUserId = actingUser.id,
                        childUserId = targetUserId
                    )

                    if (!isOwnChild) {
                        throw IllegalArgumentException("Parents can only assign teams for their own children")
                    }

                    if (assignment.teamIds.size != 1) {
                        throw IllegalArgumentException("Each child may only be assigned one team")
                    }

                    if (targetUser.role != UserRole.ATHLETE) {
                        throw IllegalArgumentException("Parents may only assign athlete children to teams")
                    }
                }

                UserRole.COACH -> {
                    if (targetUserId != actingUser.id) {
                        throw IllegalArgumentException("Coaches can only assign teams for themselves")
                    }
                    if (request.assignments.size != 1) {
                        throw IllegalArgumentException("Coaches may only submit one assignment for themselves")
                    }
                }

                UserRole.ADMIN -> {
                    throw IllegalArgumentException("Admins cannot use this team join flow")
                }
            }

            val targetUserClubIds = clubsRepository
                .getClubsForUserTx(targetUserId)
                .map { it.id }
                .toSet()

            if (targetUserClubIds.isEmpty()) {
                throw IllegalArgumentException("User is not associated with any club")
            }

            val parsedTeamIds = assignment.teamIds.map { teamIdString ->
                try {
                    UUID.fromString(teamIdString)
                } catch (_: Exception) {
                    throw IllegalArgumentException("Invalid teamId: $teamIdString")
                }
            }.distinct()

            val teams = parsedTeamIds.map { teamId ->
                teamsRepository.getByIdTx(teamId)
                    ?: throw IllegalArgumentException("Team not found: $teamId")
            }

            teams.forEach { team ->
                if (team.clubId !in targetUserClubIds) {
                    throw IllegalArgumentException("Team does not belong to the same club as the user")
                }

                if (targetUser.role == UserRole.ATHLETE) {
                    val athleteAge = AgeUtils.calculateAge(targetUser.dob)
                    val allowedRange = (team.lowerAgeRange - 1)..team.upperAgeRange

                    if (athleteAge !in allowedRange) {
                        throw IllegalArgumentException(
                            "${targetUser.name} with age $athleteAge does not fit team ${team.name} age range"
                        )
                    }
                }
            }

            // Replace semantics: clear existing team memberships first
            teamsRepository.removeUserFromAllTeamsTx(targetUserId)

            // Then add back only the selected teams
            teams.forEach { team ->
                teamsRepository.addUserToTeamTx(
                    userId = targetUserId,
                    teamId = team.id
                )
            }

            joinedAssignments.add(
                JoinedTeamAssignmentResponse(
                    userId = targetUserId.toString(),
                    teamIds = teams.map { it.id.toString() }
                )
            )
        }

        JoinTeamsResponse(
            success = true,
            assignments = joinedAssignments
        )
    }
}