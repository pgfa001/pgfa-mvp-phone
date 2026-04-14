package com.provingground.service

import com.provingground.database.repositories.ChallengesRepository
import com.provingground.database.repositories.ClubsRepository
import com.provingground.database.repositories.TeamsRepository
import com.provingground.database.repositories.UsersRepository
import com.provingground.database.tables.SubmissionValidationStatus
import com.provingground.database.tables.UserRole
import com.provingground.datamodels.Challenge
import com.provingground.datamodels.ChallengeDemoUploadIntent
import com.provingground.datamodels.ChallengeSubmission
import com.provingground.datamodels.ChallengeUploadIntent
import com.provingground.datamodels.CreateChallengeCmsRequest
import com.provingground.datamodels.Team
import com.provingground.datamodels.UpdateChallengeCmsRequest
import com.provingground.datamodels.User
import com.provingground.datamodels.VerifyChallengeSubmissionRequest
import com.provingground.datamodels.response.ChallengeCmsResponse
import com.provingground.datamodels.response.ChallengeDetailsResponse
import com.provingground.datamodels.response.ChallengeSubmissionDetailsResponse
import com.provingground.datamodels.response.ChallengeSubmissionResponse
import com.provingground.datamodels.response.ChallengeSummaryResponse
import com.provingground.datamodels.response.ChallengeTeamViewResponse
import com.provingground.datamodels.response.CreateChallengeDemoUploadUrlRequest
import com.provingground.datamodels.response.CreateChallengeDemoUploadUrlResponse
import com.provingground.datamodels.response.CreateChallengeSubmissionRequest
import com.provingground.datamodels.response.CreateChallengeSubmissionResponse
import com.provingground.datamodels.response.CreateChallengeSubmissionUploadUrlRequest
import com.provingground.datamodels.response.CreateChallengeSubmissionUploadUrlResponse
import com.provingground.datamodels.response.GetChallengeDemoVideoUrlResponse
import com.provingground.datamodels.response.GetChallengesCmsResponse
import com.provingground.datamodels.response.GetMyChallengeSubmissionsResponse
import com.provingground.datamodels.response.LeaderboardEntryResponse
import com.provingground.datamodels.response.VerifyChallengeSubmissionResponse
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

