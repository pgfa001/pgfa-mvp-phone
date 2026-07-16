package com.provingground.database.repositories

import com.provingground.database.dbQuery
import com.provingground.database.tables.AthleteSubscriptionStatus
import com.provingground.database.tables.AthleteSubscriptionsTable
import com.provingground.database.toAthleteSubscription
import com.provingground.datamodels.AthleteSubscription
import org.jetbrains.exposed.sql.*
import java.util.UUID

class AthleteSubscriptionsRepository {
    fun createTx(subscription: AthleteSubscription): AthleteSubscription {
        AthleteSubscriptionsTable.insert {
            it[id] = subscription.id
            it[athleteUserId] = subscription.athleteUserId
            it[payerUserId] = subscription.payerUserId
            it[status] = subscription.status
            it[trialStartedAt] = subscription.trialStartedAt
            it[trialEndsAt] = subscription.trialEndsAt
            it[stripeCustomerId] = subscription.stripeCustomerId
            it[stripeSubscriptionId] = subscription.stripeSubscriptionId
            it[stripePriceId] = subscription.stripePriceId
            it[currentPeriodEndsAt] = subscription.currentPeriodEndsAt
            it[cancelAtPeriodEnd] = subscription.cancelAtPeriodEnd
            it[createdAt] = subscription.createdAt
            it[updatedAt] = subscription.updatedAt
        }

        return subscription
    }

    suspend fun create(subscription: AthleteSubscription): AthleteSubscription = dbQuery {
        createTx(subscription)
    }

    fun createTrialIfMissingTx(
        athleteUserId: UUID,
        payerUserId: UUID?,
        now: Long = System.currentTimeMillis()
    ): AthleteSubscription {
        val existing = getByAthleteUserIdTx(athleteUserId)
        if (existing != null) return existing

        return createTx(
            AthleteSubscription(
                id = UUID.randomUUID(),
                athleteUserId = athleteUserId,
                payerUserId = payerUserId,
                status = AthleteSubscriptionStatus.TRIALING,
                trialStartedAt = now,
                trialEndsAt = now + FREE_TRIAL_DURATION_MS,
                stripeCustomerId = null,
                stripeSubscriptionId = null,
                stripePriceId = null,
                currentPeriodEndsAt = null,
                cancelAtPeriodEnd = false,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    fun getByAthleteUserIdTx(athleteUserId: UUID): AthleteSubscription? {
        return AthleteSubscriptionsTable
            .selectAll()
            .where { AthleteSubscriptionsTable.athleteUserId eq athleteUserId }
            .singleOrNull()
            ?.toAthleteSubscription()
    }

    fun getByAthleteUserIdsTx(athleteUserIds: Collection<UUID>): List<AthleteSubscription> {
        if (athleteUserIds.isEmpty()) return emptyList()

        return AthleteSubscriptionsTable
            .selectAll()
            .where { AthleteSubscriptionsTable.athleteUserId inList athleteUserIds }
            .map { it.toAthleteSubscription() }
    }

    fun getByStripeSubscriptionIdTx(stripeSubscriptionId: String): AthleteSubscription? {
        return AthleteSubscriptionsTable
            .selectAll()
            .where { AthleteSubscriptionsTable.stripeSubscriptionId eq stripeSubscriptionId }
            .singleOrNull()
            ?.toAthleteSubscription()
    }

    fun getActiveStripeSubscriptionsForUserDeletionTx(userId: UUID): List<AthleteSubscription> {
        return AthleteSubscriptionsTable
            .selectAll()
            .where {
                ((AthleteSubscriptionsTable.athleteUserId eq userId) or
                        (AthleteSubscriptionsTable.payerUserId eq userId)) and
                        AthleteSubscriptionsTable.stripeSubscriptionId.isNotNull() and
                        (AthleteSubscriptionsTable.status inList listOf(
                            AthleteSubscriptionStatus.TRIALING,
                            AthleteSubscriptionStatus.ACTIVE,
                            AthleteSubscriptionStatus.PAST_DUE
                        ))
            }
            .map { it.toAthleteSubscription() }
    }

    fun updateCheckoutSessionTx(
        athleteUserId: UUID,
        payerUserId: UUID,
        stripeCustomerId: String,
        stripeSubscriptionId: String,
        stripePriceId: String,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        return AthleteSubscriptionsTable.update({ AthleteSubscriptionsTable.athleteUserId eq athleteUserId }) {
            it[AthleteSubscriptionsTable.payerUserId] = payerUserId
            it[AthleteSubscriptionsTable.stripeCustomerId] = stripeCustomerId
            it[AthleteSubscriptionsTable.stripeSubscriptionId] = stripeSubscriptionId
            it[AthleteSubscriptionsTable.stripePriceId] = stripePriceId
            it[updatedAt] = now
        } > 0
    }

    fun updateStripeStateTx(
        stripeSubscriptionId: String,
        status: AthleteSubscriptionStatus,
        currentPeriodEndsAt: Long?,
        cancelAtPeriodEnd: Boolean,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        return AthleteSubscriptionsTable.update({ AthleteSubscriptionsTable.stripeSubscriptionId eq stripeSubscriptionId }) {
            it[AthleteSubscriptionsTable.status] = status
            it[AthleteSubscriptionsTable.currentPeriodEndsAt] = currentPeriodEndsAt
            it[AthleteSubscriptionsTable.cancelAtPeriodEnd] = cancelAtPeriodEnd
            it[updatedAt] = now
        } > 0
    }

    fun expireTrialTx(
        athleteUserId: UUID,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        return AthleteSubscriptionsTable.update({ AthleteSubscriptionsTable.athleteUserId eq athleteUserId }) {
            it[AthleteSubscriptionsTable.status] = AthleteSubscriptionStatus.EXPIRED
            it[updatedAt] = now
        } > 0
    }

    companion object {
        const val FREE_TRIAL_DURATION_MS: Long = 3L * 24L * 60L * 60L * 1000L
    }
}
