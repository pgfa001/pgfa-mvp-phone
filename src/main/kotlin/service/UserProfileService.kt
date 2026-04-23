package com.provingground.service

import com.provingground.database.repositories.ChallengesRepository
import com.provingground.database.repositories.TeamsRepository
import com.provingground.database.repositories.UsersRepository
import com.provingground.database.tables.UserRole
import com.provingground.datamodels.ChallengeSubmission
import com.provingground.datamodels.response.AthleteProfileResponse
import com.provingground.datamodels.response.RecentActivityItemResponse
import com.provingground.datamodels.response.UserProfileResponse
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

class UserProfileService(
    private val usersRepository: UsersRepository,
    private val teamsRepository: TeamsRepository,
    private val challengesRepository: ChallengesRepository
) {
    suspend fun getMyProfile(
        actingUserId: UUID
    ): UserProfileResponse = newSuspendedTransaction(Dispatchers.IO) {
        val user = usersRepository.getByIdTx(actingUserId)
            ?: throw IllegalArgumentException("User not found")

        val age = calculateAge(user.dob)

        val athleteProfile = if (user.role == UserRole.ATHLETE) {
            buildAthleteProfile(user.id)
        } else {
            null
        }

        UserProfileResponse(
            userId = user.id.toString(),
            name = user.name,
            role = user.role,
            age = age,
            athleteProfile = athleteProfile
        )
    }

    private fun buildAthleteProfile(userId: UUID): AthleteProfileResponse {
        val team = teamsRepository.getTeamsForUserTx(userId).firstOrNull()

        val completedChallenges = challengesRepository.getCompletedChallengesForAthleteTx(userId)
        val challengesById = completedChallenges
            .map { it.challengeId }
            .distinct()
            .associateWith { challengeId ->
                challengesRepository.getByIdTx(challengeId)
            }

        val recentActivity = completedChallenges
            .take(3)
            .mapNotNull { submission ->
                val challenge = challengesById[submission.challengeId] ?: return@mapNotNull null

                RecentActivityItemResponse(
                    challengeId = challenge.id.toString(),
                    challengeTitle = challenge.title,
                    completedAt = submission.createdAt,
                    activityText = "Completed ${challenge.title}"
                )
            }

        return AthleteProfileResponse(
            teamName = team?.name,
            challengesCompleted = completedChallenges.size,
            currentChallengeCompletionStreak = calculateCompletionStreak(completedChallenges),
            recentActivity = recentActivity
        )
    }

    private fun calculateAge(dob: String): Int {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy")
        val birthDate = java.time.LocalDate.parse(dob, formatter)
        return java.time.Period.between(birthDate, java.time.LocalDate.now()).years
    }

    private fun calculateCompletionStreak(
        completedChallengeSubmissions: List<ChallengeSubmission>
    ): Int {
        if (completedChallengeSubmissions.isEmpty()) return 0

        val sorted = completedChallengeSubmissions.sortedByDescending { it.createdAt }

        var streak = 1

        for (i in 0 until sorted.size - 1) {
            val current = sorted[i]
            val next = sorted[i + 1]

            val diffMillis = current.createdAt - next.createdAt
            val diffDays = diffMillis / (1000L * 60 * 60 * 24)

            if (diffDays <= 7) {
                streak++
            } else {
                break
            }
        }

        return streak
    }
}