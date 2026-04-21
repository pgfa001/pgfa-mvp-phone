package com.provingground.database

import com.provingground.database.tables.ChallengeDemoUploadIntentsTable
import com.provingground.database.tables.ChallengeSubmissionsTable
import com.provingground.database.tables.ChallengeToClubsTable
import com.provingground.database.tables.ChallengeUploadIntentsTable
import com.provingground.database.tables.ChallengesTable
import com.provingground.database.tables.ClubLogoUploadIntentsTable
import com.provingground.database.tables.ClubsTable
import com.provingground.database.tables.ConsentsTable
import com.provingground.database.tables.ParentToChildrenTable
import com.provingground.database.tables.TeamsTable
import com.provingground.database.tables.UsersTable
import com.provingground.datamodels.Challenge
import com.provingground.datamodels.ChallengeClub
import com.provingground.datamodels.ChallengeDemoUploadIntent
import com.provingground.datamodels.ChallengeSubmission
import com.provingground.datamodels.ChallengeUploadIntent
import com.provingground.datamodels.Club
import com.provingground.datamodels.ClubLogoUploadIntent
import com.provingground.datamodels.Consent
import com.provingground.datamodels.ParentChildRelationship
import com.provingground.datamodels.Team
import com.provingground.datamodels.User
import org.jetbrains.exposed.sql.ResultRow
import java.util.UUID

fun ResultRow.toClub(): Club {
    return Club(
        id = this[ClubsTable.id],
        name = this[ClubsTable.name],
        logoUrl = this[ClubsTable.logoUrl],
        accessCode = this[ClubsTable.accessCode],
        primaryColor = this[ClubsTable.primaryColor],
        accentColor = this[ClubsTable.accentColor],
        subscriptionType = this[ClubsTable.subscriptionType],
        createdAt = this[ClubsTable.createdAt]
    )
}

fun ResultRow.toClubLogoUploadIntent(): ClubLogoUploadIntent {
    return ClubLogoUploadIntent(
        id = this[ClubLogoUploadIntentsTable.id],
        actingUserId = this[ClubLogoUploadIntentsTable.actingUserId],
        objectKey = this[ClubLogoUploadIntentsTable.objectKey],
        originalFileName = this[ClubLogoUploadIntentsTable.originalFileName],
        contentType = this[ClubLogoUploadIntentsTable.contentType],
        expiresAt = this[ClubLogoUploadIntentsTable.expiresAt],
        consumedAt =this[ClubLogoUploadIntentsTable.consumedAt] ,
        createdAt = this[ClubLogoUploadIntentsTable.createdAt],
    )
}

fun ResultRow.toTeam(): Team {
    return Team(
        id = this[TeamsTable.id],
        name = this[TeamsTable.name],
        clubId = this[TeamsTable.clubId],
        lowerAgeRange = this[TeamsTable.lowerAgeRange],
        upperAgeRange = this[TeamsTable.upperAgeRange],
        createdAt = this[TeamsTable.createdAt]
    )
}

fun ResultRow.toUser(): User {
    return User(
        id = this[UsersTable.id],
        name = this[UsersTable.name],
        username = this[UsersTable.username],
        password = this[UsersTable.password],
        email = this[UsersTable.email],
        phone = this[UsersTable.phone],
        role = this[UsersTable.role],
        dob = this[UsersTable.dob],
        avatarUrl = this[UsersTable.avatarUrl],
        position = this[UsersTable.position],
        createdAt = this[UsersTable.createdAt]
    )
}

fun ResultRow.toChallenge(): Challenge {
    return Challenge(
        id = this[ChallengesTable.id],
        title = this[ChallengesTable.title],
        description = this[ChallengesTable.description],
        demoVideoObjectKey = this[ChallengesTable.demoVideoObjectKey],
        scoringType = this[ChallengesTable.scoringType],
        difficulty = this[ChallengesTable.difficulty],
        startTime = this[ChallengesTable.startTime],
        endTime = this[ChallengesTable.endTime],
        createdBy = this[ChallengesTable.createdBy],
        createdAt = this[ChallengesTable.createdAt]
    )
}

fun ResultRow.toChallengeClub(): ChallengeClub {
    return ChallengeClub(
        id = this[ChallengeToClubsTable.id],
        challengeId = this[ChallengeToClubsTable.challengeId],
        clubId = this[ChallengeToClubsTable.clubId],
        createdAt = this[ChallengeToClubsTable.createdAt]
    )
}

fun ResultRow.toChallengeDemoUploadIntent(): ChallengeDemoUploadIntent {
    return ChallengeDemoUploadIntent(
        id = this[ChallengeDemoUploadIntentsTable.id],
        actingUserId = this[ChallengeDemoUploadIntentsTable.actingUserId],
        objectKey = this[ChallengeDemoUploadIntentsTable.objectKey],
        originalFileName = this[ChallengeDemoUploadIntentsTable.originalFileName],
        contentType = this[ChallengeDemoUploadIntentsTable.contentType],
        expiresAt = this[ChallengeDemoUploadIntentsTable.expiresAt],
        consumedAt = this[ChallengeDemoUploadIntentsTable.consumedAt],
        createdAt = this[ChallengeDemoUploadIntentsTable.createdAt]
    )
}

fun ResultRow.toChallengeSubmission(): ChallengeSubmission {
    return ChallengeSubmission(
        id = this[ChallengeSubmissionsTable.id],
        userId = this[ChallengeSubmissionsTable.userId],
        challengeId = this[ChallengeSubmissionsTable.challengeId],
        teamId = this[ChallengeSubmissionsTable.teamId],
        videoObjectKey = this[ChallengeSubmissionsTable.videoObjectKey],
        score = this[ChallengeSubmissionsTable.score],
        validationStatus = this[ChallengeSubmissionsTable.validationStatus],
        validatedBy = this[ChallengeSubmissionsTable.validatedBy],
        validatedAt = this[ChallengeSubmissionsTable.validatedAt],
        createdAt = this[ChallengeSubmissionsTable.createdAt]
    )
}

fun ResultRow.toConsent(): Consent {
    return Consent(
        id = this[ConsentsTable.id],
        userId = this[ConsentsTable.userId],
        consentType = this[ConsentsTable.consentType],
        createdAt = this[ConsentsTable.createdAt]
    )
}

fun ResultRow.toParentChildRelationship(): ParentChildRelationship {
    return ParentChildRelationship(
        id = this[ParentToChildrenTable.id],
        parentUserId = this[ParentToChildrenTable.parentUserId],
        childUserId = this[ParentToChildrenTable.childUserId],
        createdAt = this[ParentToChildrenTable.createdAt]
    )
}

fun ResultRow.toChallengeUploadIntent(): ChallengeUploadIntent {
    return ChallengeUploadIntent(
        id = this[ChallengeUploadIntentsTable.id],
        actingUserId = this[ChallengeUploadIntentsTable.actingUserId],
        athleteUserId = this[ChallengeUploadIntentsTable.athleteUserId],
        challengeId = this[ChallengeUploadIntentsTable.challengeId],
        teamId = this[ChallengeUploadIntentsTable.teamId],
        objectKey = this[ChallengeUploadIntentsTable.objectKey],
        originalFileName = this[ChallengeUploadIntentsTable.originalFileName],
        contentType = this[ChallengeUploadIntentsTable.contentType],
        expiresAt = this[ChallengeUploadIntentsTable.expiresAt],
        consumedAt = this[ChallengeUploadIntentsTable.consumedAt],
        createdAt = this[ChallengeUploadIntentsTable.createdAt]
    )
}