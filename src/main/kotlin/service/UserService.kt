package com.provingground.service

import com.provingground.PasswordHasher
import com.provingground.database.repositories.ClubsRepository
import com.provingground.database.repositories.TeamsRepository
import com.provingground.database.repositories.UsersRepository
import com.provingground.database.tables.UserRole
import com.provingground.datamodels.CreateCmsChildAccountRequest
import com.provingground.datamodels.CreateCmsUserRequest
import com.provingground.datamodels.CreateSuperAdminRequest
import com.provingground.datamodels.ResetUserPasswordRequest
import com.provingground.datamodels.UpdateUserClubRequest
import com.provingground.datamodels.UpdateUserTeamsRequest
import com.provingground.datamodels.UpdateUserUsernameRequest
import com.provingground.datamodels.User
import com.provingground.datamodels.response.CmsCreatedChildUserResponse
import com.provingground.datamodels.response.CmsCreatedUserResponse
import com.provingground.datamodels.response.DeleteAccountResponse
import com.provingground.datamodels.response.ChildUserSummaryResponse
import com.provingground.datamodels.response.ResetUserPasswordResponse
import com.provingground.datamodels.response.SearchUsersResponse
import com.provingground.datamodels.response.SuperAdminUserResponse
import com.provingground.datamodels.response.UserManagementResponse
import com.provingground.datamodels.response.UserDetailsResponse
import com.provingground.datamodels.response.UserSearchResultResponse
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.security.SecureRandom
import java.util.UUID

