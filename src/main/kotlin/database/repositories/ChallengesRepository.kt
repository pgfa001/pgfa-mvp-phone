package com.provingground.database.repositories

import com.provingground.database.dbQuery
import com.provingground.database.tables.ChallengeDemoUploadIntentsTable
import com.provingground.database.tables.ChallengeSubmissionsTable
import com.provingground.database.tables.ChallengeToClubsTable
import com.provingground.database.tables.ChallengeUploadIntentsTable
import com.provingground.database.tables.ChallengesTable
import com.provingground.database.tables.SubmissionValidationStatus
import com.provingground.database.tables.TeamsToUsersTable
import com.provingground.database.tables.UsersTable
import com.provingground.database.toChallenge
import com.provingground.database.toChallengeDemoUploadIntent
import com.provingground.database.toChallengeSubmission
import com.provingground.database.toChallengeUploadIntent
import com.provingground.database.toUser
import com.provingground.datamodels.Challenge
import com.provingground.datamodels.ChallengeDemoUploadIntent
import com.provingground.datamodels.ChallengeSubmission
import com.provingground.datamodels.ChallengeUploadIntent
import com.provingground.datamodels.UpdateChallengeCmsRequest
import com.provingground.datamodels.User
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.Date
import java.util.UUID
import kotlin.time.Instant

class ChallengesRepository {

    fun createTx(challenge: Challenge): Challenge {
        ChallengesTable.insert {
            it[id] = challenge.id
            it[title] = challenge.title
            it[description] = challenge.description
            it[demoVideoObjectKey] = challenge.demoVideoObjectKey
            it[scoringType] = challenge.scoringType
            it[difficulty] = challenge.difficulty
            it[startTime] = challenge.startTime
            it[endTime] = challenge.endTime
            it[createdBy] = challenge.createdBy
            it[createdAt] = challenge.createdAt
        }
        return challenge
    }

    suspend fun create(challenge: Challenge): Challenge = dbQuery {
        createTx(challenge)
    }

    fun getByIdTx(id: UUID): Challenge? {
        return ChallengesTable
                .selectAll()
                .where { ChallengesTable.id eq id }
                .singleOrNull()?.toChallenge()
    }

    suspend fun getById(id: UUID): Challenge? = dbQuery {
        getByIdTx(id)
    }

    fun getAllTx(): List<Challenge> {
        return ChallengesTable.selectAll().map { it.toChallenge() }
    }

    suspend fun getAll(): List<Challenge> = dbQuery {
        getAllTx()
    }

    fun getClubIdsForChallengeTx(challengeId: UUID): List<UUID> {
        return ChallengeToClubsTable
            .selectAll()
            .where { ChallengeToClubsTable.challengeId eq challengeId }
            .map { it[ChallengeToClubsTable.clubId] }
    }

    fun replaceChallengeClubsTx(challengeId: UUID, clubIds: List<UUID>) {
        ChallengeToClubsTable.deleteWhere { ChallengeToClubsTable.challengeId eq challengeId }

        val now = System.currentTimeMillis()

        clubIds.distinct().forEach { clubId ->
            ChallengeToClubsTable.insert {
                it[id] = UUID.randomUUID()
                it[ChallengeToClubsTable.challengeId] = challengeId
                it[ChallengeToClubsTable.clubId] = clubId
                it[createdAt] = now
            }
        }
    }

    fun createDemoUploadIntentTx(intent: ChallengeDemoUploadIntent): ChallengeDemoUploadIntent {
        ChallengeDemoUploadIntentsTable.insert {
            it[id] = intent.id
            it[actingUserId] = intent.actingUserId
            it[objectKey] = intent.objectKey
            it[originalFileName] = intent.originalFileName
            it[contentType] = intent.contentType
            it[expiresAt] = intent.expiresAt
            it[consumedAt] = intent.consumedAt
            it[createdAt] = intent.createdAt
        }
        return intent
    }

    fun getDemoUploadIntentByObjectKeyTx(objectKey: String): ChallengeDemoUploadIntent? {
        return ChallengeDemoUploadIntentsTable
            .selectAll()
            .where { ChallengeDemoUploadIntentsTable.objectKey eq objectKey }
            .singleOrNull()
            ?.toChallengeDemoUploadIntent()
    }

