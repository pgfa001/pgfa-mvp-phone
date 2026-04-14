package com.provingground.datamodels.response

import kotlinx.serialization.Serializable

@Serializable
data class ClubDetailsResponse(
    val id: String,
    val name: String,
    val logoUrl: String,
    val primaryColor: String,
    val accentColor: String,
    val subscriptionType: String
)