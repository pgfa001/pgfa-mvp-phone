package com.provingground.database.tables

import org.jetbrains.exposed.sql.Table

enum class SubscriptionType {
    CLUB_PAID,
    ATHLETE_PAID
}

object ClubsTable : Table("clubs") {
    val id = uuid("id")
    val name = varchar("name", 255)
    val logoUrl = text("logo_url")
    val accessCode = varchar("access_code", 255)
    val primaryColor = varchar("primary_color", 255)
    val accentColor = varchar("accent_color", 255)
    val subscriptionType = enumerationByName("subscription_type", 50, SubscriptionType::class)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(accessCode)
    }
}