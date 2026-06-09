package com.provingground.datamodels.response

import com.provingground.database.tables.UserRole
import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    val token: String,
    val userId: String,
    val username: String,
    val role: UserRole,
    val clubId: String? = null,
    val hasAcceptedRequiredConsents: Boolean,
    val subscription: SubscriptionEntitlementResponse? = null,
    val athleteSubscriptions: List<AthleteSubscriptionResponse> = emptyList()
)
