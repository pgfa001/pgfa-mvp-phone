package com.provingground.datamodels.response

import com.provingground.database.tables.SubmissionValidationStatus
import kotlinx.serialization.Serializable

@Serializable
data class GetMyChallengeSubmissionsResponse(
    val challengeId: String,
    val athleteId: String? = null,
    val athleteName: String? = null,
    val teamId: String,
    val teamName: String,
    val submissions: List<ChallengeSubmissionResponse>
)

@Serializable
data class ChallengeSubmissionResponse(
    val id: String,
    val videoUrl: String,
    val score: Int,
    val validationStatus: SubmissionValidationStatus,
    val createdAt: Long
)