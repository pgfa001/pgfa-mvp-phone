package com.provingground.service

import com.provingground.PasswordHasher
import com.provingground.database.repositories.ClubsRepository
import com.provingground.database.repositories.UsersRepository
import com.provingground.database.tables.UserRole
import com.provingground.datamodels.Club
import com.provingground.datamodels.ClubLogoUploadIntent
import com.provingground.datamodels.ClubSummaryResponse
import com.provingground.datamodels.GetClubsResponse
import com.provingground.datamodels.response.ClubCmsResponse
import com.provingground.datamodels.response.ClubAdminResponse
import com.provingground.datamodels.response.ClubDetailsResponse
import com.provingground.datamodels.response.CreateClubAdminRequest
import com.provingground.datamodels.response.CreateClubLogoUploadUrlRequest
import com.provingground.datamodels.response.CreateClubLogoUploadUrlResponse
import com.provingground.datamodels.response.CreateClubRequest
import com.provingground.datamodels.response.GetClubLogoUrlResponse
import com.provingground.datamodels.response.UpdateClubRequest
import com.provingground.datamodels.toCmsResponse
import com.provingground.datamodels.User
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

class ClubsService(
    private val clubsRepository: ClubsRepository,
    private val usersRepository: UsersRepository,
    private val videoStorageService: VideoStorageService,
    private val passwordHasher: PasswordHasher,
) {
    suspend fun createClub(
        actingUserId: UUID,
        request: CreateClubRequest
    ): ClubCmsResponse = newSuspendedTransaction(Dispatchers.IO) {
        val actingUser = usersRepository.getByIdTx(actingUserId)
            ?: throw IllegalArgumentException("User not found")

        if (actingUser.role != UserRole.SUPERADMIN) {
            throw IllegalArgumentException("Only super admins can create clubs")
        }

        if (request.name.isBlank()) throw IllegalArgumentException("Club name is required")
        if (request.accessCode.isBlank()) throw IllegalArgumentException("Access code is required")

        val existing = clubsRepository.getByAccessCodeTx(request.accessCode)
        if (existing != null) {
            throw IllegalArgumentException("Access code already in use")
        }

        if (request.logoObjectKey != null) {
            val intent = clubsRepository.getLogoUploadIntentByObjectKeyTx(request.logoObjectKey)
                ?: throw IllegalArgumentException("Logo upload intent not found")

            val now = System.currentTimeMillis()
            if (intent.actingUserId != actingUser.id) throw IllegalArgumentException("Logo upload intent does not belong to user")
            if (intent.consumedAt != null) throw IllegalArgumentException("Logo upload intent already used")
            if (intent.expiresAt < now) throw IllegalArgumentException("Logo upload intent expired")

            clubsRepository.markLogoUploadIntentConsumedTx(intent.id, now)
        }

        val club = Club(
            id = UUID.randomUUID(),
            name = request.name.trim(),
            logoUrl = request.logoObjectKey ?: "",
            accessCode = request.accessCode.trim(),
            primaryColor = request.primaryColor,
            accentColor = request.accentColor,
            subscriptionType = request.subscriptionType,
            createdAt = System.currentTimeMillis()
        )

        clubsRepository.createTx(club)

        club.toCmsResponse()
    }

    suspend fun createClubAdmin(
        actingUserId: UUID,
        clubId: String,
        request: CreateClubAdminRequest
    ): ClubAdminResponse = newSuspendedTransaction(Dispatchers.IO) {
        val actingUser = usersRepository.getByIdTx(actingUserId)
            ?: throw IllegalArgumentException("User not found")

        if (actingUser.role != UserRole.SUPERADMIN) {
            throw IllegalArgumentException("Only super admins can create club admins")
        }

        val clubUuid = try {
            UUID.fromString(clubId)
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid clubId")
        }

        val club = clubsRepository.getByIdTx(clubUuid)
            ?: throw IllegalArgumentException("Club not found")

        if (request.name.isBlank()) throw IllegalArgumentException("Name is required")
        if (request.username.isBlank()) throw IllegalArgumentException("Username is required")
        if (request.password.isBlank()) throw IllegalArgumentException("Password is required")
        if (request.email.isBlank()) throw IllegalArgumentException("Email is required")

        val username = request.username.trim()
        val email = request.email.trim()

        if (usersRepository.usernameExistsTx(username)) {
            throw IllegalArgumentException("Username already exists")
        }

        if (usersRepository.emailExistsTx(email)) {
            throw IllegalArgumentException("Email already exists")
        }

        val now = System.currentTimeMillis()
        val admin = User(
            id = UUID.randomUUID(),
            name = request.name.trim(),
            username = username,
            password = passwordHasher.hash(request.password),
            email = email,
            phone = request.phone.trim(),
            role = UserRole.ADMIN,
            dob = request.dob,
            gender = null,
            avatarUrl = null,
            position = null,
            createdAt = now
        )

        usersRepository.createTx(admin)
        clubsRepository.addUserToClubTx(admin.id, club.id, createdAt = now)
        clubsRepository.addClubAdminTx(admin.id, club.id, createdAt = now)

        ClubAdminResponse(
            id = admin.id.toString(),
            clubId = club.id.toString(),
            name = admin.name,
            username = admin.username,
            email = admin.email ?: "",
            phone = admin.phone ?: "",
            role = admin.role,
            createdAt = admin.createdAt
        )
    }

    suspend fun updateClub(
        actingUserId: UUID,
        clubId: String,
        request: UpdateClubRequest
    ): ClubCmsResponse = newSuspendedTransaction(Dispatchers.IO) {
        val actingUser = usersRepository.getByIdTx(actingUserId)
            ?: throw IllegalArgumentException("User not found")

        if (actingUser.role != UserRole.SUPERADMIN) {
            throw IllegalArgumentException("Only super admins can update clubs")
        }

        val clubUuid = UUID.fromString(clubId)
        val existing = clubsRepository.getByIdTx(clubUuid)
            ?: throw IllegalArgumentException("Club not found")

        if (request.name.isBlank()) throw IllegalArgumentException("Club name is required")
        if (request.accessCode.isBlank()) throw IllegalArgumentException("Access code is required")

        val otherWithCode = clubsRepository.getByAccessCodeTx(request.accessCode)
        if (otherWithCode != null && otherWithCode.id != existing.id) {
            throw IllegalArgumentException("Access code already in use")
        }

        if (request.logoObjectKey != null && request.logoObjectKey != existing.logoUrl) {
            val intent = clubsRepository.getLogoUploadIntentByObjectKeyTx(request.logoObjectKey)
                ?: throw IllegalArgumentException("Logo upload intent not found")

            val now = System.currentTimeMillis()
            if (intent.actingUserId != actingUser.id) throw IllegalArgumentException("Logo upload intent does not belong to user")
            if (intent.consumedAt != null) throw IllegalArgumentException("Logo upload intent already used")
            if (intent.expiresAt < now) throw IllegalArgumentException("Logo upload intent expired")

            clubsRepository.markLogoUploadIntentConsumedTx(intent.id, now)
        }

        val updated = clubsRepository.updateTx(clubUuid, request)
        if (!updated) throw IllegalArgumentException("Failed to update club")

        ClubCmsResponse(
            id = existing.id.toString(),
            name = request.name.trim(),
            logoObjectKey = request.logoObjectKey,
            accessCode = request.accessCode.trim(),
            primaryColor = request.primaryColor,
            accentColor = request.accentColor,
            subscriptionType = request.subscriptionType,
            createdAt = existing.createdAt
        )
    }

    suspend fun deleteClub(
        actingUserId: UUID,
        clubId: String
    ) = newSuspendedTransaction(Dispatchers.IO) {
        val actingUser = usersRepository.getByIdTx(actingUserId)
            ?: throw IllegalArgumentException("User not found")

        if (actingUser.role != UserRole.SUPERADMIN) {
            throw IllegalArgumentException("Only super admins can delete clubs")
        }

        val clubUuid = UUID.fromString(clubId)

        val deleted = clubsRepository.deleteTx(clubUuid)
        if (!deleted) throw IllegalArgumentException("Club not found")
    }

    suspend fun createClubLogoUploadUrl(
        actingUserId: UUID,
        request: CreateClubLogoUploadUrlRequest
    ): CreateClubLogoUploadUrlResponse = newSuspendedTransaction(Dispatchers.IO) {
        val actingUser = usersRepository.getByIdTx(actingUserId)
            ?: throw IllegalArgumentException("User not found")

        if (actingUser.role != UserRole.SUPERADMIN) {
            throw IllegalArgumentException("Only super admins can upload club logos")
        }

        val safeFileName = request.fileName.substringAfterLast('/').substringAfterLast('\\')
        val extension = safeFileName.substringAfterLast('.', "")
        val intentId = UUID.randomUUID()

        val objectKey = buildString {
            append("club-logos/")
            append(actingUser.id)
            append("/")
            append(intentId)
            if (extension.isNotBlank()) {
                append(".")
                append(extension.lowercase())
            }
        }

        val presigned = videoStorageService.createUploadUrl(
            objectKey = objectKey,
            expiresInSeconds = 900
        )

        clubsRepository.createLogoUploadIntentTx(
            ClubLogoUploadIntent(
                id = intentId,
                actingUserId = actingUser.id,
                objectKey = objectKey,
                originalFileName = safeFileName,
                contentType = request.contentType,
                expiresAt = presigned.expiresAt,
                consumedAt = null,
                createdAt = System.currentTimeMillis()
            )
        )

        CreateClubLogoUploadUrlResponse(
            uploadIntentId = intentId.toString(),
            objectKey = objectKey,
            uploadUrl = presigned.uploadUrl,
            expiresAt = presigned.expiresAt
        )
    }

    suspend fun getClubLogoUrl(
        actingUserId: UUID,
        clubId: String
    ): GetClubLogoUrlResponse = newSuspendedTransaction(Dispatchers.IO) {
        val actingUser = usersRepository.getByIdTx(actingUserId)
            ?: throw IllegalArgumentException("User not found")

        if (actingUser.role != UserRole.SUPERADMIN) {
            throw IllegalArgumentException("Only super admins can view club logos from CMS")
        }

        val clubUuid = UUID.fromString(clubId)
        val club = clubsRepository.getByIdTx(clubUuid)
            ?: throw IllegalArgumentException("Club not found")

        val objectKey = club.logoUrl.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Club has no logo")

        val readUrl = videoStorageService.createReadUrl(objectKey, expiresInSeconds = 900)

        GetClubLogoUrlResponse(
            clubId = club.id.toString(),
            logoUrl = readUrl.readUrl,
            expiresAt = readUrl.expiresAt
        )
    }

    suspend fun getClubDetails(id: String?, accessCode: String?): ClubDetailsResponse? {
        val club = (if (!id.isNullOrBlank()) {
            val clubUuid = try {
                UUID.fromString(id)
            } catch (_: Exception) {
                throw IllegalArgumentException("Invalid club id")
            }

            clubsRepository.getById(clubUuid)
        } else if (!accessCode.isNullOrBlank()) {
            clubsRepository.getByAccessCode(accessCode)
        } else {
            throw IllegalArgumentException("Missing required query parameter: id or accessCode")
        }) ?: return null

        val readUrl = if (club.logoUrl.isNotEmpty())
            videoStorageService.createReadUrl(club.logoUrl, expiresInSeconds = 900).readUrl
        else ""

        return ClubDetailsResponse(
            id = club.id.toString(),
            name = club.name,
            logoUrl = readUrl,
            primaryColor = club.primaryColor,
            accentColor = club.accentColor,
            subscriptionType = club.subscriptionType.name
        )
    }

    suspend fun getAllClubs(actingUserId: UUID): GetClubsResponse =
        newSuspendedTransaction(Dispatchers.IO) {
            val actingUser = usersRepository.getByIdTx(actingUserId)
                ?: throw IllegalArgumentException("User not found")

            if (actingUser.role != UserRole.SUPERADMIN) {
                throw IllegalArgumentException("Only super admins can view clubs")
            }

            val clubs = clubsRepository.getAllTx()

            GetClubsResponse(
                clubs = clubs.map { club ->
                    val readUrl = if (club.logoUrl.isNotEmpty())
                        videoStorageService.createReadUrl(club.logoUrl, expiresInSeconds = 900).readUrl
                    else ""

                    ClubSummaryResponse(
                        id = club.id.toString(),
                        name = club.name,
                        logoUrl = readUrl,
                        accessCode = club.accessCode,
                        primaryColor = club.primaryColor,
                        accentColor = club.accentColor,
                        subscriptionType = club.subscriptionType,
                        createdAt = club.createdAt
                    )
                }
            )
        }
}