class ChallengeService(
    private val usersRepository: UsersRepository,
    private val teamsRepository: TeamsRepository,
    private val challengesRepository: ChallengesRepository,
    private val clubsRepository: ClubsRepository,
    private val videoStorageService: VideoStorageService,
) {

    suspend fun getChallengeDetails(
        actingUserId: UUID,
        challengeId: String,
        teamIds: List<String>
    ): ChallengeDetailsResponse = newSuspendedTransaction(Dispatchers.IO) {
        val actingUser = usersRepository.getByIdTx(actingUserId)
            ?: throw IllegalArgumentException("User not found")

        if (teamIds.isEmpty()) {
            throw IllegalArgumentException("teamIds is required")
        }

        val challengeUuid = try {
            UUID.fromString(challengeId)
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid challengeId")
        }

        val challenge = challengesRepository.getByIdTx(challengeUuid)
            ?: throw IllegalArgumentException("Challenge not found")

        val parsedTeamIds = teamIds.map {
            try {
                UUID.fromString(it)
            } catch (_: Exception) {
                throw IllegalArgumentException("Invalid teamId: $it")
            }
        }.distinct()

        val teamViews = when (actingUser.role) {
            UserRole.ATHLETE -> buildAthleteTeamViews(
                athlete = actingUser,
                challenge = challenge,
                requestedTeamIds = parsedTeamIds
            )

            UserRole.PARENT -> buildParentTeamViews(
                parent = actingUser,
                challenge = challenge,
                requestedTeamIds = parsedTeamIds
            )

            UserRole.COACH -> buildCoachTeamViews(
                coach = actingUser,
                challenge = challenge,
                requestedTeamIds = parsedTeamIds
            )

            UserRole.ADMIN -> throw IllegalArgumentException("Admins do not have a challenge details view")
        }

        ChallengeDetailsResponse(
            challenge = challenge.toChallengeSummaryResponse(),
            teamViews = teamViews
        )
    }

    suspend fun getMyChallengeSubmissions(
        actingUserId: UUID,
        challengeId: String,
        teamId: String?
    ): GetMyChallengeSubmissionsResponse = newSuspendedTransaction(Dispatchers.IO) {
        val actingUser = usersRepository.getByIdTx(actingUserId)
            ?: throw IllegalArgumentException("User not found")

        val challengeUuid = try {
            UUID.fromString(challengeId)
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid challengeId")
        }

        val challenge = challengesRepository.getByIdTx(challengeUuid)
            ?: throw IllegalArgumentException("Challenge not found")

        when (actingUser.role) {
            UserRole.ATHLETE -> getAthleteSubmissions(
                athlete = actingUser,
                challenge = challenge
            )

            UserRole.PARENT -> {
                val parsedTeamId = teamId?.let {
                    try {
                        UUID.fromString(it)
                    } catch (_: Exception) {
                        throw IllegalArgumentException("Invalid teamId")
                    }
                } ?: throw IllegalArgumentException("teamId is required for parents")

                getParentChildSubmissions(
                    parent = actingUser,
                    challenge = challenge,
                    teamId = parsedTeamId
                )
            }

            UserRole.COACH -> {
                throw IllegalArgumentException("Coaches do not have personal challenge submissions")
            }

            UserRole.ADMIN -> {
                throw IllegalArgumentException("Admins do not have personal challenge submissions")
            }
        }
    }

    suspend fun createChallengeSubmission(
        actingUserId: UUID,
        challengeId: String,
        request: CreateChallengeSubmissionRequest
    ): CreateChallengeSubmissionResponse = newSuspendedTransaction(Dispatchers.IO) {
        val actingUser = usersRepository.getByIdTx(actingUserId)
            ?: throw IllegalArgumentException("User not found")

        val challengeUuid = UUID.fromString(challengeId)
        val teamUuid = UUID.fromString(request.teamId)

        val challenge = challengesRepository.getByIdTx(challengeUuid)
            ?: throw IllegalArgumentException("Challenge not found")

        val team = teamsRepository.getByIdTx(teamUuid)
            ?: throw IllegalArgumentException("Team not found")

        requireChallengeAvailableForTeam(challenge, team)

        val athlete = resolveSubmissionAthlete(
            actingUser = actingUser,
            athleteId = request.athleteId,
            teamId = team.id
        )

        val uploadIntent = challengesRepository.getUploadIntentByObjectKeyTx(request.objectKey)
            ?: throw IllegalArgumentException("Upload intent not found")

        val now = System.currentTimeMillis()

        if (uploadIntent.consumedAt != null) {
            throw IllegalArgumentException("Upload intent already used")
        }

        if (uploadIntent.expiresAt < now) {
            throw IllegalArgumentException("Upload intent expired")
        }

        if (uploadIntent.actingUserId != actingUser.id ||
            uploadIntent.athleteUserId != athlete.id ||
            uploadIntent.challengeId != challenge.id ||
            uploadIntent.teamId != team.id ||
            uploadIntent.objectKey != request.objectKey
        ) {
            throw IllegalArgumentException("Upload intent does not match submission context")
        }

        val submission = ChallengeSubmission(
            id = UUID.randomUUID(),
            userId = athlete.id,
            challengeId = challenge.id,
            teamId = team.id,
            videoObjectKey = request.objectKey,
            score = request.score,
            validationStatus = SubmissionValidationStatus.NOT_VALIDATED,
            validatedBy = null,
            validatedAt = null,
            createdAt = now
        )

        challengesRepository.createSubmissionTx(submission)
        challengesRepository.markUploadIntentConsumedTx(uploadIntent.id, now)

        CreateChallengeSubmissionResponse(
            submissionId = submission.id.toString(),
            challengeId = challenge.id.toString(),
            athleteId = athlete.id.toString(),
            teamId = team.id.toString(),
            objectKey = submission.videoObjectKey,
            score = submission.score,
            validationStatus = submission.validationStatus,
            createdAt = submission.createdAt
        )
    }

    suspend fun getChallengeSubmissionDetails(
        actingUserId: UUID,
        submissionId: String
    ): ChallengeSubmissionDetailsResponse = newSuspendedTransaction(Dispatchers.IO) {
        val actingUser = usersRepository.getByIdTx(actingUserId)
            ?: throw IllegalArgumentException("User not found")

        val submissionUuid = try {
            UUID.fromString(submissionId)
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid submissionId")
        }

        val submission = challengesRepository.getSubmissionByIdTx(submissionUuid)
            ?: throw IllegalArgumentException("Submission not found")

        val athlete = usersRepository.getByIdTx(submission.userId)
            ?: throw IllegalArgumentException("Athlete not found")

        val challenge = challengesRepository.getByIdTx(submission.challengeId)
            ?: throw IllegalArgumentException("Challenge not found")

        val team = teamsRepository.getByIdTx(submission.teamId)
            ?: throw IllegalArgumentException("Team not found")

        when (actingUser.role) {
            UserRole.ATHLETE -> {
                if (actingUser.id != athlete.id) {
                    throw IllegalArgumentException("Athletes may only view their own submissions")
                }
            }

            UserRole.PARENT -> {
                val isOwnChild = usersRepository.isParentOfChildTx(
                    parentUserId = actingUser.id,
                    childUserId = athlete.id
                )
                if (!isOwnChild) {
                    throw IllegalArgumentException("Parents may only view their own child's submissions")
                }
            }

            UserRole.COACH -> {
                val coachedTeamIds = teamsRepository.getTeamsForUserTx(actingUser.id)
                    .map { it.id }
                    .toSet()

                if (submission.teamId !in coachedTeamIds) {
                    throw IllegalArgumentException("Coaches may only view submissions for teams they coach")
                }
            }

            UserRole.ADMIN -> {
                // allowed
            }
        }

        val readUrl = videoStorageService.createReadUrl(
            objectKey = submission.videoObjectKey,
            expiresInSeconds = 900
        )

        ChallengeSubmissionDetailsResponse(
            submissionId = submission.id.toString(),
            challengeId = challenge.id.toString(),
            challengeTitle = challenge.title,
            athleteId = athlete.id.toString(),
            athleteName = athlete.name,
            teamId = team.id.toString(),
            teamName = team.name,
            score = submission.score,
            validationStatus = submission.validationStatus,
            createdAt = submission.createdAt,
            videoUrl = readUrl.readUrl,
            videoUrlExpiresAt = readUrl.expiresAt
        )
    }

    suspend fun verifyChallengeSubmission(
        actingUserId: UUID,
        submissionId: String,
        request: VerifyChallengeSubmissionRequest
    ): VerifyChallengeSubmissionResponse = newSuspendedTransaction(Dispatchers.IO) {
        val actingUser = usersRepository.getByIdTx(actingUserId)
            ?: throw IllegalArgumentException("User not found")

        if (actingUser.role != UserRole.COACH && actingUser.role != UserRole.ADMIN) {
            throw IllegalArgumentException("Only coaches or admins can verify submissions")
        }

        if (
            request.validationStatus != SubmissionValidationStatus.VALIDATED &&
            request.validationStatus != SubmissionValidationStatus.INVALID
        ) {
            throw IllegalArgumentException("validationStatus must be VALIDATED or INVALID")
        }

        val submissionUuid = try {
            UUID.fromString(submissionId)
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid submissionId")
        }

        val submission = challengesRepository.getSubmissionByIdTx(submissionUuid)
            ?: throw IllegalArgumentException("Submission not found")

        val team = teamsRepository.getByIdTx(submission.teamId)
            ?: throw IllegalArgumentException("Team not found")

        if (actingUser.role == UserRole.COACH) {
            val coachedTeamIds = teamsRepository.getTeamsForUserTx(actingUser.id)
                .map { it.id }
                .toSet()

            if (submission.teamId !in coachedTeamIds) {
                throw IllegalArgumentException("Coach may only verify submissions for teams they coach")
            }
        }

        challengesRepository.updateSubmissionValidationStatusTx(
            id = submission.id,
            validationStatus = request.validationStatus,
            validatingUserId = actingUserId
        )

        VerifyChallengeSubmissionResponse(
            submissionId = submission.id.toString(),
            validationStatus = request.validationStatus
        )
    }

    suspend fun createSubmissionUploadUrl(
        actingUserId: UUID,
        challengeId: String,
        request: CreateChallengeSubmissionUploadUrlRequest
    ): CreateChallengeSubmissionUploadUrlResponse = newSuspendedTransaction(Dispatchers.IO) {
        val actingUser = usersRepository.getByIdTx(actingUserId)
            ?: throw IllegalArgumentException("User not found")

        val challengeUuid = UUID.fromString(challengeId)
        val teamUuid = UUID.fromString(request.teamId)

        val challenge = challengesRepository.getByIdTx(challengeUuid)
            ?: throw IllegalArgumentException("Challenge not found")

        val team = teamsRepository.getByIdTx(teamUuid)
            ?: throw IllegalArgumentException("Team not found")

        requireChallengeAvailableForTeam(challenge, team)

        val athlete = resolveSubmissionAthlete(
            actingUser = actingUser,
            athleteId = request.athleteId,
            teamId = team.id
        )

        val safeFileName = request.fileName.substringAfterLast('/').substringAfterLast('\\')
        val extension = safeFileName.substringAfterLast('.', "")
        val uploadIntentId = UUID.randomUUID()
        val now = System.currentTimeMillis()
        val expiresAt = now + 15 * 60 * 1000

        val objectKey = buildString {
            append("challenge-submissions/")
            append(challenge.id)
            append("/")
            append(athlete.id)
            append("/")
            append(uploadIntentId)
            if (extension.isNotBlank()) {
                append(".")
                append(extension.lowercase())
            }
        }

        val presigned = videoStorageService.createUploadUrl(
            objectKey = objectKey,
            expiresInSeconds = 900
        )

        challengesRepository.createUploadIntentTx(
            ChallengeUploadIntent(
                id = uploadIntentId,
                actingUserId = actingUser.id,
                athleteUserId = athlete.id,
                challengeId = challenge.id,
                teamId = team.id,
                objectKey = objectKey,
                originalFileName = safeFileName,
                contentType = request.contentType,
                expiresAt = expiresAt,
                consumedAt = null,
                createdAt = now
            )
        )

        CreateChallengeSubmissionUploadUrlResponse(
            uploadIntentId = uploadIntentId.toString(),
            objectKey = objectKey,
            uploadUrl = presigned.uploadUrl,
            expiresAt = presigned.expiresAt
        )
    }

    private fun resolveSubmissionAthlete(
        actingUser: User,
        athleteId: String?,
        teamId: UUID
    ): User {
        val athlete = when (actingUser.role) {
            UserRole.ATHLETE -> {
                if (athleteId != null && athleteId != actingUser.id.toString()) {
                    throw IllegalArgumentException("Athletes may only submit for themselves")
                }
                actingUser
            }
            UserRole.PARENT -> {
                val athleteUuid = athleteId?.let(UUID::fromString)
                    ?: throw IllegalArgumentException("athleteId is required for parents")

                val child = usersRepository.getByIdTx(athleteUuid)
                    ?: throw IllegalArgumentException("Athlete not found")

                if (!usersRepository.isParentOfChildTx(actingUser.id, child.id)) {
                    throw IllegalArgumentException("Parents may only submit for their own children")
                }
                child
            }
            UserRole.COACH, UserRole.ADMIN -> {
                throw IllegalArgumentException("This user role cannot submit challenge attempts")
            }
        }

        val athleteTeamIds = teamsRepository.getTeamsForUserTx(athlete.id).map { it.id }.toSet()
        if (teamId !in athleteTeamIds) {
            throw IllegalArgumentException("Athlete is not assigned to the selected team")
        }

        return athlete
    }

    private fun buildAthleteTeamViews(
        athlete: User,
        challenge: Challenge,
        requestedTeamIds: List<UUID>
    ): List<ChallengeTeamViewResponse> {
        val athleteTeams = teamsRepository.getTeamsForUserTx(athlete.id)
        val athleteTeamIds = athleteTeams.map { it.id }.toSet()

        if (requestedTeamIds.any { it !in athleteTeamIds }) {
            throw IllegalArgumentException("Athlete may only request their own team(s)")
        }

        return requestedTeamIds.map { teamId ->
            val team = athleteTeams.firstOrNull { it.id == teamId }
                ?: throw IllegalArgumentException("Team not found for athlete")

            requireChallengeAvailableForTeam(challenge, team)

            val submissions = challengesRepository.getSubmissionsByUserAndChallengeTx(
                userId = athlete.id,
                challengeId = challenge.id
            )

            ChallengeTeamViewResponse(
                athleteId = athlete.id.toString(),
                athleteName = athlete.name,
                teamId = team.id.toString(),
                teamName = team.name,
                leaderboard = buildLeaderboard(challenge.id, team.id, limit = 10),
                hasSubmitted = submissions.isNotEmpty(),
                submissionCount = submissions.size
            )
        }
    }

    private fun buildParentTeamViews(
        parent: User,
        challenge: Challenge,
        requestedTeamIds: List<UUID>
    ): List<ChallengeTeamViewResponse> {
        val children = usersRepository.getChildrenForParentTx(parent.id)

        return requestedTeamIds.map { teamId ->
            val team = teamsRepository.getByIdTx(teamId)
                ?: throw IllegalArgumentException("Team not found")

            requireChallengeAvailableForTeam(challenge, team)

            val matchingChildren = children.filter { child ->
                teamsRepository.getTeamsForUserTx(child.id).any { it.id == team.id }
            }

            if (matchingChildren.isEmpty()) {
                throw IllegalArgumentException("Parent does not have a child on team ${team.name}")
            }

            if (matchingChildren.size > 1) {
                throw IllegalArgumentException(
                    "Multiple children found on team ${team.name}; this request needs more specific targeting"
                )
            }

            val child = matchingChildren.first()

            val submissions = challengesRepository.getSubmissionsByUserAndChallengeTx(
                userId = child.id,
                challengeId = challenge.id
            )

            ChallengeTeamViewResponse(
                athleteId = child.id.toString(),
                athleteName = child.name,
                teamId = team.id.toString(),
                teamName = team.name,
                leaderboard = buildLeaderboard(challenge.id, team.id, limit = 10),
                hasSubmitted = submissions.isNotEmpty(),
                submissionCount = submissions.size
            )
        }
    }

    private fun buildCoachTeamViews(
        coach: User,
        challenge: Challenge,
        requestedTeamIds: List<UUID>
    ): List<ChallengeTeamViewResponse> {
        val coachedTeams = teamsRepository.getTeamsForUserTx(coach.id)
        val coachedTeamMap = coachedTeams.associateBy { it.id }

        if (requestedTeamIds.any { it !in coachedTeamMap.keys }) {
            throw IllegalArgumentException("Coach may only request teams they coach")
        }

        return requestedTeamIds.map { teamId ->
            val team = coachedTeamMap[teamId]
                ?: throw IllegalArgumentException("Team not found")

            requireChallengeAvailableForTeam(challenge, team)

            ChallengeTeamViewResponse(
                athleteId = null,
                athleteName = null,
                teamId = team.id.toString(),
                teamName = team.name,
                leaderboard = buildLeaderboard(challenge.id, team.id, limit = 10),
                hasSubmitted = false,
                submissionCount = 0
            )
        }
    }

    private fun buildLeaderboard(
        challengeId: UUID,
        teamId: UUID,
        limit: Int
    ): List<LeaderboardEntryResponse> {
        return challengesRepository
            .getBestSubmissionsForChallengeAndTeamTx(challengeId, teamId)
            .sortedWith(
                compareByDescending<Pair<ChallengeSubmission, User>> { it.first.score }
                    .thenBy { it.first.createdAt }
            )
            .take(limit)
            .mapIndexed { index, (submission, user) ->
                LeaderboardEntryResponse(
                    rank = index + 1,
                    submissionId = submission.id.toString(),
                    userId = user.id.toString(),
                    userName = user.name,
                    avatarUrl = user.avatarUrl,
                    score = submission.score,
                    validationStatus = submission.validationStatus
                )
            }
    }

    private fun getAthleteSubmissions(
        athlete: User,
        challenge: Challenge
    ): GetMyChallengeSubmissionsResponse {
        val team = teamsRepository.getPrimaryTeamForUserTx(athlete.id)
            ?: throw IllegalArgumentException("Athlete is not assigned to a team")

        requireChallengeAvailableForTeam(challenge, team)

        val submissions = challengesRepository.getSubmissionsByUserAndChallengeTx(
            userId = athlete.id,
            challengeId = challenge.id
        )

        return GetMyChallengeSubmissionsResponse(
            challengeId = challenge.id.toString(),
            athleteId = athlete.id.toString(),
            athleteName = athlete.name,
            teamId = team.id.toString(),
            teamName = team.name,
            submissions = submissions.map { submission ->
                ChallengeSubmissionResponse(
                    id = submission.id.toString(),
                    videoUrl = submission.videoObjectKey,
                    score = submission.score,
                    validationStatus = submission.validationStatus,
                    createdAt = submission.createdAt
                )
            }
        )
    }

    private fun getParentChildSubmissions(
        parent: User,
        challenge: Challenge,
        teamId: UUID
    ): GetMyChallengeSubmissionsResponse {
        val team = teamsRepository.getByIdTx(teamId)
            ?: throw IllegalArgumentException("Team not found")

        requireChallengeAvailableForTeam(challenge, team)

        val children = usersRepository.getChildrenForParentTx(parent.id)

        val matchingChildren = children.filter { child ->
            teamsRepository.getTeamsForUserTx(child.id).any { it.id == team.id }
        }

        if (matchingChildren.isEmpty()) {
            throw IllegalArgumentException("Parent does not have a child on this team")
        }

        if (matchingChildren.size > 1) {
            throw IllegalArgumentException(
                "Multiple children found on team ${team.name}; this request needs more specific targeting"
            )
        }

        val child = matchingChildren.first()

        val submissions = challengesRepository.getSubmissionsByUserAndChallengeTx(
            userId = child.id,
            challengeId = challenge.id
        )

        return GetMyChallengeSubmissionsResponse(
            challengeId = challenge.id.toString(),
            athleteId = child.id.toString(),
            athleteName = child.name,
            teamId = team.id.toString(),
            teamName = team.name,
            submissions = submissions.map { submission ->
                ChallengeSubmissionResponse(
                    id = submission.id.toString(),
                    videoUrl = submission.videoObjectKey,
                    score = submission.score,
                    validationStatus = submission.validationStatus,
                    createdAt = submission.createdAt
                )
            }
        )
    }

    private fun requireAdmin(user: User) {
        if (user.role != UserRole.ADMIN) {
            throw IllegalArgumentException("Only admins can perform this action")
        }
    }

    suspend fun getAllChallengesCms(actingUserId: UUID): GetChallengesCmsResponse =
        newSuspendedTransaction(Dispatchers.IO) {
            val actingUser = usersRepository.getByIdTx(actingUserId)
                ?: throw IllegalArgumentException("User not found")
            requireAdmin(actingUser)

            val challenges = challengesRepository.getAllTx()

            GetChallengesCmsResponse(
                challenges = challenges.map { challenge ->
                    ChallengeCmsResponse(
                        id = challenge.id.toString(),
                        title = challenge.title,
                        description = challenge.description,
                        demoVideoObjectKey = challenge.demoVideoObjectKey,
                        scoringType = challenge.scoringType,
                        difficulty = challenge.difficulty,
                        startTime = challenge.startTime,
                        endTime = challenge.endTime,
                        createdBy = challenge.createdBy.toString(),
                        createdAt = challenge.createdAt,
                        clubIds = challengesRepository
                            .getClubIdsForChallengeTx(challenge.id)
                            .map { it.toString() }
                    )
                }
            )
        }

    suspend fun createChallengeCms(
        actingUserId: UUID,
        request: CreateChallengeCmsRequest
    ): ChallengeCmsResponse = newSuspendedTransaction(Dispatchers.IO) {
        val actingUser = usersRepository.getByIdTx(actingUserId)
            ?: throw IllegalArgumentException("User not found")
        requireAdmin(actingUser)

        if (request.title.isBlank()) throw IllegalArgumentException("Title is required")
        if (request.clubIds.isEmpty()) throw IllegalArgumentException("At least one club must be selected")
        if (request.startTime >= request.endTime) throw IllegalArgumentException("startTime must be before endTime")

        val clubUuids = request.clubIds.map {
            try {
                UUID.fromString(it)
            } catch (_: Exception) {
                throw IllegalArgumentException("Invalid clubId: $it")
            }
        }.distinct()

        clubUuids.forEach { clubId ->
            if (clubsRepository.getByIdTx(clubId) == null) {
                throw IllegalArgumentException("Club not found: $clubId")
            }
        }

        if (request.demoVideoObjectKey != null) {
            val intent = challengesRepository.getDemoUploadIntentByObjectKeyTx(request.demoVideoObjectKey)
                ?: throw IllegalArgumentException("Demo video upload intent not found")

            val now = System.currentTimeMillis()
            if (intent.actingUserId != actingUser.id) throw IllegalArgumentException("Demo video upload intent does not belong to user")
            if (intent.consumedAt != null) throw IllegalArgumentException("Demo video upload intent already used")
            if (intent.expiresAt < now) throw IllegalArgumentException("Demo video upload intent expired")

            challengesRepository.markDemoUploadIntentConsumedTx(intent.id, now)
        }

        val challenge = Challenge(
            id = UUID.randomUUID(),
            title = request.title.trim(),
            description = request.description,
            demoVideoObjectKey = request.demoVideoObjectKey,
            scoringType = request.scoringType,
            difficulty = request.difficulty,
            startTime = request.startTime,
            endTime = request.endTime,
            createdBy = actingUser.id,
            createdAt = System.currentTimeMillis()
        )

        challengesRepository.createTx(challenge)
        challengesRepository.replaceChallengeClubsTx(challenge.id, clubUuids)

        ChallengeCmsResponse(
            id = challenge.id.toString(),
            title = challenge.title,
            description = challenge.description,
            demoVideoObjectKey = challenge.demoVideoObjectKey,
            scoringType = challenge.scoringType,
            difficulty = challenge.difficulty,
            startTime = challenge.startTime,
            endTime = challenge.endTime,
            createdBy = challenge.createdBy.toString(),
            createdAt = challenge.createdAt,
            clubIds = clubUuids.map { it.toString() }
        )
    }

    suspend fun updateChallengeCms(
        actingUserId: UUID,
        challengeId: String,
        request: UpdateChallengeCmsRequest
    ): ChallengeCmsResponse = newSuspendedTransaction(Dispatchers.IO) {
        val actingUser = usersRepository.getByIdTx(actingUserId)
            ?: throw IllegalArgumentException("User not found")
        requireAdmin(actingUser)

        val challengeUuid = try {
            UUID.fromString(challengeId)
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid challengeId")
        }

        val existing = challengesRepository.getByIdTx(challengeUuid)
            ?: throw IllegalArgumentException("Challenge not found")

        if (request.title.isBlank()) throw IllegalArgumentException("Title is required")
        if (request.clubIds.isEmpty()) throw IllegalArgumentException("At least one club must be selected")
        if (request.startTime >= request.endTime) throw IllegalArgumentException("startTime must be before endTime")

        val clubUuids = request.clubIds.map {
            try {
                UUID.fromString(it)
            } catch (_: Exception) {
                throw IllegalArgumentException("Invalid clubId: $it")
            }
        }.distinct()

        clubUuids.forEach { clubId ->
            if (clubsRepository.getByIdTx(clubId) == null) {
                throw IllegalArgumentException("Club not found: $clubId")
            }
        }

        if (request.demoVideoObjectKey != null && request.demoVideoObjectKey != existing.demoVideoObjectKey) {
            val intent = challengesRepository.getDemoUploadIntentByObjectKeyTx(request.demoVideoObjectKey)
                ?: throw IllegalArgumentException("Demo video upload intent not found")

            val now = System.currentTimeMillis()
            if (intent.actingUserId != actingUser.id) throw IllegalArgumentException("Demo video upload intent does not belong to user")
            if (intent.consumedAt != null) throw IllegalArgumentException("Demo video upload intent already used")
            if (intent.expiresAt < now) throw IllegalArgumentException("Demo video upload intent expired")

            challengesRepository.markDemoUploadIntentConsumedTx(intent.id, now)
        }

        val updated = challengesRepository.updateTx(challengeUuid, request)
        if (!updated) throw IllegalArgumentException("Failed to update challenge")

        challengesRepository.replaceChallengeClubsTx(challengeUuid, clubUuids)

        ChallengeCmsResponse(
            id = challengeUuid.toString(),
            title = request.title.trim(),
            description = request.description,
            demoVideoObjectKey = request.demoVideoObjectKey,
            scoringType = request.scoringType,
            difficulty = request.difficulty,
            startTime = request.startTime,
            endTime = request.endTime,
            createdBy = existing.createdBy.toString(),
            createdAt = existing.createdAt,
            clubIds = clubUuids.map { it.toString() }
        )
    }

    suspend fun createChallengeDemoUploadUrl(
        actingUserId: UUID,
        request: CreateChallengeDemoUploadUrlRequest
    ): CreateChallengeDemoUploadUrlResponse = newSuspendedTransaction(Dispatchers.IO) {
        val actingUser = usersRepository.getByIdTx(actingUserId)
            ?: throw IllegalArgumentException("User not found")
        requireAdmin(actingUser)

        val safeFileName = request.fileName.substringAfterLast('/').substringAfterLast('\\')
        val extension = safeFileName.substringAfterLast('.', "")
        val intentId = UUID.randomUUID()
        val now = System.currentTimeMillis()

        val objectKey = buildString {
            append("challenge-demo-videos/")
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

        challengesRepository.createDemoUploadIntentTx(
            ChallengeDemoUploadIntent(
                id = intentId,
                actingUserId = actingUser.id,
                objectKey = objectKey,
                originalFileName = safeFileName,
                contentType = request.contentType,
                expiresAt = presigned.expiresAt,
                consumedAt = null,
                createdAt = now
            )
        )

        CreateChallengeDemoUploadUrlResponse(
            uploadIntentId = intentId.toString(),
            objectKey = objectKey,
            uploadUrl = presigned.uploadUrl,
            expiresAt = presigned.expiresAt
        )
    }

    suspend fun getChallengeDemoVideoUrl(
        actingUserId: UUID,
        challengeId: String
    ): GetChallengeDemoVideoUrlResponse = newSuspendedTransaction(Dispatchers.IO) {
        val actingUser = usersRepository.getByIdTx(actingUserId)
            ?: throw IllegalArgumentException("User not found")

        if (actingUser.role != UserRole.ADMIN && actingUser.role != UserRole.COACH) {
            throw IllegalArgumentException("Only admins and coaches can view challenge demo videos")
        }

        val challengeUuid = try {
            UUID.fromString(challengeId)
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid challengeId")
        }

        val challenge = challengesRepository.getByIdTx(challengeUuid)
            ?: throw IllegalArgumentException("Challenge not found")

        val objectKey = challenge.demoVideoObjectKey
            ?: throw IllegalArgumentException("Challenge has no demo video")

        val readUrl = videoStorageService.createReadUrl(objectKey, expiresInSeconds = 900)

        GetChallengeDemoVideoUrlResponse(
            challengeId = challenge.id.toString(),
            videoUrl = readUrl.readUrl,
            expiresAt = readUrl.expiresAt
        )
    }

    private suspend fun Challenge.toChallengeSummaryResponse(): ChallengeSummaryResponse {
        val demoVideoUrl = demoVideoObjectKey?.let { objectKey ->
            videoStorageService.createReadUrl(
                objectKey = objectKey,
                expiresInSeconds = 900
            ).readUrl
        }

        return ChallengeSummaryResponse(
            id = id.toString(),
            title = title,
            description = description,
            demoVideoUrl = demoVideoUrl,
            scoringType = scoringType,
            difficulty = difficulty,
            startTime = startTime,
            endTime = endTime
        )
    }

    private fun requireChallengeAvailableForTeam(
        challenge: Challenge,
        team: Team
    ) {
        val isAvailable = challengesRepository.isChallengeAvailableForClubTx(
            challengeId = challenge.id,
            clubId = team.clubId
        )

        if (!isAvailable) {
            throw IllegalArgumentException("Challenge does not belong to the selected team's club")
        }
    }
}