    fun markDemoUploadIntentConsumedTx(intentId: UUID, consumedAt: Long): Boolean {
        return ChallengeDemoUploadIntentsTable.update({ ChallengeDemoUploadIntentsTable.id eq intentId }) {
            it[ChallengeDemoUploadIntentsTable.consumedAt] = consumedAt
        } > 0
    }

    fun updateTx(challengeId: UUID, request: UpdateChallengeCmsRequest): Boolean {
        return ChallengesTable.update({ ChallengesTable.id eq challengeId }) {
            it[title] = request.title
            it[description] = request.description
            it[demoVideoObjectKey] = request.demoVideoObjectKey
            it[scoringType] = request.scoringType
            it[difficulty] = request.difficulty
            it[startTime] = request.startTime
            it[endTime] = request.endTime
        } > 0
    }

    fun deleteTx(id: UUID): Boolean {
        return ChallengesTable.deleteWhere { ChallengesTable.id eq id } > 0
    }

    suspend fun delete(id: UUID): Boolean = dbQuery {
        deleteTx(id)
    }

    fun createSubmissionTx(submission: ChallengeSubmission): ChallengeSubmission {
        ChallengeSubmissionsTable.insert {
            it[id] = submission.id
            it[userId] = submission.userId
            it[challengeId] = submission.challengeId
            it[teamId] = submission.teamId
            it[videoObjectKey] = submission.videoObjectKey
            it[score] = submission.score
            it[validationStatus] = submission.validationStatus
            it[createdAt] = submission.createdAt
        }
        return submission
    }

    suspend fun createSubmission(submission: ChallengeSubmission): ChallengeSubmission = dbQuery {
        createSubmissionTx(submission)
    }

    fun getSubmissionByIdTx(id: UUID): ChallengeSubmission? {
        return ChallengeSubmissionsTable
                .selectAll()
                .where { ChallengeSubmissionsTable.id eq id }
                .singleOrNull()?.toChallengeSubmission()
    }

    suspend fun getSubmissionById(id: UUID): ChallengeSubmission? = dbQuery {
        getSubmissionByIdTx(id)
    }

    fun getSubmissionsByChallengeIdTx(challengeId: UUID): List<ChallengeSubmission> {
        return ChallengeSubmissionsTable
            .selectAll()
            .where { ChallengeSubmissionsTable.challengeId eq challengeId }
            .map { it.toChallengeSubmission() }
            .sortedByDescending { it.createdAt }
    }

    suspend fun getSubmissionsByChallengeId(challengeId: UUID): List<ChallengeSubmission> = dbQuery {
        getSubmissionsByChallengeIdTx(challengeId)
    }

    fun getSubmissionsByUserAndChallengeTx(userId: UUID, challengeId: UUID): List<ChallengeSubmission> {
        return ChallengeSubmissionsTable
            .selectAll()
            .where {
                (ChallengeSubmissionsTable.userId eq userId) and
                        (ChallengeSubmissionsTable.challengeId eq challengeId)
            }
            .map { it.toChallengeSubmission() }
    }

    suspend fun getSubmissionsByUserAndChallenge(userId: UUID, challengeId: UUID): List<ChallengeSubmission> = dbQuery {
        getSubmissionsByUserAndChallengeTx(userId, challengeId)
    }

    fun updateSubmissionTx(id: UUID, submission: ChallengeSubmission): Boolean {
        return ChallengeSubmissionsTable.update({ ChallengeSubmissionsTable.id eq id }) {
            it[userId] = submission.userId
            it[challengeId] = submission.challengeId
            it[teamId] = submission.teamId
            it[videoObjectKey] = submission.videoObjectKey
            it[score] = submission.score
            it[validationStatus] = submission.validationStatus
            it[validatedBy] = submission.validatedBy
            it[validatedAt] = submission.validatedAt
        } > 0
    }

    suspend fun updateSubmission(id: UUID, submission: ChallengeSubmission): Boolean = dbQuery {
        updateSubmissionTx(id, submission)
    }

    fun updateSubmissionValidationStatusTx(
        id: UUID,
        validationStatus: SubmissionValidationStatus,
        validatingUserId: UUID,
    ): Boolean {
        return ChallengeSubmissionsTable.update({ ChallengeSubmissionsTable.id eq id }) {
            it[ChallengeSubmissionsTable.validationStatus] = validationStatus
            it[validatedBy] = validatingUserId
            it[validatedAt] = Date().time
        } > 0
    }