class UserService(
    private val usersRepository: UsersRepository,
    private val clubsRepository: ClubsRepository,
    private val teamsRepository: TeamsRepository,
    private val subscriptionService: SubscriptionService,
    private val passwordHasher: PasswordHasher
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
                    state = user.state,
                    town = user.town,
                    socialMediaHandle = user.socialMediaHandle,
                    clubIds = clubsRepository.getClubsForUserTx(user.id).map { it.id.toString() },
                    teamIds = teamsRepository.getTeamsForUserTx(user.id).map { it.id.toString() }
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
                    subscription = subscriptionService.getEntitlementForAthleteTx(child),
                    position = child.position,
                    state = child.state,
                    town = child.town,
                    socialMediaHandle = child.socialMediaHandle
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
            state = user.state,
            town = user.town,
            socialMediaHandle = user.socialMediaHandle,
            subscription = if (user.role == UserRole.ATHLETE) {
                subscriptionService.getEntitlementForAthleteTx(user)
            } else {
                null
            },
            children = children
        )
    }

    suspend fun createSuperAdmin(
        actingUserId: UUID,
        request: CreateSuperAdminRequest
    ): SuperAdminUserResponse = newSuspendedTransaction(Dispatchers.IO) {
        requireSuperAdminTx(actingUserId)

        if (request.name.isBlank()) throw IllegalArgumentException("Name is required")
        if (request.username.isBlank()) throw IllegalArgumentException("Username is required")
        if (request.password.isBlank()) throw IllegalArgumentException("Password is required")
        if (request.email.isBlank()) throw IllegalArgumentException("Email is required")
        if (request.phone.isBlank()) throw IllegalArgumentException("Phone is required")
        if (request.dob.isBlank()) throw IllegalArgumentException("Date of birth is required")

        val username = request.username.trim()
        val email = request.email.trim()

        if (usersRepository.usernameExistsTx(username)) {
            throw IllegalArgumentException("Username already exists")
        }

        if (usersRepository.emailExistsTx(email)) {
            throw IllegalArgumentException("Email already exists")
        }

        val now = System.currentTimeMillis()
        val superAdmin = User(
            id = UUID.randomUUID(),
            name = request.name.trim(),
            username = username,
            password = passwordHasher.hash(request.password),
            email = email,
            phone = request.phone.trim(),
            role = UserRole.SUPERADMIN,
            dob = request.dob,
            gender = request.gender.trimToNull(),
            avatarUrl = null,
            position = null,
            state = request.state.trimToNull(),
            town = request.town.trimToNull(),
            socialMediaHandle = request.socialMediaHandle.trimToNull(),
            createdAt = now
        )

        usersRepository.createTx(superAdmin)

        SuperAdminUserResponse(
            id = superAdmin.id.toString(),
            name = superAdmin.name,
            username = superAdmin.username,
            email = superAdmin.email ?: "",
            phone = superAdmin.phone ?: "",
            role = superAdmin.role,
            dob = superAdmin.dob,
            gender = superAdmin.gender,
            state = superAdmin.state,
            town = superAdmin.town,
            socialMediaHandle = superAdmin.socialMediaHandle,
            createdAt = superAdmin.createdAt
        )
    }

    suspend fun createCmsUser(
        actingUserId: UUID,
        request: CreateCmsUserRequest
    ): CmsCreatedUserResponse = newSuspendedTransaction(Dispatchers.IO) {
        requireSuperAdminTx(actingUserId)

        val clubId = parseUuid(request.clubId, "clubId")
        clubsRepository.getByIdTx(clubId)
            ?: throw IllegalArgumentException("Club not found")

        val role = parseCmsCreatableRole(request.role)

        if (request.name.isBlank()) throw IllegalArgumentException("Name is required")
        if (request.username.isBlank()) throw IllegalArgumentException("Username is required")
        if (request.password.isBlank()) throw IllegalArgumentException("Password is required")
        if (request.dob.isBlank()) throw IllegalArgumentException("Date of birth is required")

        if (role != UserRole.PARENT && request.childAccounts.isNotEmpty()) {
            throw IllegalArgumentException("Only parent accounts can include child accounts")
        }

        if (role == UserRole.ATHLETE && request.position.isNullOrBlank()) {
            throw IllegalArgumentException("Athlete accounts require a position")
        }

        if (role == UserRole.ATHLETE && request.gender.isNullOrBlank()) {
            throw IllegalArgumentException("Athlete accounts require a gender")
        }

        validateUsernameAndEmailAvailability(
            username = request.username,
            email = request.email
        )

        val teamIds = validateTeamAssignmentsForClub(
            role = role,
            clubId = clubId,
            teamIds = request.teamIds
        )

        val now = System.currentTimeMillis()
        val user = User(
            id = UUID.randomUUID(),
            name = request.name.trim(),
            username = request.username.trim(),
            password = passwordHasher.hash(request.password),
            email = request.email.trimToNull(),
            phone = request.phone.trimToNull(),
            role = role,
            dob = request.dob,
            gender = if (role == UserRole.ATHLETE) request.gender?.trim() else request.gender.trimToNull(),
            avatarUrl = null,
            position = if (role == UserRole.ATHLETE) request.position else null,
            state = request.state.trimToNull(),
            town = request.town.trimToNull(),
            socialMediaHandle = request.socialMediaHandle.trimToNull(),
            createdAt = now
        )

        usersRepository.createTx(user)
        clubsRepository.addUserToClubTx(user.id, clubId, createdAt = now)
        assignTeamsToUserTx(user.id, teamIds, now)

        if (role == UserRole.ATHLETE) {
            subscriptionService.createTrialForAthleteTx(
                athleteUserId = user.id,
                payerUserId = user.id,
                now = now
            )
        }

        val children = if (role == UserRole.PARENT) {
            request.childAccounts.map { childRequest ->
                createCmsChildAthleteTx(
                    parentUserId = user.id,
                    clubId = clubId,
                    request = childRequest
                )
            }
        } else {
            emptyList()
        }

        CmsCreatedUserResponse(
            id = user.id.toString(),
            name = user.name,
            username = user.username,
            role = user.role,
            dob = user.dob,
            gender = user.gender,
            email = user.email,
            phone = user.phone,
            position = user.position,
            state = user.state,
            town = user.town,
            socialMediaHandle = user.socialMediaHandle,
            clubIds = listOf(clubId.toString()),
            teamIds = teamIds.map { it.toString() },
            children = children,
            createdAt = user.createdAt
        )
    }

    suspend fun resetUserPassword(
        actingUserId: UUID,
        requestedUserId: String,
        request: ResetUserPasswordRequest
    ): ResetUserPasswordResponse = newSuspendedTransaction(Dispatchers.IO) {
        val actingUser = usersRepository.getByIdTx(actingUserId)
            ?: throw IllegalArgumentException("User not found")

        if (actingUser.role != UserRole.SUPERADMIN) {
            throw IllegalArgumentException("Only super admins can reset user passwords")
        }

        val requestedUuid = try {
            UUID.fromString(requestedUserId)
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid userId")
        }

        val user = usersRepository.getByIdTx(requestedUuid)
            ?: throw IllegalArgumentException("Target user not found")

        val newPassword = request.password
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: generateTemporaryPassword()

        if (newPassword.length < 8) {
            throw IllegalArgumentException("Password must be at least 8 characters")
        }

        usersRepository.updatePasswordTx(
            id = user.id,
            passwordHash = passwordHasher.hash(newPassword)
        )

        ResetUserPasswordResponse(
            userId = user.id.toString(),
            username = user.username,
            temporaryPassword = newPassword,
            message = "Password reset successfully"
        )
    }

    suspend fun deleteUser(
        actingUserId: UUID,
        requestedUserId: String
    ): UserManagementResponse = newSuspendedTransaction(Dispatchers.IO) {
        val actingUser = requireSuperAdminTx(actingUserId)
        val targetUser = getTargetUserTx(requestedUserId)

        if (targetUser.id == actingUser.id) {
            throw IllegalArgumentException("Super admins cannot delete their own account")
        }

        usersRepository.deleteUserFullyTx(targetUser.id)

        UserManagementResponse(
            userId = targetUser.id.toString(),
            username = targetUser.username,
            message = "User deleted successfully"
        )
    }

    suspend fun deleteMyAccount(
        actingUserId: UUID
    ): DeleteAccountResponse {
        newSuspendedTransaction(Dispatchers.IO) {
            usersRepository.getByIdTx(actingUserId)
                ?: throw IllegalArgumentException("User not found")

            if (usersRepository.hasCreatedChallengesTx(actingUserId)) {
                throw IllegalArgumentException("User cannot be deleted because they have created challenges")
            }
        }

        subscriptionService.cancelStripeSubscriptionsForUserDeletion(actingUserId)

        return newSuspendedTransaction(Dispatchers.IO) {
            val deleted = usersRepository.deleteUserFullyTx(actingUserId)
            if (!deleted) {
                throw IllegalArgumentException("User not found")
            }

            DeleteAccountResponse(deleted = true)
        }
    }

    suspend fun updateUsername(
        actingUserId: UUID,
        requestedUserId: String,
        request: UpdateUserUsernameRequest
    ): UserManagementResponse = newSuspendedTransaction(Dispatchers.IO) {
        requireSuperAdminTx(actingUserId)
        val targetUser = getTargetUserTx(requestedUserId)
        val username = request.username.trim()

        if (username.isBlank()) {
            throw IllegalArgumentException("Username is required")
        }

        val existing = usersRepository.getByUsernameTx(username)
        if (existing != null && existing.id != targetUser.id) {
            throw IllegalArgumentException("Username already exists")
        }

        usersRepository.updateUsernameTx(targetUser.id, username)
        buildUserManagementResponse(targetUser.id, username, "Username updated successfully")
    }

    suspend fun updateUserClub(
        actingUserId: UUID,
        requestedUserId: String,
        request: UpdateUserClubRequest
    ): UserManagementResponse = newSuspendedTransaction(Dispatchers.IO) {
        requireSuperAdminTx(actingUserId)
        val targetUser = getTargetUserTx(requestedUserId)

        if (targetUser.role == UserRole.SUPERADMIN) {
            throw IllegalArgumentException("Super admins are not assigned to clubs")
        }

        val clubIds = when {
            !request.clubIds.isNullOrEmpty() -> request.clubIds.map { parseUuid(it, "clubId") }.distinct()
            !request.clubId.isNullOrBlank() -> listOf(parseUuid(request.clubId, "clubId"))
            else -> throw IllegalArgumentException("At least one clubId is required")
        }

        if (targetUser.role != UserRole.ADMIN && clubIds.size != 1) {
            throw IllegalArgumentException("Only club admins can be assigned to multiple clubs")
        }

        clubIds.forEach { clubId ->
            clubsRepository.getByIdTx(clubId)
                ?: throw IllegalArgumentException("Club not found: $clubId")
        }

        clubsRepository.replaceUserClubsTx(
            userId = targetUser.id,
            clubIds = clubIds,
            isAdmin = targetUser.role == UserRole.ADMIN
        )

        if (targetUser.role == UserRole.ATHLETE || targetUser.role == UserRole.COACH) {
            teamsRepository.removeUserFromAllTeamsTx(targetUser.id)
        }

        buildUserManagementResponse(targetUser.id, targetUser.username, "User clubs updated successfully")
    }

    suspend fun updateUserTeams(
        actingUserId: UUID,
        requestedUserId: String,
        request: UpdateUserTeamsRequest
    ): UserManagementResponse = newSuspendedTransaction(Dispatchers.IO) {
        requireSuperAdminTx(actingUserId)
        val targetUser = getTargetUserTx(requestedUserId)

        if (targetUser.role != UserRole.ATHLETE && targetUser.role != UserRole.COACH) {
            throw IllegalArgumentException("Only athletes and coaches can be assigned to teams")
        }

        val requestedTeamIds = request.teamIds
            .map { parseUuid(it, "teamId") }
            .distinct()

        if (requestedTeamIds.isEmpty()) {
            throw IllegalArgumentException("At least one teamId is required")
        }

        if (targetUser.role == UserRole.ATHLETE && requestedTeamIds.size != 1) {
            throw IllegalArgumentException("Athletes can only be assigned to one team")
        }

        val teams = teamsRepository.getByIdsTx(requestedTeamIds)
        val foundTeamIds = teams.map { it.id }.toSet()
        val missingTeamId = requestedTeamIds.firstOrNull { it !in foundTeamIds }
        if (missingTeamId != null) {
            throw IllegalArgumentException("Team not found: $missingTeamId")
        }

        val teamClubIds = teams.map { it.clubId }.distinct()
        if (teamClubIds.size != 1) {
            throw IllegalArgumentException("All selected teams must belong to the same club")
        }

        val clubId = teamClubIds.first()
        clubsRepository.replaceUserClubTx(
            userId = targetUser.id,
            clubId = clubId,
            isAdmin = false
        )

        teamsRepository.removeUserFromAllTeamsTx(targetUser.id)
        requestedTeamIds.forEach { teamId ->
            teamsRepository.addUserToTeamTx(targetUser.id, teamId)
        }

        buildUserManagementResponse(targetUser.id, targetUser.username, "User teams updated successfully")
    }

    private fun createCmsChildAthleteTx(
        parentUserId: UUID,
        clubId: UUID,
        request: CreateCmsChildAccountRequest
    ): CmsCreatedChildUserResponse {
        if (request.name.isBlank()) throw IllegalArgumentException("Child name is required")
        if (request.username.isBlank()) throw IllegalArgumentException("Child username is required")
        if (request.password.isBlank()) throw IllegalArgumentException("Child password is required")
        if (request.dob.isBlank()) throw IllegalArgumentException("Child date of birth is required")
        if (request.gender.isNullOrBlank()) throw IllegalArgumentException("Child athlete accounts require a gender")
        if (request.position.isBlank()) throw IllegalArgumentException("Child athlete accounts require a position")

        validateUsernameAndEmailAvailability(
            username = request.username,
            email = null
        )

        val teamIds = validateTeamAssignmentsForClub(
            role = UserRole.ATHLETE,
            clubId = clubId,
            teamIds = request.teamIds
        )

        val now = System.currentTimeMillis()
        val childUser = User(
            id = UUID.randomUUID(),
            name = request.name.trim(),
            username = request.username.trim(),
            password = passwordHasher.hash(request.password),
            email = null,
            phone = null,
            role = UserRole.ATHLETE,
            dob = request.dob,
            gender = request.gender.trim(),
            avatarUrl = null,
            position = request.position.trim(),
            state = request.state.trimToNull(),
            town = request.town.trimToNull(),
            socialMediaHandle = request.socialMediaHandle.trimToNull(),
            createdAt = now
        )

        usersRepository.createTx(childUser)
        clubsRepository.addUserToClubTx(childUser.id, clubId, createdAt = now)
        assignTeamsToUserTx(childUser.id, teamIds, now)
        usersRepository.addChildToParentTx(
            parentUserId = parentUserId,
            childUserId = childUser.id,
            createdAt = now
        )
        subscriptionService.createTrialForAthleteTx(
            athleteUserId = childUser.id,
            payerUserId = parentUserId,
            now = now
        )

        return CmsCreatedChildUserResponse(
            id = childUser.id.toString(),
            name = childUser.name,
            username = childUser.username,
            role = childUser.role,
            dob = childUser.dob,
            gender = childUser.gender,
            position = childUser.position,
            state = childUser.state,
            town = childUser.town,
            socialMediaHandle = childUser.socialMediaHandle,
            clubIds = listOf(clubId.toString()),
            teamIds = teamIds.map { it.toString() },
            createdAt = childUser.createdAt
        )
    }

    private fun parseCmsCreatableRole(role: String): UserRole {
        val parsedRole = try {
            UserRole.valueOf(role.uppercase())
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid role")
        }

        if (parsedRole != UserRole.COACH && parsedRole != UserRole.ATHLETE && parsedRole != UserRole.PARENT) {
            throw IllegalArgumentException("CMS user creation only supports COACH, ATHLETE, or PARENT")
        }

        return parsedRole
    }

    private fun validateUsernameAndEmailAvailability(
        username: String,
        email: String?
    ) {
        val normalizedUsername = username.trim()
        if (usersRepository.usernameExistsTx(normalizedUsername)) {
            throw IllegalArgumentException("Username already exists: $normalizedUsername")
        }

        val normalizedEmail = email.trimToNull()
        if (normalizedEmail != null && usersRepository.emailExistsTx(normalizedEmail)) {
            throw IllegalArgumentException("Email already exists: $normalizedEmail")
        }
    }

    private fun validateTeamAssignmentsForClub(
        role: UserRole,
        clubId: UUID,
        teamIds: List<String>
    ): List<UUID> {
        if (teamIds.isEmpty()) return emptyList()

        if (role != UserRole.ATHLETE && role != UserRole.COACH) {
            throw IllegalArgumentException("Only athletes and coaches can be assigned to teams")
        }

        val requestedTeamIds = teamIds.map { parseUuid(it, "teamId") }.distinct()
        if (role == UserRole.ATHLETE && requestedTeamIds.size != 1) {
            throw IllegalArgumentException("Athletes can only be assigned to one team")
        }

        val teams = teamsRepository.getByIdsTx(requestedTeamIds)
        val teamsById = teams.associateBy { it.id }
        val missingTeamId = requestedTeamIds.firstOrNull { it !in teamsById }
        if (missingTeamId != null) {
            throw IllegalArgumentException("Team not found: $missingTeamId")
        }

        val teamInDifferentClub = teams.firstOrNull { it.clubId != clubId }
        if (teamInDifferentClub != null) {
            throw IllegalArgumentException("Team does not belong to club: ${teamInDifferentClub.id}")
        }

        return requestedTeamIds
    }

    private fun assignTeamsToUserTx(
        userId: UUID,
        teamIds: List<UUID>,
        createdAt: Long
    ) {
        teamIds.forEach { teamId ->
            teamsRepository.addUserToTeamTx(userId, teamId, createdAt = createdAt)
        }
    }

    private fun generateTemporaryPassword(length: Int = 14): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%*"
        val random = SecureRandom()
        return buildString {
            repeat(length) {
                append(alphabet[random.nextInt(alphabet.length)])
            }
        }
    }

    private fun requireSuperAdminTx(actingUserId: UUID): com.provingground.datamodels.User {
        val actingUser = usersRepository.getByIdTx(actingUserId)
            ?: throw IllegalArgumentException("User not found")

        if (actingUser.role != UserRole.SUPERADMIN) {
            throw IllegalArgumentException("Only super admins can manage users")
        }

        return actingUser
    }

    private fun getTargetUserTx(userId: String): com.provingground.datamodels.User {
        val userUuid = parseUuid(userId, "userId")
        return usersRepository.getByIdTx(userUuid)
            ?: throw IllegalArgumentException("Target user not found")
    }

    private fun parseUuid(value: String, fieldName: String): UUID {
        return try {
            UUID.fromString(value)
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid $fieldName")
        }
    }

    private fun buildUserManagementResponse(
        userId: UUID,
        username: String,
        message: String
    ): UserManagementResponse {
        return UserManagementResponse(
            userId = userId.toString(),
            username = username,
            clubIds = clubsRepository.getClubsForUserTx(userId).map { it.id.toString() },
            teamIds = teamsRepository.getTeamsForUserTx(userId).map { it.id.toString() },
            message = message
        )
    }

    private fun String?.trimToNull(): String? =
        this?.trim()?.takeIf { it.isNotBlank() }
}
