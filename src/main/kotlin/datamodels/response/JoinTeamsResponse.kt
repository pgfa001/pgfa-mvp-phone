package com.provingground.datamodels.response

import kotlinx.serialization.Serializable

@Serializable
data class JoinTeamsResponse(
    val success: Boolean,
    val assignments: List<JoinedTeamAssignmentResponse>
)

@Serializable
data class JoinedTeamAssignmentResponse(
    val userId: String,
    val teamIds: List<String>
)