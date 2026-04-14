package com.provingground.datamodels.response

import com.provingground.database.tables.SubmissionValidationStatus
import kotlinx.serialization.Serializable

@Serializable
data class VerifyChallengeSubmissionResponse(
    val submissionId: String,
    val validationStatus: SubmissionValidationStatus
)