    suspend fun updateSubmissionValidationStatus(
        id: UUID,
        validationStatus: SubmissionValidationStatus,
        validatingUserId: UUID,
    ): Boolean = dbQuery {
        updateSubmissionValidationStatusTx(id, validationStatus, validatingUserId)
    }

    fun deleteSubmissionTx(id: UUID): Boolean {
        return ChallengeSubmissionsTable.deleteWhere { ChallengeSubmissionsTable.id eq id } > 0
    }

    suspend fun deleteSubmission(id: UUID): Boolean = dbQuery {
        deleteSubmissionTx(id)
    }

    fun isChallengeAvailableForClubTx(
        challengeId: UUID,
        clubId: UUID
    ): Boolean {
        return ChallengeToClubsTable
            .selectAll()
            .where {
                (ChallengeToClubsTable.challengeId eq challengeId) and
                        (ChallengeToClubsTable.clubId eq clubId)
            }
            .any()
    }

    fun getActiveChallengesForClubTx(
        clubId: UUID,
        now: Long = System.currentTimeMillis()
    ): List<Challenge> {
        return (ChallengesTable innerJoin ChallengeToClubsTable)
            .selectAll()
            .where {
                (ChallengeToClubsTable.clubId eq clubId) and
                        (ChallengesTable.startTime lessEq now) and
                        (ChallengesTable.endTime greaterEq now)
            }
            .map { it.toChallenge() }
            .distinctBy { it.id }
            .sortedByDescending { it.startTime }
    }

    fun getCurrentChallengeForClubTx(
        clubId: UUID,
        now: Long = System.currentTimeMillis()
    ): Challenge? {
        return getActiveChallengesForClubTx(clubId, now)
            .maxByOrNull { it.startTime }
    }

    fun getBestSubmissionsForChallengeAndTeamTx(
        challengeId: UUID,
        teamId: UUID
    ): List<Pair<ChallengeSubmission, User>> {
        return ChallengeSubmissionsTable
            .selectAll()
            .where {
                (ChallengeSubmissionsTable.challengeId eq challengeId) and
                        (ChallengeSubmissionsTable.teamId eq teamId)
            }
            .map { row -> row.toChallengeSubmission() to row.toUser() }
            .groupBy { (submission, _) -> submission.userId }
            .values
            .map { submissionsForUser ->
                submissionsForUser.maxWithOrNull(
                    compareBy<Pair<ChallengeSubmission, User>> { it.first.score }
                        .thenBy { it.first.createdAt }
                )!!
            }
    }

    fun getAllSubmissionsForChallengeAndTeamTx(
        challengeId: UUID,
        teamId: UUID
    ): List<ChallengeSubmission> {
        return (ChallengeSubmissionsTable innerJoin TeamsToUsersTable)
            .selectAll()
            .where {
                (ChallengeSubmissionsTable.challengeId eq challengeId) and
                        (ChallengeSubmissionsTable.teamId eq teamId)
            }
            .map { it.toChallengeSubmission() }
    }

    fun createUploadIntentTx(intent: ChallengeUploadIntent): ChallengeUploadIntent {
        ChallengeUploadIntentsTable.insert {
            it[id] = intent.id
            it[actingUserId] = intent.actingUserId
            it[athleteUserId] = intent.athleteUserId
            it[challengeId] = intent.challengeId
            it[teamId] = intent.teamId
            it[objectKey] = intent.objectKey
            it[originalFileName] = intent.originalFileName
            it[contentType] = intent.contentType
            it[expiresAt] = intent.expiresAt
            it[consumedAt] = intent.consumedAt
            it[createdAt] = intent.createdAt
        }
        return intent
    }

    fun getUploadIntentByObjectKeyTx(objectKey: String): ChallengeUploadIntent? {
        return ChallengeUploadIntentsTable
            .selectAll()
            .where { ChallengeUploadIntentsTable.objectKey eq objectKey }
            .singleOrNull()
            ?.toChallengeUploadIntent()
    }

    fun markUploadIntentConsumedTx(intentId: UUID, consumedAt: Long): Boolean {
        return ChallengeUploadIntentsTable.update({ ChallengeUploadIntentsTable.id eq intentId }) {
            it[ChallengeUploadIntentsTable.consumedAt] = consumedAt
        } > 0
    }
}