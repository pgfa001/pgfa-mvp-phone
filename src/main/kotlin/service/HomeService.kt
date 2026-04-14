package com.provingground.service

import com.provingground.database.repositories.ChallengesRepository
import com.provingground.database.repositories.TeamsRepository
import com.provingground.database.repositories.UsersRepository
import com.provingground.database.tables.UserRole
import com.provingground.datamodels.Challenge
import com.provingground.datamodels.ChallengeSubmission
import com.provingground.datamodels.Team
import com.provingground.datamodels.User
import com.provingground.datamodels.response.ChallengeSummaryResponse
import com.provingground.datamodels.response.HomeChallengeCardResponse
import com.provingground.datamodels.response.HomeScreenResponse
import com.provingground.datamodels.response.LeaderboardEntryResponse
import com.provingground.datamodels.response.SubmissionSummaryResponse
import com.provingground.datamodels.response.TeamChallengeStatsResponse
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

class HomeService(
    private val usersRepository: UsersRepository,
    private val teamsRepository: TeamsRepository,
    private val challengesRepository: ChallengesRepository,
    private val videoStorageService: VideoStorageService
) {

    suspend fun getHomeScreen(userId: UUID): HomeScreenResponse =
        newSuspendedTransaction(Dispatchers.IO) {
            val user = usersRepository.getByIdTx(userId)
                ?: throw IllegalArgumentException("User not found")

            val cards = when (user.role) {
                UserRole.ATHLETE -> buildAthleteCards(user)
                UserRole.PARENT -> buildParentCards(user)
                UserRole.COACH -> buildCoachCards(user)
                UserRole.ADMIN -> emptyList()
            }

            HomeScreenResponse(
                userId = user.id.toString(),
                role = user.role,
                cards = cards
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
            .getBestSubmissionsForChallengeAndTeamTx(challenge.id, team.id)
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