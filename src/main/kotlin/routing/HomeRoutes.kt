package com.provingground.routing

import com.provingground.datamodels.ApiMessageResponse
import com.provingground.service.HomeService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.homeRoutes(homeService: HomeService) {
    authenticate("auth-jwt") {
        route("/home") {
            get {
                val principal = call.principal<JWTPrincipal>()
                val userIdString = principal?.payload?.getClaim("userId")?.asString()

                if (userIdString.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiMessageResponse("Invalid token")
                    )
                    return@get
                }

                try {
                    val response = homeService.getHomeScreen(UUID.fromString(userIdString))
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