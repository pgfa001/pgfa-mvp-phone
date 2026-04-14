package com.provingground.routing

import com.provingground.datamodels.ApiMessageResponse
import com.provingground.datamodels.SignUpForSubscriptionRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.subscriptionRoutes() {
    route("/subscriptions") {

        post("/signup") {
            val request = call.receive<SignUpForSubscriptionRequest>()
            call.respond(
                HttpStatusCode.OK,
                ApiMessageResponse("Sign up for subscription endpoint hit")
            )
        }
    }
}