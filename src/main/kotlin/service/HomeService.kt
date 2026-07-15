package com.provingground.service

import com.provingground.database.repositories.ChallengesRepository
import com.provingground.database.repositories.TeamsRepository
import com.provingground.database.repositories.UsersRepository
import com.provingground.database.tables.ChallengeScoringType
import com.provingground.database.tables.SubmissionValidationStatus
import com.provingground.database.tables.UserRole
import com.provingground.datamodels.Challenge
import com.provingground.datamodels.ChallengeSubmission
import com.provingground.datamodels.Team
import com.provingground.datamodels.User
import com.provingground.datamodels.response.ChallengeSummaryResponse
import com.provingground.datamodels.response.HomeChallengeCardResponse
import com.provingground.datamodels.response.HomeClubRankResponse
import com.provingground.datamodels.response.HomeRankSummaryResponse
import com.provingground.datamodels.response.HomeScreenResponse
import com.provingground.datamodels.response.HomeStatsPeriod
import com.provingground.datamodels.response.HomeStatsResponse
import com.provingground.datamodels.response.HomeThisWeekStatsResponse
import com.provingground.datamodels.response.HomeTeamRankResponse
import com.provingground.datamodels.response.LeaderboardEntryResponse
import com.provingground.datamodels.response.PreviousChallengeResponse
import com.provingground.datamodels.response.PreviousChallengesResponse
import com.provingground.datamodels.response.SubmissionSummaryResponse
import com.provingground.datamodels.response.TeamChallengeStatsResponse
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID
import kotlin.math.roundToInt

