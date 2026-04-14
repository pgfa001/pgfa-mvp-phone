package com.provingground.datamodels.response

import com.provingground.database.tables.SubmissionValidationStatus
import com.provingground.database.tables.UserRole
import kotlinx.serialization.Serializable

@Serializable
data class HomeScreenResponse(
    val userId: String,
    val role: UserRole,
    val cards: List<HomeChallengeCardResponse>
)

@Serializable
data class HomeChallengeCardResponse(
    val athleteId: String? = null,
    val athleteName: String? = null,
    val teamId: String,
    val teamName: String,
    val challenge: ChallengeSummaryResponse?,
    val leaderboard: List<LeaderboardEntryResponse>,
    val submissionSummary: SubmissionSummaryResponse?,
    val teamStats: TeamChallengeStatsResponse?
)

@Serializable
data class ChallengeSummaryResponse(
    val id: String,
    val title: String,
    val description: String,
    val demoVideoUrl: String? = null,
    val scoringType: String,
    val difficulty: Int,
    val startTime: Long,
    val endTime: Long
)

@Serializable
data class LeaderboardEntryResponse(
    val rank: Int,
    val submissionId: String,
    val userId: String,
    val userName: String,
    val avatarUrl: String? = null,
    val score: Int,
    val validationStatus: SubmissionValidationStatus
)

@Serializable
data class SubmissionSummaryResponse(
    val hasSubmitted: Boolean,
    val submissionId: String? = null,
    val score: Int? = null,
    val validationStatus: SubmissionValidationStatus? = null,
    val submittedAt: Long? = null
)

@Serializable
data class TeamChallengeStatsResponse(
    val teamAthleteCount: Int,
    val submittedAthleteCount: Int
)