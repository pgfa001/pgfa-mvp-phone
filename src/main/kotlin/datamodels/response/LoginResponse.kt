package com.provingground.datamodels.response

import com.provingground.database.tables.UserRole
import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    val token: String,
    val userId: String,
    val username: String,
    val role: UserRole,
    val hasAcceptedRequiredConsents: Boolean,
)