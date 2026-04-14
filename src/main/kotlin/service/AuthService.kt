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
            hasAcceptedRequiredConsents = hasAcceptedRequiredConsents,
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
                avatarUrl = null,
                position = if (role == UserRole.ATHLETE) request.position else null,
                createdAt = now
            )

            usersRepository.createTx(primaryUser)
            clubsRepository.addUserToClubTx(primaryUser.id, clubId, createdAt = now)

            if (role == UserRole.PARENT) {
                request.childAccounts.forEach { childRequest ->
                    if (usersRepository.usernameExistsTx(childRequest.username)) {
                        throw IllegalArgumentException("Child username already exists: ${childRequest.username}")
                    }

                    val childNow = System.currentTimeMillis()
                    val childUser = User(
                        id = UUID.randomUUID(),
                        name = childRequest.name,
                        username = childRequest.username,
                        password = passwordHasher.hash(childRequest.password),
                        email = null,
                        phone = null,
                        role = UserRole.ATHLETE,
                        dob = childRequest.dob,
                        avatarUrl = null,
                        position = childRequest.position,
                        createdAt = childNow
                    )

                    usersRepository.createTx(childUser)
                    clubsRepository.addUserToClubTx(childUser.id, clubId, createdAt = childNow)
                    usersRepository.addChildToParentTx(
                        parentUserId = primaryUser.id,
                        childUserId = childUser.id,
                        createdAt = childNow
                    )
                }
            }

            val token = JwtConfig.generateToken(primaryUser)

            LoginResponse(
                token = token,
                userId = primaryUser.id.toString(),
                username = primaryUser.username,
                role = primaryUser.role,
                hasAcceptedRequiredConsents = false
            )
        }
}