package com.provingground.service

import com.provingground.database.repositories.ConsentsRepository
import com.provingground.database.repositories.UsersRepository
import com.provingground.database.tables.ConsentType
import com.provingground.datamodels.AcceptConsentFormsRequest
import com.provingground.datamodels.Consent
import com.provingground.datamodels.response.AcceptConsentFormsResponse
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

class ConsentsService(
    private val consentsRepository: ConsentsRepository,
    private val usersRepository: UsersRepository
) {
    suspend fun acceptConsents(
        userId: UUID,
        request: AcceptConsentFormsRequest
    ): AcceptConsentFormsResponse = newSuspendedTransaction(Dispatchers.IO) {
        if (usersRepository.getByIdTx(userId) == null) {
            throw IllegalArgumentException("User not found")
        }

        if (request.consentTypes.isEmpty()) {
            throw IllegalArgumentException("At least one consent type is required")
        }

        val acceptedTypes = mutableListOf<ConsentType>()
        val now = System.currentTimeMillis()

        request.consentTypes.distinct().forEach { consentType ->
            val existing = consentsRepository.getByUserIdAndTypeTx(
                userId = userId,
                consentType = consentType
            )

            if (existing == null) {
                consentsRepository.createTx(
                    Consent(
                        id = UUID.randomUUID(),
                        userId = userId,
                        consentType = consentType,
                        createdAt = now
                    )
                )
            }

            acceptedTypes.add(consentType)
        }

        AcceptConsentFormsResponse(
            success = true,
            acceptedConsentTypes = acceptedTypes
        )
    }
}