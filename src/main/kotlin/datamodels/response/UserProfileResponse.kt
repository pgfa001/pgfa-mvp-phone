package com.provingground.datamodels.response

import com.provingground.database.tables.UserRole
import kotlinx.serialization.Serializable

@Serializable
data class UserProfileResponse(
    val userId: String,
    val name: String,
    val role: UserRole,
    val age: Int,
    val athleteProfile: AthleteProfileResponse? = null
)

@Serializable
data class AthleteProfileResponse(
    val teamName: String? = null,
    val challengesCompleted: Int,
    val currentChallengeCompletionStreak: Int,
    val recentActivity: List<RecentActivityItemResponse>
)

@Serializable
data class RecentActivityItemResponse(
    val challengeId: String,
    val challengeTitle: String,
    val completedAt: Long,
    val activityText: String
)