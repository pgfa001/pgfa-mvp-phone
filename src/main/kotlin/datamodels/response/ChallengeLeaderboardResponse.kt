package com.provingground.datamodels.response

import com.provingground.database.tables.SubmissionValidationStatus
import kotlinx.serialization.Serializable

@Serializable
enum class LeaderboardScope {
    CLUB,
    TEAM
}

@Serializable
data class CurrentChallengeLeaderboardResponse(
    val challenge: ChallengeSummaryResponse,
    val scope: LeaderboardScope,
    val clubId: String,
    val clubName: String,
    val teamId: String? = null,
    val teamName: String? = null,
    val gender: String? = null,
    val entries: List<FullLeaderboardEntryResponse>
)

@Serializable
data class FullLeaderboardEntryResponse(
    val rank: Int,
    val athleteId: String,
    val athleteName: String,
    val teamId: String,
    val teamName: String,
    val avatarUrl: String? = null,
    val attempts: Int,
    val bestScore: Int,
    val bestScoreSubmissionId: String,
    val validationStatus: SubmissionValidationStatus,
    val submittedAt: Long
)

@Serializable
data class MostImprovedAthleteResponse(
    val challengeId: String,
    val teamId: String,
    val athlete: MostImprovedAthleteEntryResponse? = null
)

@Serializable
data class MostImprovedAthleteEntryResponse(
    val athleteId: String,
    val athleteName: String,
    val avatarUrl: String? = null,
    val attemptCount: Int,
    val firstSubmissionId: String,
    val firstScore: Int,
    val firstSubmittedAt: Long,
    val bestSubmissionId: String,
    val bestScore: Int,
    val bestSubmittedAt: Long,
    val improvement: Int,
    val improvementPercentage: Double? = null
)
