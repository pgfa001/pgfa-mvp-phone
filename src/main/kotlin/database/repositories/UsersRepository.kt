package com.provingground.database.repositories

import com.provingground.database.dbQuery
import com.provingground.database.tables.AthleteSubscriptionStatus
import com.provingground.database.tables.AthleteSubscriptionsTable
import com.provingground.database.tables.ChallengeDemoUploadIntentsTable
import com.provingground.database.tables.ChallengeSubmissionsTable
import com.provingground.database.tables.ChallengeUploadIntentsTable
import com.provingground.database.tables.ChallengesTable
import com.provingground.database.tables.ClubAdminsTable
import com.provingground.database.tables.ClubLogoUploadIntentsTable
import com.provingground.database.tables.ClubToUsersTable
import com.provingground.database.tables.ConsentsTable
import com.provingground.database.tables.ParentToChildrenTable
import com.provingground.database.tables.TeamsToUsersTable
import com.provingground.database.tables.UserRole
import com.provingground.database.tables.UsersTable
import com.provingground.database.toParentChildRelationship
import com.provingground.database.toUser
import com.provingground.datamodels.ParentChildRelationship
import com.provingground.datamodels.User
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.UUID

class UsersRepository {

    fun createTx(user: User): User {
        UsersTable.insert {
            it[id] = user.id
            it[name] = user.name
            it[username] = user.username
            it[password] = user.password
            it[email] = user.email
            it[phone] = user.phone
            it[role] = user.role
            it[dob] = user.dob
            it[gender] = user.gender
            it[avatarUrl] = user.avatarUrl
            it[position] = user.position
            it[state] = user.state
            it[town] = user.town
            it[socialMediaHandle] = user.socialMediaHandle
            it[createdAt] = user.createdAt
        }
        return user
    }

    suspend fun create(user: User): User = dbQuery {
        createTx(user)
    }

    fun getByIdTx(id: UUID): User? {
        return UsersTable
            .selectAll()
            .where { UsersTable.id eq id }
            .singleOrNull()
            ?.toUser()
    }

    fun getByIdsTx(ids: List<UUID>): List<User> {
        if (ids.isEmpty()) return emptyList()

        return UsersTable
            .selectAll()
            .where { UsersTable.id inList ids }
            .map { it.toUser() }
    }

    suspend fun getById(id: UUID): User? = dbQuery {
        getByIdTx(id)
    }

    fun getAllTx(): List<User> {
        return UsersTable.selectAll().map { it.toUser() }
    }

    fun getUsersForClubIdsTx(clubIds: Collection<UUID>): List<User> {
        if (clubIds.isEmpty()) return emptyList()

        return (UsersTable innerJoin ClubToUsersTable)
            .selectAll()
            .where { ClubToUsersTable.clubId inList clubIds }
            .map { it.toUser() }
            .distinctBy { it.id }
    }

    suspend fun getAll(): List<User> = dbQuery {
        getAllTx()
    }

    fun getByUsernameTx(username: String): User? {
        return UsersTable
            .selectAll()
            .where { UsersTable.username eq username }
            .singleOrNull()
            ?.toUser()
    }

    suspend fun getByUsername(username: String): User? = dbQuery {
        getByUsernameTx(username)
    }

    fun getByEmailTx(email: String): User? {
        return UsersTable
            .selectAll()
            .where { UsersTable.email eq email }
            .singleOrNull()
            ?.toUser()
    }

    suspend fun getByEmail(email: String): User? = dbQuery {
        getByEmailTx(email)
    }

    fun isParentOfChildTx(parentUserId: UUID, childUserId: UUID): Boolean {
        return ParentToChildrenTable
            .selectAll()
            .where {
                (ParentToChildrenTable.parentUserId eq parentUserId) and
                        (ParentToChildrenTable.childUserId eq childUserId)
            }
            .any()
    }

    fun getChildrenIdsForParentTx(parentUserId: UUID): List<UUID> {
        return ParentToChildrenTable
            .select(ParentToChildrenTable.childUserId)
            .where { ParentToChildrenTable.parentUserId eq parentUserId }
            .map { it[ParentToChildrenTable.childUserId] }
    }

    fun usernameExistsTx(username: String): Boolean {
        return UsersTable
            .selectAll()
            .where { UsersTable.username eq username }
            .any()
    }

    suspend fun usernameExists(username: String): Boolean = dbQuery {
        usernameExistsTx(username)
    }

    fun emailExistsTx(email: String): Boolean {
        return UsersTable
            .selectAll()
            .where { UsersTable.email eq email }
            .any()
    }

    suspend fun emailExists(email: String): Boolean = dbQuery {
        emailExistsTx(email)
    }

    fun updateTx(id: UUID, user: User): Boolean {
        return UsersTable.update({ UsersTable.id eq id }) {
            it[name] = user.name
            it[username] = user.username
            it[password] = user.password
            it[email] = user.email
            it[phone] = user.phone
            it[role] = user.role
            it[dob] = user.dob
            it[gender] = user.gender
            it[avatarUrl] = user.avatarUrl
            it[position] = user.position
            it[state] = user.state
            it[town] = user.town
            it[socialMediaHandle] = user.socialMediaHandle
        } > 0
    }

    suspend fun update(id: UUID, user: User): Boolean = dbQuery {
        updateTx(id, user)
    }

    fun updatePasswordTx(id: UUID, passwordHash: String): Boolean {
        return UsersTable.update({ UsersTable.id eq id }) {
            it[password] = passwordHash
        } > 0
    }

    suspend fun updatePassword(id: UUID, passwordHash: String): Boolean = dbQuery {
        updatePasswordTx(id, passwordHash)
    }

