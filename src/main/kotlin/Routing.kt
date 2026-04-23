package com.provingground

import aws.sdk.kotlin.services.s3.S3Client
import com.provingground.database.repositories.ChallengesRepository
import com.provingground.database.repositories.ClubsRepository
import com.provingground.database.repositories.ConsentsRepository
import com.provingground.database.repositories.TeamsRepository
import com.provingground.database.repositories.UsersRepository
import com.provingground.routing.authRoutes
import com.provingground.routing.challengeRoutes
import com.provingground.routing.clubRoutes
import com.provingground.routing.consentRoutes
import com.provingground.routing.homeRoutes
import com.provingground.routing.subscriptionRoutes
import com.provingground.routing.teamRoutes
import com.provingground.routing.userRoutes
import com.provingground.service.AuthService
import com.provingground.service.ChallengeService
import com.provingground.service.ClubsService
import com.provingground.service.ConsentsService
import com.provingground.service.HomeService
import com.provingground.service.S3VideoStorageService
import com.provingground.service.TeamsService
import com.provingground.service.UserProfileService
import com.provingground.service.UserService
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    val clubsRepository = ClubsRepository()
    val teamsRepository = TeamsRepository()
    val usersRepository = UsersRepository()
    val consentRepository = ConsentsRepository()
    val challengesRepository = ChallengesRepository()

    val awsRegion = System.getenv("AWS_REGION")
    val s3Client = S3Client {
        this.region = awsRegion
    }
    val s3VideoStorageService = S3VideoStorageService(
        bucketName = System.getenv("S3_BUCKET_NAME"),
        s3Client = s3Client,
    )

    val clubsService = ClubsService(clubsRepository, usersRepository, s3VideoStorageService)
    val teamsService = TeamsService(clubsRepository, teamsRepository, usersRepository)

    val passwordHasher = PasswordHasher()
    val authService = AuthService(usersRepository, clubsRepository, consentRepository, passwordHasher)

    val consentService = ConsentsService(consentRepository, usersRepository)

    val homeService = HomeService(usersRepository, teamsRepository, challengesRepository, s3VideoStorageService)

    val challengeService = ChallengeService(usersRepository, teamsRepository, challengesRepository, clubsRepository, s3VideoStorageService)

    val userService = UserService(usersRepository)

    val userProfileService = UserProfileService(usersRepository, teamsRepository, challengesRepository)

    routing {
        /**
         * Authentication
         */
        authRoutes(authService)

        /**
         * Club Details
         */
        clubRoutes(clubsService, teamsService)

        /**
         * Consent forms
         */
        consentRoutes(consentService)

        /**
         * Teams
         */
        teamRoutes(teamsService)

        subscriptionRoutes()

        /**
         * Home screen
         */
        homeRoutes(homeService)

        /**
         * User details
         */
        userRoutes(userService, userProfileService)

        /**
         * Challenges
         */
        challengeRoutes(challengeService)
    }
}
