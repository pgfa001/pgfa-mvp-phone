package com.provingground.datamodels.response

import com.provingground.database.tables.UserRole
import kotlinx.serialization.Serializable

@Serializable
data class UserDetailsResponse(
    val id: String,
    val name: String,
    val username: String,
    val role: UserRole,
    val dob: String,
    val gender: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val avatarUrl: String? = null,
    val position: String? = null,
    val state: String? = null,
    val town: String? = null,
    val socialMediaHandle: String? = null,
    val subscription: SubscriptionEntitlementResponse? = null,
    val children: List<ChildUserSummaryResponse> = emptyList()
)

@Serializable
data class ChildUserSummaryResponse(
    val id: String,
    val name: String,
    val username: String,
    val dob: String,
    val gender: String? = null,
    val subscription: SubscriptionEntitlementResponse? = null,
    val position: String? = null,
    val state: String? = null,
    val town: String? = null,
    val socialMediaHandle: String? = null
)

@Serializable
data class SearchUsersResponse(
    val query: String? = null,
    val limit: Int,
    val users: List<UserSearchResultResponse>
)

@Serializable
data class UserSearchResultResponse(
    val id: String,
    val name: String,
    val username: String,
    val role: UserRole,
    val gender: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val avatarUrl: String? = null,
    val position: String? = null,
    val state: String? = null,
    val town: String? = null,
    val socialMediaHandle: String? = null,
    val clubIds: List<String>
)

@Serializable
data class ResetUserPasswordResponse(
    val userId: String,
    val username: String,
    val temporaryPassword: String,
    val message: String
)

@Serializable
data class SuperAdminUserResponse(
    val id: String,
    val name: String,
    val username: String,
    val email: String,
    val phone: String,
    val role: UserRole,
    val dob: String,
    val gender: String? = null,
    val state: String? = null,
    val town: String? = null,
    val socialMediaHandle: String? = null,
    val createdAt: Long
)

@Serializable
data class CmsCreatedUserResponse(
    val id: String,
    val name: String,
    val username: String,
    val role: UserRole,
    val dob: String,
    val gender: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val position: String? = null,
    val state: String? = null,
    val town: String? = null,
    val socialMediaHandle: String? = null,
    val clubIds: List<String>,
    val teamIds: List<String> = emptyList(),
    val children: List<CmsCreatedChildUserResponse> = emptyList(),
    val createdAt: Long
)

@Serializable
data class CmsCreatedChildUserResponse(
    val id: String,
    val name: String,
    val username: String,
    val role: UserRole,
    val dob: String,
    val gender: String? = null,
    val position: String? = null,
    val state: String? = null,
    val town: String? = null,
    val socialMediaHandle: String? = null,
    val clubIds: List<String>,
    val teamIds: List<String> = emptyList(),
    val createdAt: Long
)

@Serializable
data class UserManagementResponse(
    val userId: String,
    val username: String,
    val clubIds: List<String> = emptyList(),
    val teamIds: List<String> = emptyList(),
    val message: String
)