    fun updateUsernameTx(id: UUID, username: String): Boolean {
        return UsersTable.update({ UsersTable.id eq id }) {
            it[UsersTable.username] = username
        } > 0
    }

    suspend fun updateUsername(id: UUID, username: String): Boolean = dbQuery {
        updateUsernameTx(id, username)
    }

    fun hasCreatedChallengesTx(id: UUID): Boolean {
        return ChallengesTable
            .select(ChallengesTable.id)
            .where { ChallengesTable.createdBy eq id }
            .any()
    }

    fun deleteUserFullyTx(id: UUID): Boolean {
        if (hasCreatedChallengesTx(id)) {
            throw IllegalArgumentException("User cannot be deleted because they have created challenges")
        }

        val hasStartedStripeSubscription = AthleteSubscriptionsTable
            .select(AthleteSubscriptionsTable.id)
            .where {
                (AthleteSubscriptionsTable.athleteUserId eq id) and
                        (AthleteSubscriptionsTable.stripeSubscriptionId.isNotNull()) and
                        (AthleteSubscriptionsTable.status inList listOf(
                            AthleteSubscriptionStatus.TRIALING,
                            AthleteSubscriptionStatus.ACTIVE,
                            AthleteSubscriptionStatus.PAST_DUE
                        ))
            }
            .any()
        if (hasStartedStripeSubscription) {
            throw IllegalArgumentException("User cannot be deleted until their Stripe subscription is canceled")
        }

        ChallengeSubmissionsTable.update({ ChallengeSubmissionsTable.validatedBy eq id }) {
            it[validatedBy] = null
        }
        AthleteSubscriptionsTable.update({ AthleteSubscriptionsTable.payerUserId eq id }) {
            it[payerUserId] = null
        }

        ChallengeUploadIntentsTable.deleteWhere {
            (actingUserId eq id) or (athleteUserId eq id)
        }
        ChallengeDemoUploadIntentsTable.deleteWhere { actingUserId eq id }
        ClubLogoUploadIntentsTable.deleteWhere { actingUserId eq id }
        ChallengeSubmissionsTable.deleteWhere { userId eq id }
        AthleteSubscriptionsTable.deleteWhere { athleteUserId eq id }
        ConsentsTable.deleteWhere { userId eq id }
        TeamsToUsersTable.deleteWhere { userId eq id }
        ClubAdminsTable.deleteWhere { userId eq id }
        ClubToUsersTable.deleteWhere { userId eq id }
        ParentToChildrenTable.deleteWhere {
            (parentUserId eq id) or (childUserId eq id)
        }

        return UsersTable.deleteWhere { UsersTable.id eq id } > 0
    }

    suspend fun deleteUserFully(id: UUID): Boolean = dbQuery {
        deleteUserFullyTx(id)
    }

    fun deleteTx(id: UUID): Boolean {
        return UsersTable.deleteWhere { UsersTable.id eq id } > 0
    }

    suspend fun delete(id: UUID): Boolean = dbQuery {
        deleteTx(id)
    }

    fun addChildToParentTx(
        parentUserId: UUID,
        childUserId: UUID,
        relationshipId: UUID = UUID.randomUUID(),
        createdAt: Long = System.currentTimeMillis()
    ): ParentChildRelationship {
        ParentToChildrenTable.insert {
            it[id] = relationshipId
            it[ParentToChildrenTable.parentUserId] = parentUserId
            it[ParentToChildrenTable.childUserId] = childUserId
            it[ParentToChildrenTable.createdAt] = createdAt
        }

        return ParentChildRelationship(
            id = relationshipId,
            parentUserId = parentUserId,
            childUserId = childUserId,
            createdAt = createdAt
        )
    }

    suspend fun addChildToParent(
        parentUserId: UUID,
        childUserId: UUID,
        relationshipId: UUID = UUID.randomUUID(),
        createdAt: Long = System.currentTimeMillis()
    ): ParentChildRelationship = dbQuery {
        addChildToParentTx(parentUserId, childUserId, relationshipId, createdAt)
    }

    fun getChildrenForParentTx(parentUserId: UUID): List<User> {
        return UsersTable.join(
            otherTable = ParentToChildrenTable,
            joinType = JoinType.INNER,
            onColumn = UsersTable.id,
            otherColumn = ParentToChildrenTable.childUserId,
        )
            .selectAll()
            .where { ParentToChildrenTable.parentUserId eq parentUserId }
            .map { it.toUser() }
    }

    suspend fun getChildrenForParent(parentUserId: UUID): List<User> = dbQuery {
        getChildrenForParentTx(parentUserId)
    }

    fun getParentsForChildTx(childUserId: UUID): List<User> {
        return UsersTable.join(
            otherTable = ParentToChildrenTable,
            joinType = JoinType.INNER,
            onColumn = UsersTable.id,
            otherColumn = ParentToChildrenTable.childUserId,
        )
            .selectAll()
            .where { ParentToChildrenTable.childUserId eq childUserId }
            .map { it.toUser() }
    }

    suspend fun getParentsForChild(childUserId: UUID): List<User> = dbQuery {
        getParentsForChildTx(childUserId)
    }

    fun getParentChildRelationshipsTx(parentUserId: UUID): List<ParentChildRelationship> {
        return ParentToChildrenTable
            .selectAll()
            .where { ParentToChildrenTable.parentUserId eq parentUserId }
            .map { it.toParentChildRelationship() }
    }

    suspend fun getParentChildRelationships(parentUserId: UUID): List<ParentChildRelationship> = dbQuery {
        getParentChildRelationshipsTx(parentUserId)
    }
}
