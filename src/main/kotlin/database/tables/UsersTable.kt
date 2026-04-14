package com.provingground.database.tables

import org.jetbrains.exposed.sql.Table

enum class UserRole {
    ATHLETE,
    PARENT,
    COACH,
    ADMIN,
}

object UsersTable : Table("users") {
    val id = uuid("id")
    val name = varchar("name", 255)
    val username = varchar("username", 255)
    val password = varchar("password", 255)
    val email = varchar("email", 255).nullable()
    val phone = varchar("phone", 255).nullable()
    val role = enumerationByName("role", 50, UserRole::class)
    val dob = varchar("dob", 255)
    val avatarUrl = varchar("avatarUrl", 255).nullable()
    val position = varchar("position", 255).nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

object ClubToUsersTable : Table("clubs_to_users") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UsersTable.id)
    val clubId = uuid("club_id").references(ClubsTable.id)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(userId, clubId)
    }
}

object TeamsToUsersTable : Table("teams_to_users") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UsersTable.id)
    val teamId = uuid("team_id").references(TeamsTable.id)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(userId, teamId)
    }
}

object ParentToChildrenTable : Table("parent_to_children") {
    val id = uuid("id")
    val parentUserId = uuid("parent_user_id").references(UsersTable.id)
    val childUserId = uuid("child_user_id").references(UsersTable.id)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(parentUserId, childUserId)
    }
}