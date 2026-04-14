package com.provingground.datamodels.response

import kotlinx.serialization.Serializable
import com.provingground.database.tables.SubmissionValidationStatus

@Serializable
data class CreateChallengeSubmissionUploadUrlRequest(
    val teamId: String,
    val athleteId: String? = null,
    val fileName: String,
    val contentType: String
)

@Serializable
data class CreateChallengeSubmissionUploadUrlResponse(
    val uploadIntentId: String,
    val objectKey: String,
    val uploadUrl: String,
    val expiresAt: Long
)

@Serializable
data class CreateChallengeSubmissionRequest(
    val teamId: String,
    val athleteId: String? = null,
    val objectKey: String,
    val score: Int
)

@Serializable
data class CreateChallengeSubmissionResponse(
    val submissionId: String,
    val challengeId: String,
    val athleteId: String,
    val teamId: String,
    val objectKey: String,
    val score: Int,
    val validationStatus: SubmissionValidationStatus,
    val createdAt: Long
)

@Serializable
data class GetSubmissionVideoUrlResponse(
    val submissionId: String,
    val videoUrl: String,
    val expiresAt: Long
)