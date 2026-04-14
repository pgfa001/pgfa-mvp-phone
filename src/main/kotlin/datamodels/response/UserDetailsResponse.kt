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
    val position: String? = null
)