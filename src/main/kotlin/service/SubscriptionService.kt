package com.provingground.service

import com.provingground.database.repositories.AthleteSubscriptionsRepository
import com.provingground.database.repositories.ClubsRepository
import com.provingground.database.repositories.UsersRepository
import com.provingground.database.tables.AthleteSubscriptionStatus
import com.provingground.database.tables.SubscriptionType
import com.provingground.database.tables.UserRole
import com.provingground.datamodels.AthleteSubscription
import com.provingground.datamodels.User
import com.provingground.datamodels.response.AthleteSubscriptionResponse
import com.provingground.datamodels.response.ConfirmSubscriptionResponse
import com.provingground.datamodels.response.CreateSubscriptionCheckoutResponse
import com.provingground.datamodels.response.GetMySubscriptionsResponse
import com.provingground.datamodels.response.SubscriptionEntitlementResponse
import com.provingground.datamodels.response.SubscriptionEntitlementSource
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

class SubscriptionRequiredException(
    val entitlement: SubscriptionEntitlementResponse
) : RuntimeException("Subscription required")

class SubscriptionService(
    private val usersRepository: UsersRepository,
    private val clubsRepository: ClubsRepository,
    private val subscriptionsRepository: AthleteSubscriptionsRepository,
    private val stripeBillingService: StripeBillingService
) {
    fun createTrialForAthleteTx(
        athleteUserId: UUID,
        payerUserId: UUID?,
        now: Long = System.currentTimeMillis()
    ): AthleteSubscription {
        return subscriptionsRepository.createTrialIfMissingTx(
            athleteUserId = athleteUserId,
            payerUserId = payerUserId,
            now = now
        )
    }

    suspend fun getMySubscriptions(actingUserId: UUID): GetMySubscriptionsResponse =
        newSuspendedTransaction(Dispatchers.IO) {
            val user = usersRepository.getByIdTx(actingUserId)
                ?: throw IllegalArgumentException("User not found")

            val athletes = when (user.role) {
                UserRole.ATHLETE -> emptyList()
                UserRole.PARENT -> usersRepository.getChildrenForParentTx(user.id)
                    .filter { it.role == UserRole.ATHLETE }
                    .map { child ->
                        getAthleteSubscriptionResponseTx(child)
                    }
                UserRole.COACH, UserRole.ADMIN, UserRole.SUPERADMIN -> emptyList()
            }

            GetMySubscriptionsResponse(
                userId = user.id.toString(),
                role = user.role,
                subscription = if (user.role == UserRole.ATHLETE) getEntitlementForAthleteTx(user) else null,
                athletes = athletes
            )
        }

    suspend fun createCheckout(
        actingUserId: UUID,
        athleteUserId: String
    ): CreateSubscriptionCheckoutResponse {
        val preparation = newSuspendedTransaction(Dispatchers.IO) {
            val actingUser = usersRepository.getByIdTx(actingUserId)
                ?: throw IllegalArgumentException("User not found")
            val athlete = resolveCheckoutAthleteTx(actingUser, athleteUserId)
            val entitlement = getEntitlementForAthleteTx(athlete)

            if (entitlement.source == SubscriptionEntitlementSource.CLUB_PAID) {
                throw IllegalArgumentException("This athlete belongs to a club-paid club and does not need an individual subscription")
            }

            val subscription = subscriptionsRepository.getByAthleteUserIdTx(athlete.id)
                ?: createTrialForAthleteTx(athlete.id, actingUser.id)

            CheckoutPreparation(
                payer = actingUser,
                athlete = athlete,
                subscription = subscription
            )
        }

        val checkout = stripeBillingService.createSubscriptionCheckout(
            payerUserId = preparation.payer.id.toString(),
            payerName = preparation.payer.name,
            payerEmail = preparation.payer.email,
            athleteUserId = preparation.athlete.id.toString(),
            existingCustomerId = preparation.subscription.stripeCustomerId
        )

        newSuspendedTransaction(Dispatchers.IO) {
            subscriptionsRepository.updateCheckoutSessionTx(
                athleteUserId = preparation.athlete.id,
                payerUserId = preparation.payer.id,
                stripeCustomerId = checkout.customerId,
                stripeSubscriptionId = checkout.subscriptionId,
                stripePriceId = checkout.priceId
            )
        }

        return CreateSubscriptionCheckoutResponse(
            athleteUserId = preparation.athlete.id.toString(),
            customerId = checkout.customerId,
            subscriptionId = checkout.subscriptionId,
            paymentIntentClientSecret = checkout.clientSecret,
            clientSecret = checkout.clientSecret
        )
    }

    suspend fun confirmSubscription(
        actingUserId: UUID,
        athleteUserId: String
    ): ConfirmSubscriptionResponse {
        val athlete = newSuspendedTransaction(Dispatchers.IO) {
            val actingUser = usersRepository.getByIdTx(actingUserId)
                ?: throw IllegalArgumentException("User not found")
            resolveCheckoutAthleteTx(actingUser, athleteUserId)
        }

        val subscription = newSuspendedTransaction(Dispatchers.IO) {
            subscriptionsRepository.getByAthleteUserIdTx(athlete.id)
                ?: throw IllegalArgumentException("Subscription not found")
        }

        val stripeSubscriptionId = subscription.stripeSubscriptionId
            ?: throw IllegalArgumentException("Subscription has not been started")

        val stripeState = stripeBillingService.retrieveSubscriptionState(stripeSubscriptionId)

        newSuspendedTransaction(Dispatchers.IO) {
            subscriptionsRepository.updateStripeStateTx(
                stripeSubscriptionId = stripeState.subscriptionId,
                status = stripeState.status,
                currentPeriodEndsAt = stripeState.currentPeriodEndsAt,
                cancelAtPeriodEnd = stripeState.cancelAtPeriodEnd
            )
        }

        return newSuspendedTransaction(Dispatchers.IO) {
            ConfirmSubscriptionResponse(
                athleteUserId = athlete.id.toString(),
                subscription = getEntitlementForAthleteTx(athlete)
            )
        }
    }

    suspend fun handleStripeWebhook(payload: String, signatureHeader: String?): Boolean {
        val update = stripeBillingService.parseWebhookUpdate(payload, signatureHeader) ?: return false

        return newSuspendedTransaction(Dispatchers.IO) {
            subscriptionsRepository.updateStripeStateTx(
                stripeSubscriptionId = update.subscriptionId,
                status = update.status,
                currentPeriodEndsAt = update.currentPeriodEndsAt,
                cancelAtPeriodEnd = update.cancelAtPeriodEnd
            )
        }
    }

    fun getEntitlementForAthleteTx(athlete: User): SubscriptionEntitlementResponse {
        if (athlete.role != UserRole.ATHLETE) {
            return SubscriptionEntitlementResponse(
                hasAccess = true,
                source = SubscriptionEntitlementSource.NOT_REQUIRED,
                upgradeRequired = false
            )
        }

        if (isClubPaidAthleteTx(athlete.id)) {
            return SubscriptionEntitlementResponse(
                status = null,
                hasAccess = true,
                source = SubscriptionEntitlementSource.CLUB_PAID,
                upgradeRequired = false
            )
        }

        val subscription = subscriptionsRepository.getByAthleteUserIdTx(athlete.id)
            ?: subscriptionsRepository.createTrialIfMissingTx(
                athleteUserId = athlete.id,
                payerUserId = athlete.id
            )

        val now = System.currentTimeMillis()
        val hasTrialAccess = subscription.status == AthleteSubscriptionStatus.TRIALING &&
                subscription.trialEndsAt?.let { it > now } == true
        val hasPaidAccess = subscription.status == AthleteSubscriptionStatus.ACTIVE &&
                subscription.currentPeriodEndsAt?.let { it > now } != false
        val hasAccess = hasTrialAccess || hasPaidAccess

        if (!hasAccess &&
            subscription.status == AthleteSubscriptionStatus.TRIALING &&
            subscription.trialEndsAt?.let { it <= now } == true
        ) {
            subscriptionsRepository.expireTrialTx(athlete.id, now)
        }

        return SubscriptionEntitlementResponse(
            status = if (!hasAccess && subscription.status == AthleteSubscriptionStatus.TRIALING) {
                AthleteSubscriptionStatus.EXPIRED
            } else {
                subscription.status
            },
            hasAccess = hasAccess,
            source = SubscriptionEntitlementSource.ATHLETE_PAID,
            trialStartedAt = subscription.trialStartedAt,
            trialEndsAt = subscription.trialEndsAt,
            currentPeriodEndsAt = subscription.currentPeriodEndsAt,
            cancelAtPeriodEnd = subscription.cancelAtPeriodEnd,
            upgradeRequired = !hasAccess
        )
    }

    fun getAthleteSubscriptionResponseTx(athlete: User): AthleteSubscriptionResponse {
        if (athlete.role != UserRole.ATHLETE) {
            throw IllegalArgumentException("Subscription response can only be built for athletes")
        }

        return AthleteSubscriptionResponse(
            athleteUserId = athlete.id.toString(),
            athleteName = athlete.name,
            subscription = getEntitlementForAthleteTx(athlete)
        )
    }

    fun requireAccessForAthleteTx(athlete: User) {
        val entitlement = getEntitlementForAthleteTx(athlete)
        if (!entitlement.hasAccess) {
            throw SubscriptionRequiredException(entitlement)
        }
    }

    private fun resolveCheckoutAthleteTx(actingUser: User, athleteUserId: String): User {
        val athleteUuid = try {
            UUID.fromString(athleteUserId)
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid athleteUserId")
        }

        val athlete = usersRepository.getByIdTx(athleteUuid)
            ?: throw IllegalArgumentException("Athlete not found")

        if (athlete.role != UserRole.ATHLETE) {
            throw IllegalArgumentException("Subscriptions can only be created for athlete accounts")
        }

        when (actingUser.role) {
            UserRole.ATHLETE -> {
                if (actingUser.id != athlete.id) {
                    throw IllegalArgumentException("Athletes may only subscribe for themselves")
                }
            }
            UserRole.PARENT -> {
                if (!usersRepository.isParentOfChildTx(actingUser.id, athlete.id)) {
                    throw IllegalArgumentException("Parents may only subscribe for their own children")
                }
            }
            UserRole.COACH, UserRole.ADMIN, UserRole.SUPERADMIN -> {
                throw IllegalArgumentException("This user role does not require athlete subscriptions")
            }
        }

        return athlete
    }

    private fun isClubPaidAthleteTx(athleteUserId: UUID): Boolean {
        return clubsRepository.getClubsForUserTx(athleteUserId)
            .any { it.subscriptionType == SubscriptionType.CLUB_PAID }
    }

    private data class CheckoutPreparation(
        val payer: User,
        val athlete: User,
        val subscription: AthleteSubscription
    )
}
