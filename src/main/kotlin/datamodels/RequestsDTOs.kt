package com.provingground.datamodels

import com.provingground.database.tables.ConsentType
import com.provingground.database.tables.SubmissionValidationStatus
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class CreateUserAccountRequest(
    val clubId: String,
    val name: String,
    val username: String,
    val password: String,
    val email: String,
    val phone: String,
    val role: String,
    val dob: String, // MM/dd/yyyy
    val position: String? = null,
    val childAccounts: List<CreateChildAccountRequest> = emptyList()
)

@Serializable
data class CreateChildAccountRequest(
    val name: String,
    val username: String,
    val password: String,
    val dob: String, // MM/dd/yyyy
    val position: String
)

@Serializable
data class AcceptConsentFormsRequest(
    val consentTypes: List<ConsentType>
)

@Serializable
data class JoinTeamsRequest(
    val assignments: List<TeamAssignmentRequest>
)

@Serializable
data class TeamAssignmentRequest(
    val userId: String,
    val teamIds: List<String>
)

@Serializable
data class SignUpForSubscriptionRequest(
    val userId: String,
    val clubId: String,
    val planId: String? = null,
    val billingPeriod: String? = null
)

@Serializable
data class EditUserDetailsRequest(
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val avatarUrl: String? = null,
    val position: String? = null
)

@Serializable
data class VerifyChallengeSubmissionRequest(
    val validationStatus: SubmissionValidationStatus
)

@Serializable
data class ApiMessageResponse(
    val message: String
)