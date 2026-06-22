package com.provingground.service

import com.provingground.JwtConfig
import com.provingground.PasswordHasher
import com.provingground.database.repositories.ClubsRepository
import com.provingground.database.repositories.ConsentsRepository
import com.provingground.database.repositories.UsersRepository
import com.provingground.database.tables.UserRole
import com.provingground.datamodels.CreateUserAccountRequest
import com.provingground.datamodels.LoginRequest
import com.provingground.datamodels.User
import com.provingground.datamodels.response.LoginResponse
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

class AuthService(
    private val usersRepository: UsersRepository,
    private val clubsRepository: ClubsRepository,
    private val consentsRepository: ConsentsRepository,
    private val subscriptionService: SubscriptionService,
    private val passwordHasher: PasswordHasher
) {
    suspend fun login(request: LoginRequest): LoginResponse? = newSuspendedTransaction(Dispatchers.IO) {
        val user = usersRepository.getByUsernameTx(request.username) ?: return@newSuspendedTransaction null

        val isValidPassword = passwordHasher.verify(
            password = request.password,
            passwordHash = user.password
        )

        if (!isValidPassword) {
            return@newSuspendedTransaction null
        }

        val token = JwtConfig.generateToken(user)
        val hasAcceptedRequiredConsents = consentsRepository.hasRequiredConsentsTx(user.id)

        return@newSuspendedTransaction LoginResponse(
            token = token,
            userId = user.id.toString(),
            username = user.username,
            role = user.role,
            clubId = getLoginClubIdTx(user),
            hasAcceptedRequiredConsents = hasAcceptedRequiredConsents,
            subscription = if (user.role == UserRole.ATHLETE) {
                subscriptionService.getEntitlementForAthleteTx(user)
            } else {
                null
            },
            athleteSubscriptions = if (user.role == UserRole.PARENT) {
                usersRepository.getChildrenForParentTx(user.id)
                    .filter { it.role == UserRole.ATHLETE }
                    .map { subscriptionService.getAthleteSubscriptionResponseTx(it) }
            } else {
                emptyList()
            }
        )
    }

    suspend fun createAccount(request: CreateUserAccountRequest): LoginResponse =
        newSuspendedTransaction(Dispatchers.IO) {
            val clubId = try {
                UUID.fromString(request.clubId)
            } catch (_: Exception) {
                throw IllegalArgumentException("Invalid clubId")
            }

            val role = try {
                UserRole.valueOf(request.role)
            } catch (_: Exception) {
                throw IllegalArgumentException("Invalid role")
            }

            if (role == UserRole.ADMIN || role == UserRole.SUPERADMIN) {
                throw IllegalArgumentException("Admin accounts must be created by a super admin")
            }

            if (usersRepository.usernameExistsTx(request.username)) {
                throw IllegalArgumentException("Username already exists")
            }

            if (usersRepository.emailExistsTx(request.email)) {
                throw IllegalArgumentException("Email already exists")
            }

            if (clubsRepository.getByIdTx(clubId) == null) {
                throw IllegalArgumentException("Club not found")
            }

            if (role != UserRole.PARENT && request.childAccounts.isNotEmpty()) {
                throw IllegalArgumentException("Only parents can create child accounts")
            }

            if (role == UserRole.ATHLETE && request.position.isNullOrBlank()) {
                throw IllegalArgumentException("Athlete accounts require a position")
            }

            if (role == UserRole.ATHLETE && request.gender.isNullOrBlank()) {
                throw IllegalArgumentException("Athlete accounts require a gender")
            }

            val now = System.currentTimeMillis()

            val primaryUser = User(
                id = UUID.randomUUID(),
                name = request.name,
                username = request.username,
                password = passwordHasher.hash(request.password),
                email = request.email,
                phone = request.phone,
                role = role,
                dob = request.dob,
                gender = if (role == UserRole.ATHLETE) request.gender?.trim() else request.gender?.trim()?.takeIf { it.isNotBlank() },
                avatarUrl = null,
                position = if (role == UserRole.ATHLETE) request.position else null,
                state = request.state.trimToNull(),
                town = request.town.trimToNull(),
                socialMediaHandle = request.socialMediaHandle.trimToNull(),
                createdAt = now
            )

            usersRepository.createTx(primaryUser)
            clubsRepository.addUserToClubTx(primaryUser.id, clubId, createdAt = now)
            if (role == UserRole.ATHLETE) {
                subscriptionService.createTrialForAthleteTx(
                    athleteUserId = primaryUser.id,
                    payerUserId = primaryUser.id,
                    now = now
                )
            }

            if (role == UserRole.PARENT) {
                request.childAccounts.forEach { childRequest ->
                    if (usersRepository.usernameExistsTx(childRequest.username)) {
                        throw IllegalArgumentException("Child username already exists: ${childRequest.username}")
                    }

                    val childNow = System.currentTimeMillis()
                    if (childRequest.gender.isNullOrBlank()) {
                        throw IllegalArgumentException("Child athlete accounts require a gender")
                    }

                    val childUser = User(
                        id = UUID.randomUUID(),
                        name = childRequest.name,
                        username = childRequest.username,
                        password = passwordHasher.hash(childRequest.password),
                        email = null,
                        phone = null,
                        role = UserRole.ATHLETE,
                        dob = childRequest.dob,
                        gender = childRequest.gender.trim(),
                        avatarUrl = null,
                        position = childRequest.position,
                        state = childRequest.state.trimToNull(),
                        town = childRequest.town.trimToNull(),
                        socialMediaHandle = childRequest.socialMediaHandle.trimToNull(),
                        createdAt = childNow
                    )

                    usersRepository.createTx(childUser)
                    clubsRepository.addUserToClubTx(childUser.id, clubId, createdAt = childNow)
                    usersRepository.addChildToParentTx(
                        parentUserId = primaryUser.id,
                        childUserId = childUser.id,
                        createdAt = childNow
                    )
                    subscriptionService.createTrialForAthleteTx(
                        athleteUserId = childUser.id,
                        payerUserId = primaryUser.id,
                        now = childNow
                    )
                }
            }

            val token = JwtConfig.generateToken(primaryUser)

            LoginResponse(
                token = token,
                userId = primaryUser.id.toString(),
                username = primaryUser.username,
                role = primaryUser.role,
                clubId = clubId.toString(),
                hasAcceptedRequiredConsents = false,
                subscription = if (primaryUser.role == UserRole.ATHLETE) {
                    subscriptionService.getEntitlementForAthleteTx(primaryUser)
                } else {
                    null
                },
                athleteSubscriptions = if (primaryUser.role == UserRole.PARENT) {
                    usersRepository.getChildrenForParentTx(primaryUser.id)
                        .filter { it.role == UserRole.ATHLETE }
                        .map { subscriptionService.getAthleteSubscriptionResponseTx(it) }
                } else {
                    emptyList()
                }
            )
        }

    private fun getLoginClubIdTx(user: User): String? {
        if (user.role == UserRole.SUPERADMIN) return null

        val clubId = if (user.role == UserRole.ADMIN) {
            clubsRepository.getClubIdsForAdminTx(user.id).firstOrNull()
                ?: clubsRepository.getClubsForUserTx(user.id).firstOrNull()?.id
        } else {
            clubsRepository.getClubsForUserTx(user.id).firstOrNull()?.id
        }

        return clubId?.toString()
    }

    private fun String?.trimToNull(): String? =
        this?.trim()?.takeIf { it.isNotBlank() }
}
