package com.provingground.service

import com.provingground.database.repositories.ClubsRepository
import com.provingground.database.repositories.UsersRepository
import com.provingground.database.tables.UserRole
import com.provingground.datamodels.ClubSummaryResponse
import com.provingground.datamodels.GetClubsResponse
import com.provingground.datamodels.response.ClubDetailsResponse
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

class ClubsService(
    private val clubsRepository: ClubsRepository,
    private val usersRepository: UsersRepository,
) {
    suspend fun getClubDetailsByAccessCode(accessCode: String): ClubDetailsResponse? {
        val club = clubsRepository.getByAccessCode(accessCode) ?: return null

        return ClubDetailsResponse(
            id = club.id.toString(),
            name = club.name,
            logoUrl = club.logoUrl,
            primaryColor = club.primaryColor,
            accentColor = club.accentColor,
            subscriptionType = club.subscriptionType.name
        )
    }

    suspend fun getAllClubs(actingUserId: UUID): GetClubsResponse =
        newSuspendedTransaction(Dispatchers.IO) {
            val actingUser = usersRepository.getByIdTx(actingUserId)
                ?: throw IllegalArgumentException("User not found")

            if (actingUser.role != UserRole.ADMIN) {
                throw IllegalArgumentException("Only admins can view clubs")
            }

            val clubs = clubsRepository.getAllTx()

            GetClubsResponse(
                clubs = clubs.map { club ->
                    ClubSummaryResponse(
                        id = club.id.toString(),
                        name = club.name,
                        logoUrl = club.logoUrl,
                        accessCode = club.accessCode,
                        primaryColor = club.primaryColor,
                        accentColor = club.accentColor,
                        subscriptionType = club.subscriptionType,
                        createdAt = club.createdAt
                    )
                }
            )
        }
}