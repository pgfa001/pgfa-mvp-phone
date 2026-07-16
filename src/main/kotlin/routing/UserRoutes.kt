package com.provingground.routing

import com.provingground.datamodels.ApiMessageResponse
import com.provingground.datamodels.CreateCmsUserRequest
import com.provingground.datamodels.CreateSuperAdminRequest
import com.provingground.datamodels.EditUserDetailsRequest
import com.provingground.datamodels.ResetUserPasswordRequest
import com.provingground.datamodels.UpdateUserClubRequest
import com.provingground.datamodels.UpdateUserTeamsRequest
import com.provingground.datamodels.UpdateUserUsernameRequest
import com.provingground.service.UserProfileService
import com.provingground.service.UserService
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

fun Route.userRoutes(
    userService: UserService,
    userProfileService: UserProfileService,
) {
    route("/users") {
        authenticate("auth-jwt") {

            get("/search") {
                val principal = call.principal<JWTPrincipal>()
                val actingUserIdString = principal?.payload?.getClaim("userId")?.asString()

                if (actingUserIdString.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiMessageResponse("Invalid token")
                    )
                    return@get
                }

                val query = call.request.queryParameters["query"]
                val clubId = call.request.queryParameters["clubId"]
                val role = call.request.queryParameters["role"]
                val limitParam = call.request.queryParameters["limit"]
                val limit = if (limitParam.isNullOrBlank()) {
                    10
                } else {
                    limitParam.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ApiMessageResponse("Invalid limit"))
                }

                try {
                    val response = userService.searchUsers(
                        actingUserId = UUID.fromString(actingUserIdString),
                        query = query,
                        clubId = clubId,
                        role = role,
                        limit = limit
                    )
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
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiMessageResponse("Invalid token")
                    )
                    return@post
                }

                try {
                    val request = call.receive<CreateCmsUserRequest>()
                    val response = userService.createCmsUser(
                        actingUserId = UUID.fromString(actingUserIdString),
                        request = request
                    )
                    call.respond(HttpStatusCode.Created, response)
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiMessageResponse(e.message ?: "Invalid request")
                    )
                }
            }

            post("/superadmins") {
                val principal = call.principal<JWTPrincipal>()
                val actingUserIdString = principal?.payload?.getClaim("userId")?.asString()

                if (actingUserIdString.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiMessageResponse("Invalid token")
                    )
                    return@post
                }

                try {
                    val request = call.receive<CreateSuperAdminRequest>()
                    val response = userService.createSuperAdmin(
                        actingUserId = UUID.fromString(actingUserIdString),
                        request = request
                    )
                    call.respond(HttpStatusCode.Created, response)
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiMessageResponse(e.message ?: "Invalid request")
                    )
                }
            }

            delete("/me") {
                val principal = call.principal<JWTPrincipal>()
                val actingUserIdString = principal?.payload?.getClaim("userId")?.asString()

                if (actingUserIdString.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiMessageResponse("Invalid token")
                    )
                    return@delete
                }

                try {
                    val response = userService.deleteMyAccount(
                        actingUserId = UUID.fromString(actingUserIdString)
                    )
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiMessageResponse("Unable to delete account. Please contact support.")
                    )
                }
            }

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

            post("/{userId}/reset-password") {
                val principal = call.principal<JWTPrincipal>()
                val actingUserIdString = principal?.payload?.getClaim("userId")?.asString()

                if (actingUserIdString.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiMessageResponse("Invalid token")
                    )
                    return@post
                }

                val userId = call.parameters["userId"]
                if (userId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiMessageResponse("Missing userId")
                    )
                    return@post
                }

                try {
                    val request = call.receive<ResetUserPasswordRequest>()
                    val response = userService.resetUserPassword(
                        actingUserId = UUID.fromString(actingUserIdString),
                        requestedUserId = userId,
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

            put("/{userId}/username") {
                val principal = call.principal<JWTPrincipal>()
                val actingUserIdString = principal?.payload?.getClaim("userId")?.asString()

                if (actingUserIdString.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiMessageResponse("Invalid token")
                    )
                    return@put
                }

                val userId = call.parameters["userId"]
                if (userId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessageResponse("Missing userId"))
                    return@put
                }

                try {
                    val request = call.receive<UpdateUserUsernameRequest>()
                    val response = userService.updateUsername(
                        actingUserId = UUID.fromString(actingUserIdString),
                        requestedUserId = userId,
                        request = request
                    )
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessageResponse(e.message ?: "Invalid request"))
                }
            }

            put("/{userId}/club") {
                val principal = call.principal<JWTPrincipal>()
                val actingUserIdString = principal?.payload?.getClaim("userId")?.asString()

                if (actingUserIdString.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiMessageResponse("Invalid token")
                    )
                    return@put
                }

                val userId = call.parameters["userId"]
                if (userId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessageResponse("Missing userId"))
                    return@put
                }

                try {
                    val request = call.receive<UpdateUserClubRequest>()
                    val response = userService.updateUserClub(
                        actingUserId = UUID.fromString(actingUserIdString),
                        requestedUserId = userId,
                        request = request
                    )
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessageResponse(e.message ?: "Invalid request"))
                }
            }

            put("/{userId}/teams") {
                val principal = call.principal<JWTPrincipal>()
                val actingUserIdString = principal?.payload?.getClaim("userId")?.asString()

                if (actingUserIdString.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiMessageResponse("Invalid token")
                    )
                    return@put
                }

                val userId = call.parameters["userId"]
                if (userId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessageResponse("Missing userId"))
                    return@put
                }

                try {
                    val request = call.receive<UpdateUserTeamsRequest>()
                    val response = userService.updateUserTeams(
                        actingUserId = UUID.fromString(actingUserIdString),
                        requestedUserId = userId,
                        request = request
                    )
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessageResponse(e.message ?: "Invalid request"))
                }
            }

            delete("/{userId}") {
                val principal = call.principal<JWTPrincipal>()
                val actingUserIdString = principal?.payload?.getClaim("userId")?.asString()

                if (actingUserIdString.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiMessageResponse("Invalid token")
                    )
                    return@delete
                }

                val userId = call.parameters["userId"]
                if (userId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessageResponse("Missing userId"))
                    return@delete
                }

                try {
                    val response = userService.deleteUser(
                        actingUserId = UUID.fromString(actingUserIdString),
                        requestedUserId = userId
                    )
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessageResponse(e.message ?: "Invalid request"))
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
