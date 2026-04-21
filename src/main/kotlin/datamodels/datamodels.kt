package com.provingground.datamodels

import com.provingground.database.tables.ConsentType
import com.provingground.database.tables.SubscriptionType
import com.provingground.database.tables.UserRole
import com.provingground.database.tables.SubmissionValidationStatus
import com.provingground.datamodels.response.ClubCmsResponse
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlin.String

data class Club(
    val id: UUID,
    val name: String,
    val logoUrl: String,
    val accessCode: String,
    val primaryColor: String,
    val accentColor: String,
    val subscriptionType: SubscriptionType,
    val createdAt: Long
)

fun Club.toCmsResponse(): ClubCmsResponse {
    return ClubCmsResponse(
        id = id.toString(),
        name = name,
        logoObjectKey = logoUrl,
        accessCode = accessCode,
        primaryColor = primaryColor,
        accentColor = accessCode,
        subscriptionType = subscriptionType,
        createdAt = createdAt,
    )
}

data class ClubLogoUploadIntent(
    val id: UUID,
    val actingUserId: UUID,
    val objectKey: String,
    val originalFileName: String,
    val contentType: String,
    val expiresAt: Long,
    val consumedAt: Long?,
    val createdAt: Long
)

data class Team(
    val id: UUID,
    val name: String,
    val clubId: UUID,
    val lowerAgeRange: Int,
    val upperAgeRange: Int,
    val createdAt: Long
)

data class User(
    val id: UUID,
    val name: String,
    val username: String,
    val password: String,
    val email: String?,
    val phone: String?,
    val role: UserRole,
    val dob: String,
    val avatarUrl: String?,
    val position: String?,
    val createdAt: Long
)

data class Challenge(
    val id: UUID,
    val title: String,
    val description: String,
    val demoVideoObjectKey: String?,
    val scoringType: String,
    val difficulty: Int,
    val startTime: Long,
    val endTime: Long,
    val createdBy: UUID,
    val createdAt: Long
)

data class ChallengeClub(
    val id: UUID,
    val challengeId: UUID,
    val clubId: UUID,
    val createdAt: Long
)

data class ChallengeDemoUploadIntent(
    val id: UUID,
    val actingUserId: UUID,
    val objectKey: String,
    val originalFileName: String,
    val contentType: String,
    val expiresAt: Long,
    val consumedAt: Long?,
    val createdAt: Long
)

data class ChallengeSubmission(
    val id: UUID,
    val userId: UUID,
    val challengeId: UUID,
    val teamId: UUID,
    val videoObjectKey: String,
    val score: Int,
    val validationStatus: SubmissionValidationStatus,
    val validatedBy: UUID?,
    val validatedAt: Long?,
    val createdAt: Long
)

data class Consent(
    val id: UUID,
    val userId: UUID,
    val consentType: ConsentType,
    val createdAt: Long
)

data class ParentChildRelationship(
    val id: UUID,
    val parentUserId: UUID,
    val childUserId: UUID,
    val createdAt: Long
)

data class ChallengeUploadIntent(
    val id: UUID,
    val actingUserId: UUID,
    val athleteUserId: UUID,
    val challengeId: UUID,
    val teamId: UUID,
    val objectKey: String,
    val originalFileName: String,
    val contentType: String,
    val expiresAt: Long,
    val consumedAt: Long?,
    val createdAt: Long
)

@Serializable
data class ClubSummaryResponse(
    val id: String,
    val name: String,
    val logoUrl: String,
    val accessCode: String,
    val primaryColor: String,
    val accentColor: String,
    val subscriptionType: SubscriptionType,
    val createdAt: Long
)

@Serializable
data class GetClubsResponse(
    val clubs: List<ClubSummaryResponse>
)

@Serializable
data class TeamResponse(
    val id: String,
    val name: String,
    val clubId: String,
    val lowerAgeRange: Int,
    val upperAgeRange: Int,
    val createdAt: Long
)

@Serializable
data class GetTeamsResponse(
    val teams: List<TeamResponse>
)

@Serializable
data class CreateTeamRequest(
    val name: String,
    val clubId: String,
    val lowerAgeRange: Int,
    val upperAgeRange: Int
)

@Serializable
data class UpdateTeamRequest(
    val name: String,
    val clubId: String,
    val lowerAgeRange: Int,
    val upperAgeRange: Int
)

@Serializable
data class CreateChallengeCmsRequest(
    val title: String,
    val description: String,
    val demoVideoObjectKey: String? = null,
    val scoringType: String,
    val difficulty: Int,
    val startTime: Long,
    val endTime: Long,
    val clubIds: List<String>
)

@Serializable
data class UpdateChallengeCmsRequest(
    val title: String,
    val description: String,
    val demoVideoObjectKey: String? = null,
    val scoringType: String,
    val difficulty: Int,
    val startTime: Long,
    val endTime: Long,
    val clubIds: List<String>
)