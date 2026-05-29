package com.provingground.datamodels.response

import com.provingground.database.tables.SubmissionValidationStatus
import com.provingground.database.tables.ChallengeScoringType
import com.provingground.database.tables.UserRole
import kotlinx.serialization.Serializable

@Serializable
enum class HomeStatsPeriod {
    THIS_WEEK,
    ALL_TIME
}

@Serializable
data class HomeScreenResponse(
    val userId: String,
    val role: UserRole,
    val cards: List<HomeChallengeCardResponse>
)

@Serializable
data class PreviousChallengesResponse(
    val challenges: List<PreviousChallengeResponse>
)

@Serializable
data class PreviousChallengeResponse(
    val name: String,
    val completed: Boolean,
    val score: Int? = null
)

@Serializable
data class HomeRankSummaryResponse(
    val athleteId: String,
    val teamRank: HomeTeamRankResponse,
    val clubRank: HomeClubRankResponse,
    val participatedChallengeCount: Int
)

@Serializable
data class HomeStatsResponse(
    val period: HomeStatsPeriod,
    val athleteId: String,
    val totalChallengesCompleted: Int? = null,
    val currentStreak: Int? = null,
    val totalSubmissions: Int? = null,
    val bestClubRank: Int? = null,
    val thisWeek: HomeThisWeekStatsResponse? = null
)

@Serializable
data class HomeThisWeekStatsResponse(
    val challengeId: String,
    val challengeTitle: String,
    val scoringType: ChallengeScoringType,
    val unlocked: Boolean,
    val requiredSubmissions: Int,
    val totalAttempts: Int,
    val remainingSubmissions: Int,
    val averageScore: Double? = null,
    val bestScore: Int? = null,
    val bestScoreSubmissionId: String? = null,
    val improvementPercentage: Double? = null,
    val clubAverageScore: Double? = null,
    val clubAverageAttempts: Double
)

@Serializable
data class HomeTeamRankResponse(
    val rank: Int? = null,
    val averageRank: Double? = null,
    val teamId: String,
    val teamName: String
)

@Serializable
data class HomeClubRankResponse(
    val rank: Int? = null,
    val averageRank: Double? = null,
    val athleteCount: Int
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
    val scoringType: ChallengeScoringType,
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
