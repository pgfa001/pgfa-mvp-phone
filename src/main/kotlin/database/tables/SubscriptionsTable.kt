package com.provingground.database.tables

import org.jetbrains.exposed.sql.Table

enum class AthleteSubscriptionStatus {
    NONE,
    TRIALING,
    ACTIVE,
    PAST_DUE,
    CANCELED,
    EXPIRED
}

object AthleteSubscriptionsTable : Table("athlete_subscriptions") {
    val id = uuid("id")
    val athleteUserId = uuid("athlete_user_id").references(UsersTable.id)
    val payerUserId = uuid("payer_user_id").references(UsersTable.id).nullable()
    val status = enumerationByName("status", 50, AthleteSubscriptionStatus::class)
    val trialStartedAt = long("trial_started_at").nullable()
    val trialEndsAt = long("trial_ends_at").nullable()
    val stripeCustomerId = varchar("stripe_customer_id", 255).nullable()
    val stripeSubscriptionId = varchar("stripe_subscription_id", 255).nullable()
    val stripePriceId = varchar("stripe_price_id", 255).nullable()
    val currentPeriodEndsAt = long("current_period_ends_at").nullable()
    val cancelAtPeriodEnd = bool("cancel_at_period_end")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(athleteUserId)
        index(isUnique = true, stripeSubscriptionId)
    }
}
