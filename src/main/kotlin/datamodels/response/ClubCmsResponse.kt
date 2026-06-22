package com.provingground.datamodels.response

import com.provingground.database.tables.SubscriptionType
import com.provingground.database.tables.UserRole
import kotlinx.serialization.Serializable

@Serializable
data class ClubCmsResponse(
    val id: String,
    val name: String,
    val logoObjectKey: String? = null,
    val accessCode: String,
    val primaryColor: String,
    val accentColor: String,
    val subscriptionType: SubscriptionType,
    val createdAt: Long
)

@Serializable
data class GetClubsResponse(
    val clubs: List<ClubCmsResponse>
)

@Serializable
data class CreateClubRequest(
    val name: String,
    val logoObjectKey: String? = null,
    val accessCode: String,
    val primaryColor: String,
    val accentColor: String,
    val subscriptionType: SubscriptionType
)

@Serializable
data class UpdateClubRequest(
    val name: String,
    val logoObjectKey: String? = null,
    val accessCode: String,
    val primaryColor: String,
    val accentColor: String,
    val subscriptionType: SubscriptionType
)

@Serializable
data class CreateClubLogoUploadUrlRequest(
    val fileName: String,
    val contentType: String
)

@Serializable
data class CreateClubLogoUploadUrlResponse(
    val uploadIntentId: String,
    val objectKey: String,
    val uploadUrl: String,
    val expiresAt: Long
)

@Serializable
data class GetClubLogoUrlResponse(
    val clubId: String,
    val logoUrl: String,
    val expiresAt: Long
)

@Serializable
data class CreateClubAdminRequest(
    val name: String,
    val username: String,
    val password: String,
    val email: String,
    val phone: String,
    val dob: String,
    val state: String? = null,
    val town: String? = null,
    val socialMediaHandle: String? = null
)

@Serializable
data class ClubAdminResponse(
    val id: String,
    val clubId: String,
    val name: String,
    val username: String,
    val email: String,
    val phone: String,
    val role: UserRole,
    val state: String? = null,
    val town: String? = null,
    val socialMediaHandle: String? = null,
    val createdAt: Long
)
