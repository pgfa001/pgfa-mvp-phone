package com.provingground.routing

import com.provingground.datamodels.ApiMessageResponse
import com.provingground.datamodels.EditUserDetailsRequest
import com.provingground.service.UserProfileService
import com.provingground.service.UserService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.util.UUID

fun Route.userRoutes(
    userService: UserService,
    userProfileService: UserProfileService,
) {
    route("/users") {
        authenticate("auth-jwt") {

            get("/{userId}") {
                val principal = call.principal<JWTPrincipal>()
                val actingUserIdString = principal?.payload?.getClaim("userId")?.asString()

                if (actingUserIdString.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiMessageResponse("Invalid token")
                    )
                    return@get
                }

                val userId = call.parameters["userId"]
                if (userId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiMessageResponse("Missing userId")
                    )
                    return@get
                }

                try {
                    val response = userService.getUserDetails(
                        actingUserId = UUID.fromString(actingUserIdString),
                        requestedUserId = userId
                    )
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiMessageResponse(e.message ?: "Invalid request")
                    )
                }
            }

            get("/me/profile") {
                val principal = call.principal<JWTPrincipal>()
                val actingUserIdString = principal?.payload?.getClaim("userId")?.asString()

                if (actingUserIdString.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiMessageResponse("Invalid token")
                    )
                    return@get
                }

                try {
                    val response = userProfileService.getMyProfile(
                        actingUserId = UUID.fromString(actingUserIdString)
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

        put("/{userId}") {
            val userId = call.parameters["userId"]
            val request = call.receive<EditUserDetailsRequest>()
            call.respond(
                HttpStatusCode.OK,
                ApiMessageResponse("Edit user details endpoint hit")
            )
        }
    }
}