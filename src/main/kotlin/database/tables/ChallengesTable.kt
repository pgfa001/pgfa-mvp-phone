package com.provingground.database.tables

import org.jetbrains.exposed.sql.Table

object ChallengesTable : Table("challenges") {
    val id = uuid("id")
    val title = varchar("title", 255)
    val description = text("description")
    val demoVideoObjectKey = varchar("demo_video_object_key", 512).nullable()
    val scoringType = varchar("scoring_type", 255)
    val difficulty = integer("difficulty")
    val startTime = long("start_time")
    val endTime = long("end_time")
    val createdBy = uuid("created_by").references(UsersTable.id)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

object ChallengeToClubsTable : Table("challenge_to_clubs") {
    val id = uuid("id")
    val challengeId = uuid("challenge_id").references(ChallengesTable.id)
    val clubId = uuid("club_id").references(ClubsTable.id)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(challengeId, clubId)
    }
}

object ChallengeDemoUploadIntentsTable : Table("challenge_demo_upload_intents") {
    val id = uuid("id")
    val actingUserId = uuid("acting_user_id").references(UsersTable.id)
    val objectKey = varchar("object_key", 512)
    val originalFileName = varchar("original_file_name", 255)
    val contentType = varchar("content_type", 255)
    val expiresAt = long("expires_at")
    val consumedAt = long("consumed_at").nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(objectKey)
    }
}

enum class SubmissionValidationStatus {
    NOT_VALIDATED,
    VALIDATED,
    INVALID
}

object ChallengeSubmissionsTable : Table("challenge_submissions") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UsersTable.id)
    val challengeId = uuid("challenge_id").references(ChallengesTable.id)
    val teamId = uuid("team_id").references(TeamsTable.id)
    val videoObjectKey = varchar("video_object_key", 512)
    val score = integer("score")
    val validationStatus = enumerationByName("validation_status", 50, SubmissionValidationStatus::class)
    val validatedBy = uuid("validated_by").references(UsersTable.id).nullable()
    val validatedAt = long("validated_at").nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

object ChallengeUploadIntentsTable : Table("challenge_upload_intents") {
    val id = uuid("id")
    val actingUserId = uuid("acting_user_id").references(UsersTable.id)
    val athleteUserId = uuid("athlete_user_id").references(UsersTable.id)
    val challengeId = uuid("challenge_id").references(ChallengesTable.id)
    val teamId = uuid("team_id").references(TeamsTable.id)
    val objectKey = varchar("object_key", 512)
    val originalFileName = varchar("original_file_name", 255)
    val contentType = varchar("content_type", 255)
    val expiresAt = long("expires_at")
    val consumedAt = long("consumed_at").nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(objectKey)
    }
}