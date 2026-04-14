package com.provingground.database.repositories

import com.provingground.database.dbQuery
import com.provingground.database.tables.ConsentType
import com.provingground.database.tables.ConsentsTable
import com.provingground.database.toConsent
import com.provingground.datamodels.Consent
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.UUID

class ConsentsRepository {

    fun createTx(consent: Consent): Consent {
        ConsentsTable.insert {
            it[id] = consent.id
            it[userId] = consent.userId
            it[consentType] = consent.consentType
            it[createdAt] = consent.createdAt
        }
        return consent
    }

    suspend fun create(consent: Consent): Consent = dbQuery {
        createTx(consent)
    }

    fun getByIdTx(id: UUID): Consent? {
        return ConsentsTable
            .selectAll()
            .where { ConsentsTable.id eq id }
            .singleOrNull()
            ?.toConsent()
    }

    suspend fun getById(id: UUID): Consent? = dbQuery {
        getByIdTx(id)
    }

    fun getByUserIdTx(userId: UUID): List<Consent> {
        return ConsentsTable
            .selectAll()
            .where { ConsentsTable.userId eq userId }
            .map { it.toConsent() }
    }

    suspend fun getByUserId(userId: UUID): List<Consent> = dbQuery {
        getByUserIdTx(userId)
    }

    fun hasRequiredConsentsTx(userId: UUID): Boolean {
        val consentTypes = ConsentsTable
            .select(ConsentsTable.consentType)
            .where { ConsentsTable.userId eq userId }
            .map { it[ConsentsTable.consentType] }
            .toSet()

        return consentTypes.contains(ConsentType.MEDIA_RELEASE) &&
                consentTypes.contains(ConsentType.LIABILITY) &&
                consentTypes.contains(ConsentType.TERMS_AND_CONDITIONS)
    }

    fun getByUserIdAndTypeTx(userId: UUID, consentType: ConsentType): Consent? {
        return ConsentsTable
            .selectAll()
            .where {
                (ConsentsTable.userId eq userId) and
                        (ConsentsTable.consentType eq consentType)
            }
            .singleOrNull()
            ?.toConsent()
    }

    suspend fun getByUserIdAndType(userId: UUID, consentType: ConsentType): Consent? = dbQuery {
        getByUserIdAndTypeTx(userId, consentType)
    }

    fun updateTx(id: UUID, consent: Consent): Boolean {
        return ConsentsTable.update({ ConsentsTable.id eq id }) {
            it[userId] = consent.userId
            it[consentType] = consent.consentType
        } > 0
    }

    suspend fun update(id: UUID, consent: Consent): Boolean = dbQuery {
        updateTx(id, consent)
    }

    fun deleteTx(id: UUID): Boolean {
        return ConsentsTable.deleteWhere { ConsentsTable.id eq id } > 0
    }

    suspend fun delete(id: UUID): Boolean = dbQuery {
        deleteTx(id)
    }
}