class HomeService(
    private val usersRepository: UsersRepository,
    private val teamsRepository: TeamsRepository,
    private val challengesRepository: ChallengesRepository,
    private val videoStorageService: VideoStorageService,
    private val subscriptionService: SubscriptionService
) {
    private val weeklyStatsRequiredSubmissions = 7


    suspend fun getHomeScreen(userId: UUID): HomeScreenResponse =
        newSuspendedTransaction(Dispatchers.IO) {
            val user = usersRepository.getByIdTx(userId)
                ?: throw IllegalArgumentException("User not found")

            val cards = when (user.role) {
                UserRole.ATHLETE -> {
                    subscriptionService.requireAccessForAthleteTx(user)
                    buildAthleteCards(user)
                }
                UserRole.PARENT -> buildParentCards(user)
                UserRole.COACH -> buildCoachCards(user)
                UserRole.ADMIN -> emptyList()
                UserRole.SUPERADMIN -> emptyList()
            }

            HomeScreenResponse(
                userId = user.id.toString(),
                role = user.role,
                cards = cards
            )
        }

    suspend fun getStats(
        userId: UUID,
        period: String?,
        athleteId: String? = null
    ): HomeStatsResponse = newSuspendedTransaction(Dispatchers.IO) {
        val requestedPeriod = parseStatsPeriod(period)
        val user = usersRepository.getByIdTx(userId)
            ?: throw IllegalArgumentException("User not found")

        val athlete = resolveHomeAthlete(user, athleteId, featureName = "stats")
        subscriptionService.requireAccessForAthleteTx(athlete)
        val team = teamsRepository.getPrimaryTeamForUserTx(athlete.id)
            ?: throw IllegalArgumentException("Athlete is not assigned to a team")

        if (requestedPeriod == HomeStatsPeriod.THIS_WEEK) {
            return@newSuspendedTransaction buildThisWeekStatsResponse(
                athlete = athlete,
                team = team
            )
        }

        val athleteSubmissions = challengesRepository.getSubmissionsForAthleteTx(athlete.id)
        val completedChallengeIds = athleteSubmissions.map { it.challengeId }.distinct()
        val clubTeams = teamsRepository.getByClubIdTx(team.clubId)
        val clubTeamIds = clubTeams.map { it.id }.toSet()

        val bestClubRank = completedChallengeIds.mapNotNull { challengeId ->
            val challenge = challengesRepository.getByIdTx(challengeId) ?: return@mapNotNull null

            calculateRankForAthlete(
                athleteId = athlete.id,
                submissions = challengesRepository
                    .getSubmissionsForChallengeTx(challenge.id)
                    .filter { submission -> submission.teamId in clubTeamIds },
                scoringType = challenge.scoringType
            )
        }.minOrNull()

        val previousChallenges = challengesRepository.getPreviousChallengesForClubTx(team.clubId)
        val completedChallengeIdSet = completedChallengeIds.toSet()

        HomeStatsResponse(
            period = requestedPeriod,
            athleteId = athlete.id.toString(),
            totalChallengesCompleted = completedChallengeIds.size,
            currentStreak = calculateCurrentStreak(
                previousChallenges = previousChallenges,
                completedChallengeIds = completedChallengeIdSet
            ),
            totalSubmissions = athleteSubmissions.size,
            bestClubRank = bestClubRank
        )
    }

    private fun buildThisWeekStatsResponse(
        athlete: User,
        team: Team
    ): HomeStatsResponse {
        val challenge = challengesRepository.getCurrentChallengeForClubTx(team.clubId)
            ?: throw IllegalArgumentException("No active challenge found for club")

        val athleteSubmissions = challengesRepository
            .getSubmissionsByUserAndChallengeTx(
                userId = athlete.id,
                challengeId = challenge.id
            )

        val totalAttempts = athleteSubmissions.size
        val averageScore = athleteSubmissions.map { it.score }.averageOrNull()
        val bestSubmission = athleteSubmissions
            .sortedWith(bestChallengeSubmissionComparator(challenge.scoringType))
            .firstOrNull()

        val clubTeams = teamsRepository.getByClubIdTx(team.clubId)
        val clubTeamIds = clubTeams.map { it.id }.toSet()
        val clubAthleteCount = clubTeams
            .flatMap { clubTeam -> teamsRepository.getAthletesForTeamTx(clubTeam.id) }
            .distinctBy { it.id }
            .size

        val clubSubmissions = challengesRepository
            .getSubmissionsForChallengeTx(challenge.id)
            .filter { submission -> submission.teamId in clubTeamIds }

        val unlocked = totalAttempts >= weeklyStatsRequiredSubmissions

        return HomeStatsResponse(
            period = HomeStatsPeriod.THIS_WEEK,
            athleteId = athlete.id.toString(),
            thisWeek = HomeThisWeekStatsResponse(
                challengeId = challenge.id.toString(),
                challengeTitle = challenge.title,
                scoringType = challenge.scoringType,
                unlocked = unlocked,
                requiredSubmissions = weeklyStatsRequiredSubmissions,
                totalAttempts = totalAttempts,
                remainingSubmissions = (weeklyStatsRequiredSubmissions - totalAttempts).coerceAtLeast(0),
                averageScore = averageScore,
                bestScore = bestSubmission?.score,
                bestScoreSubmissionId = bestSubmission?.id?.toString(),
                improvementPercentage = if (unlocked && averageScore != null && bestSubmission != null) {
                    calculateImprovementPercentage(
                        averageScore = averageScore,
                        bestScore = bestSubmission.score,
                        scoringType = challenge.scoringType
                    )
                } else {
                    null
                },
                clubAverageScore = clubSubmissions.map { it.score }.averageOrNull(),
                clubAverageAttempts = if (clubAthleteCount == 0) {
                    0.0
                } else {
                    clubSubmissions.size.toDouble() / clubAthleteCount.toDouble()
                }
            )
        )
    }

    suspend fun getRankSummary(
        userId: UUID,
        athleteId: String? = null
    ): HomeRankSummaryResponse = newSuspendedTransaction(Dispatchers.IO) {
        val user = usersRepository.getByIdTx(userId)
            ?: throw IllegalArgumentException("User not found")

        val athlete = resolveHomeAthlete(user, athleteId, featureName = "rank summary")
        subscriptionService.requireAccessForAthleteTx(athlete)
        val team = teamsRepository.getPrimaryTeamForUserTx(athlete.id)
            ?: throw IllegalArgumentException("Athlete is not assigned to a team")

        val athleteSubmissions = challengesRepository.getSubmissionsForAthleteTx(athlete.id)
        val participatedChallengeIds = athleteSubmissions
            .map { it.challengeId }
            .distinct()

        val clubTeams = teamsRepository.getByClubIdTx(team.clubId)
        val clubTeamIds = clubTeams.map { it.id }.toSet()

        val clubAthleteCount = clubTeams
            .flatMap { clubTeam -> teamsRepository.getAthletesForTeamTx(clubTeam.id) }
            .distinctBy { it.id }
            .size

        val ranks = participatedChallengeIds.mapNotNull { challengeId ->
            val challenge = challengesRepository.getByIdTx(challengeId) ?: return@mapNotNull null

            val teamRank = calculateRankForAthlete(
                athleteId = athlete.id,
                submissions = challengesRepository.getAllSubmissionsForChallengeAndTeamTx(
                    challengeId = challenge.id,
                    teamId = team.id
                ),
                scoringType = challenge.scoringType
            ) ?: return@mapNotNull null

            val clubRank = calculateRankForAthlete(
                athleteId = athlete.id,
                submissions = challengesRepository
                    .getSubmissionsForChallengeTx(challenge.id)
                    .filter { submission -> submission.teamId in clubTeamIds },
                scoringType = challenge.scoringType
            ) ?: return@mapNotNull null

            teamRank to clubRank
        }

        val averageTeamRank = ranks.map { it.first }.averageOrNull()
        val averageClubRank = ranks.map { it.second }.averageOrNull()

        HomeRankSummaryResponse(
            athleteId = athlete.id.toString(),
            teamRank = HomeTeamRankResponse(
                rank = averageTeamRank?.roundToInt(),
                averageRank = averageTeamRank,
                teamId = team.id.toString(),
                teamName = team.name
            ),
            clubRank = HomeClubRankResponse(
                rank = averageClubRank?.roundToInt(),
                averageRank = averageClubRank,
                athleteCount = clubAthleteCount
            ),
            participatedChallengeCount = ranks.size
        )
    }

    suspend fun getPreviousChallenges(
        userId: UUID,
        pageSize: Int = 3,
        athleteId: String? = null
    ): PreviousChallengesResponse = newSuspendedTransaction(Dispatchers.IO) {
        if (pageSize <= 0) {
            throw IllegalArgumentException("pageSize must be greater than 0")
        }

        val user = usersRepository.getByIdTx(userId)
            ?: throw IllegalArgumentException("User not found")

        val athlete = resolveHomeAthlete(user, athleteId, featureName = "previous challenges")
        subscriptionService.requireAccessForAthleteTx(athlete)

        val team = teamsRepository.getPrimaryTeamForUserTx(athlete.id)
            ?: return@newSuspendedTransaction PreviousChallengesResponse(challenges = emptyList())

        val challenges = challengesRepository.getPreviousChallengesForClubTx(
            clubId = team.clubId,
            limit = pageSize
        )

        PreviousChallengesResponse(
            challenges = challenges.map { challenge ->
                val bestSubmission = challengesRepository
                    .getSubmissionsByUserAndChallengeTx(
                        userId = athlete.id,
                        challengeId = challenge.id
                    )
                    .sortedWith(bestChallengeSubmissionComparator(challenge.scoringType))
                    .firstOrNull()

                PreviousChallengeResponse(
                    name = challenge.title,
                    completed = bestSubmission != null,
                    score = bestSubmission?.score
                )
            }
        )
    }

    private suspend fun buildAthleteCards(user: User): List<HomeChallengeCardResponse> {
        val team = teamsRepository.getPrimaryTeamForUserTx(user.id) ?: return emptyList()
        val challenge = challengesRepository.getCurrentChallengeForClubTx(team.clubId)

        return listOf(
            buildAthleteCard(
                athlete = user,
                team = team,
                challenge = challenge,
                leaderboardLimit = 5
            )
        )
    }

    private suspend fun buildParentCards(parent: User): List<HomeChallengeCardResponse> {
        val children = usersRepository.getChildrenForParentTx(parent.id)

        return children.mapNotNull { child ->
            val team = teamsRepository.getPrimaryTeamForUserTx(child.id) ?: return@mapNotNull null
            val challenge = challengesRepository.getCurrentChallengeForClubTx(team.clubId)

            buildAthleteCard(
                athlete = child,
                team = team,
                challenge = challenge,
                leaderboardLimit = 5
            )
        }
    }

    private suspend fun buildCoachCards(coach: User): List<HomeChallengeCardResponse> {
        val teams = teamsRepository.getTeamsForUserTx(coach.id)

        return teams.map { team ->
            val challenge = challengesRepository.getCurrentChallengeForClubTx(team.clubId)
            val leaderboard = buildLeaderboard(
                challenge = challenge,
                team = team,
                limit = 10
            )

            val stats = if (challenge != null) {
                val athletes = teamsRepository.getAthletesForTeamTx(team.id)
                val submissions = challengesRepository
                    .getAllSubmissionsForChallengeAndTeamTx(challenge.id, team.id)

                TeamChallengeStatsResponse(
                    teamAthleteCount = athletes.size,
                    submittedAthleteCount = submissions.map { it.userId }.distinct().size
                )
            } else {
                null
            }

            HomeChallengeCardResponse(
                athleteId = null,
                athleteName = null,
                teamId = team.id.toString(),
                teamName = team.name,
                challenge = challenge?.toChallengeSummaryResponse(),
                leaderboard = leaderboard,
                submissionSummary = null,
                teamStats = stats
            )
        }
    }

    private suspend fun buildAthleteCard(
        athlete: User,
        team: Team,
        challenge: Challenge?,
        leaderboardLimit: Int
    ): HomeChallengeCardResponse {
        val leaderboard = buildLeaderboard(
            challenge = challenge,
            team = team,
            limit = leaderboardLimit
        )

        val submissionSummary = if (challenge != null) {
            val submission = challengesRepository.getSubmissionsByUserAndChallengeTx(
                userId = athlete.id,
                challengeId = challenge.id
            ).firstOrNull()

            SubmissionSummaryResponse(
                hasSubmitted = submission != null,
                submissionId = submission?.id?.toString(),
                score = submission?.score,
                validationStatus = submission?.validationStatus,
                submittedAt = submission?.createdAt
            )
        } else {
            SubmissionSummaryResponse(
                hasSubmitted = false
            )
        }

        return HomeChallengeCardResponse(
            athleteId = athlete.id.toString(),
            athleteName = athlete.name,
            teamId = team.id.toString(),
            teamName = team.name,
            challenge = challenge?.toChallengeSummaryResponse(),
            leaderboard = leaderboard,
            submissionSummary = submissionSummary,
            teamStats = null
        )
    }

    private fun buildLeaderboard(
        challenge: Challenge?,
        team: Team,
        limit: Int
    ): List<LeaderboardEntryResponse> {
        if (challenge == null) return emptyList()

        return challengesRepository
            .getBestSubmissionsForChallengeAndTeamTx(challenge.id, team.id, challenge.scoringType)
            .sortedWith(bestSubmissionComparator(challenge.scoringType))
            .take(limit)
            .mapIndexed { index, (submission, user) ->
                LeaderboardEntryResponse(
                    rank = index + 1,
                    submissionId = submission.id.toString(),
                    userId = user.id.toString(),
                    userName = user.username,
                    avatarUrl = user.avatarUrl,
                    score = submission.score,
                    validationStatus = submission.validationStatus
                )
            }
    }

    private fun resolveHomeAthlete(
        user: User,
        athleteId: String?,
        featureName: String
    ): User {
        return when (user.role) {
            UserRole.ATHLETE -> {
                if (!athleteId.isNullOrBlank() && athleteId != user.id.toString()) {
                    throw IllegalArgumentException("Athletes can only request their own $featureName")
                }
                user
            }

            UserRole.PARENT -> {
                val children = usersRepository.getChildrenForParentTx(user.id)

                if (!athleteId.isNullOrBlank()) {
                    val athleteUuid = try {
                        UUID.fromString(athleteId)
                    } catch (_: Exception) {
                        throw IllegalArgumentException("Invalid athleteId")
                    }

                    children.firstOrNull { it.id == athleteUuid }
                        ?: throw IllegalArgumentException("Parents may only request $featureName for their own children")
                } else {
                    if (children.size != 1) {
                        throw IllegalArgumentException("athleteId is required for parents with multiple children")
                    }
                    children.first()
                }
            }

            UserRole.COACH, UserRole.ADMIN, UserRole.SUPERADMIN -> {
                throw IllegalArgumentException("$featureName is only available for athletes and parents")
            }
        }
    }

    private fun calculateRankForAthlete(
        athleteId: UUID,
        submissions: List<ChallengeSubmission>,
        scoringType: ChallengeScoringType
    ): Int? {
        val rankedBestSubmissions = submissions
            .filter { it.validationStatus != SubmissionValidationStatus.INVALID }
            .groupBy { it.userId }
            .values
            .map { athleteSubmissions ->
                athleteSubmissions.sortedWith(bestChallengeSubmissionComparator(scoringType)).first()
            }
            .sortedWith(bestChallengeSubmissionComparator(scoringType))

        return rankedBestSubmissions.indexOfFirst { it.userId == athleteId }
            .takeIf { it >= 0 }
            ?.plus(1)
    }

    private fun List<Int>.averageOrNull(): Double? {
        if (isEmpty()) return null
        return average()
    }

    private fun calculateImprovementPercentage(
        averageScore: Double,
        bestScore: Int,
        scoringType: ChallengeScoringType
    ): Double? {
        if (averageScore == 0.0) return null

        val improvement = if (scoringType.higherIsBetter) {
            bestScore.toDouble() - averageScore
        } else {
            averageScore - bestScore.toDouble()
        }

        return improvement / kotlin.math.abs(averageScore) * 100.0
    }

    private fun parseStatsPeriod(period: String?): HomeStatsPeriod {
        val value = period?.takeIf { it.isNotBlank() } ?: HomeStatsPeriod.ALL_TIME.name

        return try {
            HomeStatsPeriod.valueOf(value.uppercase())
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid period")
        }
    }

    private fun calculateCurrentStreak(
        previousChallenges: List<Challenge>,
        completedChallengeIds: Set<UUID>
    ): Int {
        var streak = 0

        for (challenge in previousChallenges) {
            if (challenge.id in completedChallengeIds) {
                streak++
            } else {
                break
            }
        }

        return streak
    }

    private fun bestSubmissionComparator(
        scoringType: ChallengeScoringType
    ): Comparator<Pair<ChallengeSubmission, User>> {
        return if (scoringType.higherIsBetter) {
            compareByDescending<Pair<ChallengeSubmission, User>> { it.first.score }
                .thenBy { it.first.createdAt }
        } else {
            compareBy<Pair<ChallengeSubmission, User>> { it.first.score }
                .thenBy { it.first.createdAt }
        }
    }

    private fun bestChallengeSubmissionComparator(
        scoringType: ChallengeScoringType
    ): Comparator<ChallengeSubmission> {
        return if (scoringType.higherIsBetter) {
            compareByDescending<ChallengeSubmission> { it.score }
                .thenBy { it.createdAt }
        } else {
            compareBy<ChallengeSubmission> { it.score }
                .thenBy { it.createdAt }
        }
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
}
