package com.provingground.routing

import com.provingground.datamodels.ApiMessageResponse
import com.provingground.datamodels.response.ConfirmSubscriptionRequest
import com.provingground.datamodels.response.CreateSubscriptionCheckoutRequest
import com.provingground.service.SubscriptionService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

fun Route.subscriptionRoutes(subscriptionService: SubscriptionService) {
    authenticate("auth-jwt") {
        route("/subscriptions") {
            get("/me") {
                val actingUserId = call.authenticatedUserId() ?: return@get

                try {
                    val response = subscriptionService.getMySubscriptions(actingUserId)
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessageResponse(e.message ?: "Invalid request"))
                }
            }

            post("/checkout") {
                val actingUserId = call.authenticatedUserId() ?: return@post

                try {
                    val request = call.receive<CreateSubscriptionCheckoutRequest>()
                    val response = subscriptionService.createCheckout(
                        actingUserId = actingUserId,
                        athleteUserId = request.athleteUserId
                    )
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessageResponse(e.message ?: "Invalid request"))
                }
            }

            post("/confirm") {
                val actingUserId = call.authenticatedUserId() ?: return@post

                try {
                    val request = call.receive<ConfirmSubscriptionRequest>()
                    val response = subscriptionService.confirmSubscription(
                        actingUserId = actingUserId,
                        athleteUserId = request.athleteUserId
                    )
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessageResponse(e.message ?: "Invalid request"))
                }
            }
        }
    }

    route("/stripe") {
        post("/webhook") {
            val payload = call.receiveText()
            val signature = call.request.header("Stripe-Signature")

            try {
                val updated = subscriptionService.handleStripeWebhook(payload, signature)
                call.respond(
                    HttpStatusCode.OK,
                    ApiMessageResponse(if (updated) "Subscription updated" else "Event ignored")
                )
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ApiMessageResponse(e.message ?: "Invalid webhook"))
            }
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.authenticatedUserId(): UUID? {
    val userIdString = principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
    if (userIdString.isNullOrBlank()) {
        respond(HttpStatusCode.Unauthorized, ApiMessageResponse("Invalid token"))
        return null
    }

    return UUID.fromString(userIdString)
}
