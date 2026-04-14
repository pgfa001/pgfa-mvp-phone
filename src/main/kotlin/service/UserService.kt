package com.provingground.service

import com.provingground.database.repositories.UsersRepository
import com.provingground.database.tables.UserRole
import com.provingground.datamodels.response.ChildUserSummaryResponse
import com.provingground.datamodels.response.UserDetailsResponse
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

class UserService(
    private val usersRepository: UsersRepository
) {
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
            email = user.email,
            phone = user.phone,
            avatarUrl = user.avatarUrl,
            position = user.position,
            children = children
        )
    }
}