package com.provingground.service

import com.provingground.database.tables.AthleteSubscriptionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

data class StripeCheckoutResult(
    val customerId: String,
    val subscriptionId: String,
    val clientSecret: String,
    val priceId: String
)

data class StripeSubscriptionState(
    val subscriptionId: String,
    val status: AthleteSubscriptionStatus,
    val currentPeriodEndsAt: Long?,
    val cancelAtPeriodEnd: Boolean
)

data class StripeWebhookUpdate(
    val subscriptionId: String,
    val status: AthleteSubscriptionStatus,
    val currentPeriodEndsAt: Long?,
    val cancelAtPeriodEnd: Boolean
)

class StripeBillingService(
    private val secretKey: String? = System.getenv("STRIPE_SECRET_KEY"),
    private val priceId: String? = System.getenv("STRIPE_ATHLETE_MONTHLY_PRICE_ID"),
    private val webhookSecret: String? = System.getenv("STRIPE_WEBHOOK_SECRET")
) {
    private val client = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun createSubscriptionCheckout(
        payerUserId: String,
        payerName: String,
        payerEmail: String?,
        athleteUserId: String,
        existingCustomerId: String?
    ): StripeCheckoutResult = withContext(Dispatchers.IO) {
        val configuredSecretKey = secretKey?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Stripe secret key is not configured")
        val configuredPriceId = priceId?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Stripe athlete monthly price id is not configured")
        if (!configuredPriceId.startsWith("price_")) {
            throw IllegalArgumentException("STRIPE_ATHLETE_MONTHLY_PRICE_ID must be a Stripe Price ID that starts with price_, not a literal dollar amount")
        }

        val customerId = existingCustomerId?.takeIf { it.isNotBlank() } ?: createCustomer(
            secretKey = configuredSecretKey,
            payerUserId = payerUserId,
            payerName = payerName,
            payerEmail = payerEmail
        )

        val response = postForm(
            secretKey = configuredSecretKey,
            path = "/v1/subscriptions",
            fields = listOf(
                "customer" to customerId,
                "items[0][price]" to configuredPriceId,
                "payment_behavior" to "default_incomplete",
                "payment_settings[save_default_payment_method]" to "on_subscription",
                "billing_mode[type]" to "flexible",
                "expand[]" to "latest_invoice.confirmation_secret",
                "expand[]" to "latest_invoice.payment_intent",
                "metadata[athleteUserId]" to athleteUserId,
                "metadata[payerUserId]" to payerUserId
            )
        )

        val body = json.parseToJsonElement(response).jsonObject
        val subscriptionId = body.string("id")
            ?: throw IllegalArgumentException("Stripe subscription response is missing id")
        val latestInvoice = body.obj("latest_invoice")
        val clientSecret = latestInvoice
            ?.obj("confirmation_secret")
            ?.string("client_secret")
            ?: latestInvoice
                ?.obj("payment_intent")
                ?.string("client_secret")
            ?: throw IllegalArgumentException("Stripe subscription response is missing payment client secret")

        StripeCheckoutResult(
            customerId = customerId,
            subscriptionId = subscriptionId,
            clientSecret = clientSecret,
            priceId = configuredPriceId
        )
    }

    suspend fun retrieveSubscriptionState(subscriptionId: String): StripeSubscriptionState = withContext(Dispatchers.IO) {
        val configuredSecretKey = secretKey?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Stripe secret key is not configured")

        val response = get(
            secretKey = configuredSecretKey,
            path = "/v1/subscriptions/$subscriptionId"
        )
        parseSubscriptionObject(json.parseToJsonElement(response).jsonObject)
    }

    fun parseWebhookUpdate(payload: String, signatureHeader: String?): StripeWebhookUpdate? {
        verifyWebhookSignatureIfConfigured(payload, signatureHeader)

        val event = json.parseToJsonElement(payload).jsonObject
        val type = event.string("type") ?: return null
        val obj = event.obj("data")?.obj("object") ?: return null

        return when {
            type.startsWith("customer.subscription.") -> parseSubscriptionObject(obj).let {
                StripeWebhookUpdate(
                    subscriptionId = it.subscriptionId,
                    status = it.status,
                    currentPeriodEndsAt = it.currentPeriodEndsAt,
                    cancelAtPeriodEnd = it.cancelAtPeriodEnd
                )
            }
            type == "invoice.payment_failed" -> {
                val subscriptionId = obj.string("subscription") ?: return null
                StripeWebhookUpdate(
                    subscriptionId = subscriptionId,
                    status = AthleteSubscriptionStatus.PAST_DUE,
                    currentPeriodEndsAt = null,
                    cancelAtPeriodEnd = false
                )
            }
            else -> null
        }
    }

    private fun createCustomer(
        secretKey: String,
        payerUserId: String,
        payerName: String,
        payerEmail: String?
    ): String {
        val fields = buildList {
            add("name" to payerName)
            payerEmail?.takeIf { it.isNotBlank() }?.let { add("email" to it) }
            add("metadata[payerUserId]" to payerUserId)
        }

        val response = postForm(
            secretKey = secretKey,
            path = "/v1/customers",
            fields = fields
        )

        return json.parseToJsonElement(response).jsonObject.string("id")
            ?: throw IllegalArgumentException("Stripe customer response is missing id")
    }

    private fun parseSubscriptionObject(obj: JsonObject): StripeSubscriptionState {
        val subscriptionId = obj.string("id")
            ?: throw IllegalArgumentException("Stripe subscription object is missing id")
        val stripeStatus = obj.string("status") ?: "incomplete"

        return StripeSubscriptionState(
            subscriptionId = subscriptionId,
            status = when (stripeStatus) {
                "active", "trialing" -> AthleteSubscriptionStatus.ACTIVE
                "past_due", "unpaid", "incomplete", "incomplete_expired" -> AthleteSubscriptionStatus.PAST_DUE
                "canceled" -> AthleteSubscriptionStatus.CANCELED
                else -> AthleteSubscriptionStatus.PAST_DUE
            },
            currentPeriodEndsAt = obj.long("current_period_end")?.times(1000L),
            cancelAtPeriodEnd = obj.boolean("cancel_at_period_end") ?: false
        )
    }

    private fun postForm(
        secretKey: String,
        path: String,
        fields: List<Pair<String, String>>
    ): String {
        val body = fields.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.stripe.com$path"))
            .header("Authorization", "Bearer $secretKey")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        return send(request)
    }

    private fun get(secretKey: String, path: String): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.stripe.com$path"))
            .header("Authorization", "Bearer $secretKey")
            .GET()
            .build()

        return send(request)
    }

    private fun send(request: HttpRequest): String {
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalArgumentException("Stripe request failed: ${response.body()}")
        }
        return response.body()
    }

    private fun verifyWebhookSignatureIfConfigured(payload: String, signatureHeader: String?) {
        val configuredSecret = webhookSecret?.takeIf { it.isNotBlank() } ?: return
        val header = signatureHeader ?: throw IllegalArgumentException("Missing Stripe signature")
        val timestamp = header.split(",")
            .firstOrNull { it.startsWith("t=") }
            ?.substringAfter("t=")
            ?: throw IllegalArgumentException("Invalid Stripe signature")
        val expectedSignature = header.split(",")
            .filter { it.startsWith("v1=") }
            .map { it.substringAfter("v1=") }
            .firstOrNull()
            ?: throw IllegalArgumentException("Invalid Stripe signature")

        val signedPayload = "$timestamp.$payload"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(configuredSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val actualSignature = mac.doFinal(signedPayload.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        if (!MessageDigest.isEqual(
                actualSignature.toByteArray(StandardCharsets.UTF_8),
                expectedSignature.toByteArray(StandardCharsets.UTF_8)
            )
        ) {
            throw IllegalArgumentException("Invalid Stripe signature")
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.content

    private fun JsonObject.long(key: String): Long? =
        this[key]?.jsonPrimitive?.longOrNull

    private fun JsonObject.boolean(key: String): Boolean? =
        this[key]?.jsonPrimitive?.booleanOrNull

    private fun JsonObject.obj(key: String): JsonObject? =
        (this[key] as? JsonObject)
}
