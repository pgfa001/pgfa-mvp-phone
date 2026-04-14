package com.provingground.routing

import com.provingground.datamodels.AcceptConsentFormsRequest
import com.provingground.datamodels.ApiMessageResponse
import com.provingground.service.ConsentsService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.consentRoutes(consentsService: ConsentsService) {
    authenticate("auth-jwt") {
        route("/consents") {
            post("/accept") {
                val principal = call.principal<JWTPrincipal>()
                val userIdClaim = principal?.payload?.getClaim("userId")?.asString()

                if (userIdClaim.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiMessageResponse("Invalid token")
                    )
                    return@post
                }

                val request = call.receive<AcceptConsentFormsRequest>()

                try {
                    val response = consentsService.acceptConsents(
                        userId = java.util.UUID.fromString(userIdClaim),
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