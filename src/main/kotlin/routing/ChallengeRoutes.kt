package com.provingground.routing

import com.provingground.datamodels.ApiMessageResponse
import com.provingground.datamodels.CreateChallengeCmsRequest
import com.provingground.datamodels.UpdateChallengeCmsRequest
import com.provingground.datamodels.VerifyChallengeSubmissionRequest
import com.provingground.datamodels.response.CreateChallengeDemoUploadUrlRequest
import com.provingground.datamodels.response.CreateChallengeSubmissionRequest
import com.provingground.datamodels.response.CreateChallengeSubmissionUploadUrlRequest
import com.provingground.service.ChallengeService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.util.UUID

fun Route.challengeRoutes(challengeService: ChallengeService) {
    route("/challenge-submissions") {
        authenticate("auth-jwt") {
            get("/{submissionId}") {
                val principal = call.principal<JWTPrincipal>()
                val actingUserIdString = principal?.payload?.getClaim("userId")?.asString()

                if (actingUserIdString.isNullOrBlank()) {
                    call.respond(HttpStatusCode.Unauthorized, ApiMessageResponse("Invalid token"))
                    return@get
                }

                val submissionId = call.parameters["submissionId"]
                if (submissionId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessageResponse("Missing submissionId"))
                    return@get
                }

                try {
                    val response = challengeService.getChallengeSubmissionDetails(
                        actingUserId = UUID.fromString(actingUserIdString),
                        submissionId = submissionId
                    )
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiMessageResponse(e.message ?: "Invalid request")
                    )
                }
            }

            post("/{submissionId}/verify") {
                val principal = call.principal<JWTPrincipal>()
                val actingUserIdString = principal?.payload?.getClaim("userId")?.asString()

                if (actingUserIdString.isNullOrBlank()) {
                    call.respond(HttpStatusCode.Unauthorized, ApiMessageResponse("Invalid token"))
                    return@post
                }

                val submissionId = call.parameters["submissionId"]
                if (submissionId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessageResponse("Missing submissionId"))
                    return@post
                }

                try {
                    val request = call.receive<VerifyChallengeSubmissionRequest>()
                    val response = challengeService.verifyChallengeSubmission(
                        actingUserId = UUID.fromString(actingUserIdString),
                        submissionId = submissionId,
                        request = request
                    )
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiMessageResponse(e.message ?: "Invalid request")
                    )
                }
            }
        }
    }

    route("/challenges") {

        authenticate("auth-jwt") {
            get {
                val principal = call.principal<JWTPrincipal>()
                val actingUserIdString = principal?.payload?.getClaim("userId")?.asString()

                if (actingUserIdString.isNullOrBlank()) {
                    call.respond(HttpStatusCode.Unauthorized, ApiMessageResponse("Invalid token"))
                    return@get
                }

                try {
                    val response = challengeService.getAllChallengesCms(UUID.fromString(actingUserIdString))
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessageResponse(e.message ?: "Invalid request"))
                }
            }

            post {
                val principal = call.principal<JWTPrincipal>()
                val actingUserIdString = principal?.payload?.getClaim("userId")?.asString()

                if (actingUserIdString.isNullOrBlank()) {
                    call.respond(HttpStatusCode.Unauthorized, ApiMessageResponse("Invalid token"))
                    return@post
                }

                try {
                    val request = call.receive<CreateChallengeCmsRequest>()
                    val response = challengeService.createChallengeCms(
                        actingUserId = UUID.fromString(actingUserIdString),
                        request = request
                    )
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessageResponse(e.message ?: "Invalid request"))
                }
            }

            put("/{challengeId}") {
                val principal = call.principal<JWTPrincipal>()
                val actingUserIdString = principal?.payload?.getClaim("userId")?.asString()
                val challengeId = call.parameters["challengeId"]

                if (actingUserIdString.isNullOrBlank()) {
                    call.respond(HttpStatusCode.Unauthorized, ApiMessageResponse("Invalid token"))
                    return@put
                }

                if (challengeId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessageResponse("Missing challengeId"))
                    return@put
                }

                try {
                    val request = call.receive<UpdateChallengeCmsRequest>()
                    val response = challengeService.updateChallengeCms(
                        actingUserId = UUID.fromString(actingUserIdString),
                        challengeId = challengeId,
                        request = request
                    )
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessageResponse(e.message ?: "Invalid request"))
                }
            }

            post("/demo-upload-url") {
                val principal = call.principal<JWTPrincipal>()
                val actingUserIdString = principal?.payload?.getClaim("userId")?.asString()

                if (actingUserIdString.isNullOrBlank()) {
                    call.respond(HttpStatusCode.Unauthorized, ApiMessageResponse("Invalid token"))
                    return@post
                }

                try {
                    val request = call.receive<CreateChallengeDemoUploadUrlRequest>()
                    val response = challengeService.createChallengeDemoUploadUrl(
                        actingUserId = UUID.fromString(actingUserIdString),
                        request = request
                    )
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessageResponse(e.message ?: "Invalid request"))
                }
            }

            get("/{challengeId}/demo-video-url") {
                val principal = call.principal<JWTPrincipal>()
                val actingUserIdString = principal?.payload?.getClaim("userId")?.asString()
                val challengeId = call.parameters["challengeId"]

                if (actingUserIdString.isNullOrBlank()) {
                    call.respond(HttpStatusCode.Unauthorized, ApiMessageResponse("Invalid token"))
                    return@get
                }

                if (challengeId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessageResponse("Missing challengeId"))
                    return@get
                }

                try {
                    val response = challengeService.getChallengeDemoVideoUrl(
                        actingUserId = UUID.fromString(actingUserIdString),
                        challengeId = challengeId
                    )
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessageResponse(e.message ?: "Invalid request"))
                }
            }

            get("/{challengeId}") {
                val principal = call.principal<JWTPrincipal>()
                val actingUserIdString = principal?.payload?.getClaim("userId")?.asString()

                if (actingUserIdString.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiMessageResponse("Invalid token")
                    )
                    return@get
                }

                val challengeId = call.parameters["challengeId"]
                if (challengeId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiMessageResponse("Missing challengeId")
                    )
                    return@get
                }

                val teamIdsParam = call.request.queryParameters["teamIds"]
                if (teamIdsParam.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiMessageResponse("Missing required query parameter: teamIds")
                    )
                    return@get
                }

                val teamIds = teamIdsParam
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }

                try {
                    val response = challengeService.getChallengeDetails(
                        actingUserId = UUID.fromString(actingUserIdString),
                        challengeId = challengeId,
                        teamIds = teamIds
                    )
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiMessageResponse(e.message ?: "Invalid request")
                    )
                }
            }

            get("/{challengeId}/submissions") {
                val principal = call.principal<JWTPrincipal>()
                val actingUserIdString = principal?.payload?.getClaim("userId")?.asString()

                if (actingUserIdString.isNullOrBlank()) {
                    call.respond(HttpStatusCode.Unauthorized, ApiMessageResponse("Invalid token"))
                    return@get
                }

                val challengeId = call.parameters["challengeId"]
                if (challengeId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessageResponse("Missing challengeId"))
                    return@get
                }

                val teamId = call.request.queryParameters["teamId"]

                try {
                    val response = challengeService.getMyChallengeSubmissions(
                        actingUserId = UUID.fromString(actingUserIdString),
                        challengeId = challengeId,
                        teamId = teamId
                    )
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiMessageResponse(e.message ?: "Invalid request")
                    )
                }
            }

            get("/{challengeId}/review-submissions") {
                val principal = call.principal<JWTPrincipal>()
                val actingUserIdString = principal?.payload?.getClaim("userId")?.asString()

                if (actingUserIdString.isNullOrBlank()) {
                    call.respond(HttpStatusCode.Unauthorized, ApiMessageResponse("Invalid token"))
                    return@get
                }

                val challengeId = call.parameters["challengeId"]
                if (challengeId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessageResponse("Missing challengeId"))
                    return@get
                }

                val teamId = call.request.queryParameters["teamId"]

                try {
                    val response = challengeService.getChallengeReviewSubmissions(
                        actingUserId = UUID.fromString(actingUserIdString),
                        challengeId = challengeId,
                        teamId = teamId
                    )
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiMessageResponse(e.message ?: "Invalid request")
                    )
                }
            }

            post("/{challengeId}/submission-upload-url") {
                val principal = call.principal<JWTPrincipal>()
                val actingUserId = principal?.payload?.getClaim("userId")?.asString()

                if (actingUserId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.Unauthorized, ApiMessageResponse("Invalid token"))
                    return@post
                }

                val challengeId = call.parameters["challengeId"]
                if (challengeId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessageResponse("Missing challengeId"))
                    return@post
                }

                try {
                    val request = call.receive<CreateChallengeSubmissionUploadUrlRequest>()
                    val response = challengeService.createSubmissionUploadUrl(
                        actingUserId = UUID.fromString(actingUserId),
                        challengeId = challengeId,
                        request = request
                    )
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessageResponse(e.message ?: "Invalid request"))
                }
            }

            post("/{challengeId}/submissions") {
                val principal = call.principal<JWTPrincipal>()
                val actingUserId = principal?.payload?.getClaim("userId")?.asString()

                if (actingUserId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.Unauthorized, ApiMessageResponse("Invalid token"))
                    return@post
                }

                val challengeId = call.parameters["challengeId"]
                if (challengeId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessageResponse("Missing challengeId"))
                    return@post
                }

                try {
                    val request = call.receive<CreateChallengeSubmissionRequest>()
                    val response = challengeService.createChallengeSubmission(
                        actingUserId = UUID.fromString(actingUserId),
                        challengeId = challengeId,
                        request = request
                    )
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessageResponse(e.message ?: "Invalid request"))
                }
            }
        }

        get("/{challengeId}/leaderboard") {
            val challengeId = call.parameters["challengeId"]
            call.respond(
                HttpStatusCode.OK,
                ApiMessageResponse("Get challenge leaderboard endpoint hit")
            )
        }
    }
}