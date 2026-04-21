package com.provingground.routing

import com.provingground.datamodels.ApiMessageResponse
import com.provingground.datamodels.response.CreateClubLogoUploadUrlRequest
import com.provingground.datamodels.response.CreateClubRequest
import com.provingground.datamodels.response.UpdateClubRequest
import com.provingground.service.ClubsService
import com.provingground.service.TeamsService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.util.UUID

fun Route.clubRoutes(
    clubsService: ClubsService,
    teamsService: TeamsService
) {
    route("/clubs") {

        get("/details") {
            val accessCode = call.request.queryParameters["accessCode"]

            if (accessCode.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiMessageResponse("Missing required query parameter: accessCode")
                )
                return@get
            }

            val club = clubsService.getClubDetailsByAccessCode(accessCode)

            if (club == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiMessageResponse("Club not found")
                )
                return@get
            }

            call.respond(HttpStatusCode.OK, club)
        }

        get("/{clubId}/teams") {
            val clubId = call.parameters["clubId"]

            if (clubId.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiMessageResponse("Missing clubId")
                )
                return@get
            }

            try {
                val response = teamsService.getTeamsForClub(clubId)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiMessageResponse(e.message ?: "Invalid request")
                )
            }
        }

        authenticate("auth-jwt") {
            get {
                val principal = call.principal<JWTPrincipal>()
                val actingUserIdString = principal?.payload?.getClaim("userId")?.asString()

                if (actingUserIdString.isNullOrBlank()) {
                    call.respond(HttpStatusCode.Unauthorized, ApiMessageResponse("Invalid token"))
                    return@get
                }

                try {
                    val response = clubsService.getAllClubs(UUID.fromString(actingUserIdString))
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiMessageResponse(e.message ?: "Invalid request")
                    )
                }
            }

            post {
                val principal = call.principal<JWTPrincipal>()
                val actingUserIdString = principal?.payload?.getClaim("userId")?.asString()
                val actingUserId = UUID.fromString(actingUserIdString)

                val request = call.receive<CreateClubRequest>()
                val response = clubsService.createClub(actingUserId, request)
                call.respond(HttpStatusCode.OK, response)
            }

            put("/{clubId}") {
                val principal = call.principal<JWTPrincipal>()
                val actingUserIdString = principal?.payload?.getClaim("userId")?.asString()
                val actingUserId = UUID.fromString(actingUserIdString)

                val clubId = call.parameters["clubId"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                val request = call.receive<UpdateClubRequest>()
                val response = clubsService.updateClub(actingUserId, clubId, request)
                call.respond(HttpStatusCode.OK, response)
            }

            delete("/{clubId}") {
                val principal = call.principal<JWTPrincipal>()
                val actingUserIdString = principal?.payload?.getClaim("userId")?.asString()
                val actingUserId = UUID.fromString(actingUserIdString)

                val clubId = call.parameters["clubId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                clubsService.deleteClub(actingUserId, clubId)
                call.respond(HttpStatusCode.OK, ApiMessageResponse("Club deleted"))
            }

            post("/logo-upload-url") {
                val principal = call.principal<JWTPrincipal>()
                val actingUserIdString = principal?.payload?.getClaim("userId")?.asString()
                val actingUserId = UUID.fromString(actingUserIdString)

                val request = call.receive<CreateClubLogoUploadUrlRequest>()
                val response = clubsService.createClubLogoUploadUrl(actingUserId, request)
                call.respond(HttpStatusCode.OK, response)
            }

            get("/{clubId}/logo-url") {
                val principal = call.principal<JWTPrincipal>()
                val actingUserIdString = principal?.payload?.getClaim("userId")?.asString()
                val actingUserId = UUID.fromString(actingUserIdString)

                val clubId = call.parameters["clubId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val response = clubsService.getClubLogoUrl(actingUserId, clubId)
                call.respond(HttpStatusCode.OK, response)
            }
        }
    }
}