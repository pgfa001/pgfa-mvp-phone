package com.provingground.datamodels.response

import kotlinx.serialization.Serializable

@Serializable
data class ChallengeDetailsResponse(
    val challenge: ChallengeSummaryResponse,
    val teamViews: List<ChallengeTeamViewResponse>
)

@Serializable
data class ChallengeTeamViewResponse(
    val athleteId: String? = null,
    val athleteName: String? = null,
    val teamId: String,
    val teamName: String,
    val leaderboard: List<LeaderboardEntryResponse>,
    val hasSubmitted: Boolean,
    val submissionCount: Int
)