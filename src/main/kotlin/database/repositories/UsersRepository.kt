package com.provingground.database.repositories

import com.provingground.database.dbQuery
import com.provingground.database.tables.ParentToChildrenTable
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
            it[avatarUrl] = user.avatarUrl
            it[position] = user.position
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
            it[avatarUrl] = user.avatarUrl
            it[position] = user.position
        } > 0
    }

    suspend fun update(id: UUID, user: User): Boolean = dbQuery {
        updateTx(id, user)
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