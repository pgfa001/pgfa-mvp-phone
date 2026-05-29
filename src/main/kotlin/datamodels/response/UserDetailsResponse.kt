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
    val children: List<ChildUserSummaryResponse> = emptyList()
)

@Serializable
data class ChildUserSummaryResponse(
    val id: String,
    val name: String,
    val username: String,
    val dob: String,
    val gender: String? = null,
    val position: String? = null
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
    val clubIds: List<String>
)
