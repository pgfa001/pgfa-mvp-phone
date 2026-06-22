package com.provingground.service

import com.provingground.database.repositories.ConsentsRepository
import com.provingground.database.repositories.UsersRepository
import com.provingground.database.tables.ConsentType
import com.provingground.datamodels.AcceptConsentFormsRequest
import com.provingground.datamodels.Consent
import com.provingground.datamodels.response.AcceptConsentFormsResponse
import com.provingground.datamodels.response.RequiredConsentFormResponse
import com.provingground.datamodels.response.RequiredConsentFormsResponse
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

class ConsentsService(
    private val consentsRepository: ConsentsRepository,
    private val usersRepository: UsersRepository
) {
    fun getRequiredConsents(): RequiredConsentFormsResponse {
        return RequiredConsentFormsResponse(
            consents = listOf(
                RequiredConsentFormResponse(
                    consentType = REQUIRED_CONSENT_TYPE,
                    title = "Terms of Service",
                    url = TERMS_OF_SERVICE_URL,
                    required = true
                )
            )
        )
    }

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

        val requestedTypes = request.consentTypes.distinct()
        if (requestedTypes.any { it != REQUIRED_CONSENT_TYPE }) {
            throw IllegalArgumentException("Only TERMS_OF_SERVICE consent is required")
        }

        val acceptedTypes = mutableListOf<ConsentType>()
        val now = System.currentTimeMillis()

        requestedTypes.forEach { consentType ->
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

    companion object {
        private val REQUIRED_CONSENT_TYPE = ConsentType.TERMS_OF_SERVICE
        const val TERMS_OF_SERVICE_URL = "https://pgfa-mvp-cms-q89v.onrender.com/terms-of-service"
    }
}
