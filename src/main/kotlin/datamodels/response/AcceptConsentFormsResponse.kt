package com.provingground.datamodels.response

import com.provingground.database.tables.ConsentType
import kotlinx.serialization.Serializable

@Serializable
data class AcceptConsentFormsResponse(
    val success: Boolean,
    val acceptedConsentTypes: List<ConsentType>
)

@Serializable
data class RequiredConsentFormsResponse(
    val consents: List<RequiredConsentFormResponse>
)

@Serializable
data class RequiredConsentFormResponse(
    val consentType: ConsentType,
    val title: String,
    val url: String,
    val required: Boolean
)
