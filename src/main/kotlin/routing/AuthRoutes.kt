package com.provingground.routing

import com.provingground.datamodels.ApiMessageResponse
import com.provingground.datamodels.CreateUserAccountRequest
import com.provingground.datamodels.LoginRequest
import com.provingground.service.AuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.authRoutes(authService: AuthService) {
    route("/auth") {

        post("/login") {
            val request = call.receive<LoginRequest>()

            val response = authService.login(request)

            if (response == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiMessageResponse("Invalid username or password")
                )
                return@post
            }

            call.respond(HttpStatusCode.OK, response)
        }

        post("/signup") {
            try {
                val request = call.receive<CreateUserAccountRequest>()
                val response = authService.createAccount(request)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ApiMessageResponse(e.message ?: "Invalid request"))
            }
        }
    }
}