package com.provingground.datamodels.response

import com.provingground.database.tables.ConsentType
import kotlinx.serialization.Serializable

@Serializable
data class AcceptConsentFormsResponse(
    val success: Boolean,
    val acceptedConsentTypes: List<ConsentType>
)