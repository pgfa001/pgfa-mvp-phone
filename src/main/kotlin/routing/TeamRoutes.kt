package com.provingground.routing

import com.provingground.datamodels.ApiMessageResponse
import com.provingground.datamodels.CreateTeamRequest
import com.provingground.datamodels.JoinTeamsRequest
import com.provingground.datamodels.UpdateTeamRequest
import com.provingground.service.TeamsService
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

fun Route.teamRoutes(teamsService: TeamsService) {
    route("/teams") {
        authenticate("auth-jwt") {
            get {
                val principal = call.principal<JWTPrincipal>()
                val actingUserIdString = principal?.payload?.getClaim("userId")?.asString()

                if (actingUserIdString.isNullOrBlank()) {
                    call.respond(HttpStatusCode.Unauthorized, ApiMessageResponse("Invalid token"))
                    return@get
                }

                try {
                    val response = teamsService.getAllTeams(UUID.fromString(actingUserIdString))
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

                if (actingUserIdString.isNullOrBlank()) {
                    call.respond(HttpStatusCode.Unauthorized, ApiMessageResponse("Invalid token"))
                    return@post
                }

                try {
                    val request = call.receive<CreateTeamRequest>()
                    val response = teamsService.createTeam(
                        actingUserId = UUID.fromString(actingUserIdString),
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

            put("/{teamId}") {
                val principal = call.principal<JWTPrincipal>()
                val actingUserIdString = principal?.payload?.getClaim("userId")?.asString()

                if (actingUserIdString.isNullOrBlank()) {
                    call.respond(HttpStatusCode.Unauthorized, ApiMessageResponse("Invalid token"))
                    return@put
                }

                val teamId = call.parameters["teamId"]
                if (teamId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessageResponse("Missing teamId"))
                    return@put
                }

                try {
                    val request = call.receive<UpdateTeamRequest>()
                    val response = teamsService.updateTeam(
                        actingUserId = UUID.fromString(actingUserIdString),
                        teamId = teamId,
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

            post("/join") {
                val principal = call.principal<JWTPrincipal>()
                val actingUserIdString = principal?.payload?.getClaim("userId")?.asString()

                if (actingUserIdString.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiMessageResponse("Invalid token")
                    )
                    return@post
                }

                val request = call.receive<JoinTeamsRequest>()

                try {
                    val response = teamsService.joinTeams(
                        actingUserId = UUID.fromString(actingUserIdString),
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
}