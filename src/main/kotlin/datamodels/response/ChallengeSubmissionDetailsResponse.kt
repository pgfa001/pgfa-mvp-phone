package com.provingground.datamodels.response

import com.provingground.database.tables.SubmissionValidationStatus
import kotlinx.serialization.Serializable

@Serializable
data class ChallengeSubmissionDetailsResponse(
    val submissionId: String,
    val challengeId: String,
    val challengeTitle: String,
    val athleteId: String,
    val athleteName: String,
    val teamId: String,
    val teamName: String,
    val score: Int,
    val validationStatus: SubmissionValidationStatus,
    val createdAt: Long,
    val videoUrl: String,
    val videoUrlExpiresAt: Long
)