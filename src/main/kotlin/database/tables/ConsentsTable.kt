package com.provingground.database.tables

import org.jetbrains.exposed.sql.Table

enum class ConsentType {
    MEDIA_RELEASE,
    LIABILITY,
    TERMS_AND_CONDITIONS;
}

object ConsentsTable : Table("consents") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UsersTable.id)
    val consentType = enumerationByName("consent_type", 50, ConsentType::class)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(userId, consentType)
    }
}