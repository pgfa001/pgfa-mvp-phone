package com.provingground.database.tables

import org.jetbrains.exposed.sql.Table

object TeamsTable : Table("teams") {
    val id = uuid("id")
    val name = varchar("name", 255)
    val clubId = uuid("club_id").references(ClubsTable.id)
    val lowerAgeRange = integer("lower_age_range")
    val upperAgeRange = integer("upper_age_range")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}