package com.provingground.datamodels.response

import kotlinx.serialization.Serializable

@Serializable
data class CreateChallengeDemoUploadUrlRequest(
    val fileName: String,
    val contentType: String
)

@Serializable
data class CreateChallengeDemoUploadUrlResponse(
    val uploadIntentId: String,
    val objectKey: String,
    val uploadUrl: String,
    val expiresAt: Long
)

@Serializable
data class GetChallengeDemoVideoUrlResponse(
    val challengeId: String,
    val videoUrl: String,
    val expiresAt: Long
)