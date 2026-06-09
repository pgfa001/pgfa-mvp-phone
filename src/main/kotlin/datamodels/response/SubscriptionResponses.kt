package com.provingground.datamodels.response

import com.provingground.database.tables.AthleteSubscriptionStatus
import com.provingground.database.tables.UserRole
import kotlinx.serialization.Serializable

@Serializable
enum class SubscriptionEntitlementSource {
    CLUB_PAID,
    ATHLETE_PAID,
    NOT_REQUIRED
}

@Serializable
data class SubscriptionEntitlementResponse(
    val status: AthleteSubscriptionStatus? = null,
    val hasAccess: Boolean,
    val source: SubscriptionEntitlementSource,
    val trialStartedAt: Long? = null,
    val trialEndsAt: Long? = null,
    val currentPeriodEndsAt: Long? = null,
    val cancelAtPeriodEnd: Boolean = false,
    val upgradeRequired: Boolean
)

@Serializable
data class AthleteSubscriptionResponse(
    val athleteUserId: String,
    val athleteName: String,
    val subscription: SubscriptionEntitlementResponse
)

@Serializable
data class GetMySubscriptionsResponse(
    val userId: String,
    val role: UserRole,
    val subscription: SubscriptionEntitlementResponse? = null,
    val athletes: List<AthleteSubscriptionResponse> = emptyList()
)

@Serializable
data class CreateSubscriptionCheckoutRequest(
    val athleteUserId: String
)

@Serializable
data class CreateSubscriptionCheckoutResponse(
    val athleteUserId: String,
    val customerId: String,
    val subscriptionId: String,
    val paymentIntentClientSecret: String,
    val setupIntentClientSecret: String? = null,
    val ephemeralKeySecret: String? = null,
    val clientSecret: String
)

@Serializable
data class ConfirmSubscriptionRequest(
    val athleteUserId: String
)

@Serializable
data class ConfirmSubscriptionResponse(
    val athleteUserId: String,
    val subscription: SubscriptionEntitlementResponse
)

@Serializable
data class SubscriptionRequiredResponse(
    val message: String,
    val subscription: SubscriptionEntitlementResponse
)
