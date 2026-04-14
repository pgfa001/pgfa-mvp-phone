package com.provingground.datamodels.response

import kotlinx.serialization.Serializable

@Serializable
data class ChallengeCmsResponse(
    val id: String,
    val title: String,
    val description: String,
    val demoVideoObjectKey: String? = null,
    val scoringType: String,
    val difficulty: Int,
    val startTime: Long,
    val endTime: Long,
    val createdBy: String,
    val createdAt: Long,
    val clubIds: List<String>
)

@Serializable
data class GetChallengesCmsResponse(
    val challenges: List<ChallengeCmsResponse>
)