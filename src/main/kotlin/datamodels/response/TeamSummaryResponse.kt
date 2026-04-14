package com.provingground.datamodels.response

import kotlinx.serialization.Serializable

@Serializable
data class TeamSummaryResponse(
    val id: String,
    val name: String,
    val lowerAgeRange: Int,
    val upperAgeRange: Int
)

@Serializable
data class GetClubTeamsResponse(
    val teams: List<TeamSummaryResponse>
)