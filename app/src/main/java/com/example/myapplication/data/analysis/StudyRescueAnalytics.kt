package com.example.myapplication.data.analysis

import com.example.myapplication.data.local.InterventionRecord
import com.example.myapplication.data.local.StudySessionEntity
import com.example.myapplication.data.tracking.AppCategory
import com.example.myapplication.data.tracking.AppCategoryClassifier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Filter time ranges supported by Study Rescue analytics.
 */
enum class AnalyticsTimeRange(val label: String, val days: Int?) {
    PAST_7_DAYS("Past 7 Days", 7),
    PAST_30_DAYS("Past 30 Days", 30),
    ALL_TIME("All Time", null)
}

/**
 * Daily aggregation data point for study session completion trends.
 */
data class DailySessionTrend(
    val date: String, // "yyyy-MM-dd"
    val timestamp: Long,
    val totalSessions: Int,
    val completedSessions: Int,
    val cancelledSessions: Int
)

/**
 * Daily aggregation data point for intervention trends over time.
 */
data class DailyInterventionTrend(
    val date: String, // "yyyy-MM-dd"
    val timestamp: Long,
    val stageOneCount: Int,
    val stageTwoCount: Int,
    val ignoredCount: Int,
    val totalCount: Int
)

/**
 * Complete aggregated summary of Study Rescue intervention analytics and trends.
 * Fully privacy-preserving: contains only counts, rates, averages, and categories.
 */
data class StudyRescueAnalyticsSummary(
    val totalSessions: Int = 0,
    val completedSessions: Int = 0,
    val cancelledSessions: Int = 0,
    val activeOrOtherSessions: Int = 0,
    val totalDistractionEvents: Int = 0,
    val interventionOneCount: Int = 0,
    val interventionTwoCount: Int = 0,
    val ignoredInterventionCount: Int = 0,
    val focusRescueActivationCount: Int = 0,
    val focusRescueCompletionCount: Int = 0,
    val focusRescueExitCount: Int = 0,
    val averageFocusRescueDurationSeconds: Long = 0L,
    val mostFrequentlyRestrictedCategory: String? = null,
    val sessionCompletionTrend: List<DailySessionTrend> = emptyList(),
    val interventionTrend: List<DailyInterventionTrend> = emptyList()
) {
    val completionRatePercentage: Int
        get() = if (totalSessions > 0) ((completedSessions.toFloat() / totalSessions) * 100).toInt() else 0

    val cancellationRatePercentage: Int
        get() = if (totalSessions > 0) ((cancelledSessions.toFloat() / totalSessions) * 100).toInt() else 0

    val isEmpty: Boolean
        get() = totalSessions == 0 && totalDistractionEvents == 0
}

/**
 * Pure calculation engine for aggregating Study Rescue metrics from persisted records.
 * Completely decoupled from database and UI to guarantee testability and thread safety.
 */
object StudyRescueAnalyticsEngine {

    fun calculateSummary(
        allSessions: List<StudySessionEntity>,
        allInterventions: List<InterventionRecord>,
        timeRange: AnalyticsTimeRange = AnalyticsTimeRange.ALL_TIME,
        nowMillis: Long = System.currentTimeMillis()
    ): StudyRescueAnalyticsSummary {
        val startMillis = timeRange.days?.let { days ->
            nowMillis - TimeUnit.DAYS.toMillis(days.toLong())
        } ?: 0L

        // Filter sessions by selected time range
        val filteredSessions = allSessions.filter { session ->
            session.createdAt in startMillis..nowMillis
        }

        // Filter interventions by selected time range
        val filteredInterventions = allInterventions.filter { intervention ->
            intervention.triggeredAt in startMillis..nowMillis
        }

        val totalSessions = filteredSessions.size
        val completedSessions = filteredSessions.count { it.currentState == "SESSION_COMPLETED" }
        val cancelledSessions = filteredSessions.count { it.currentState == "SESSION_CANCELLED" }
        val activeOrOtherSessions = totalSessions - completedSessions - cancelledSessions

        // Each recorded intervention corresponds to a detected distraction during study
        val totalDistractions = filteredInterventions.size
        val interventionOne = filteredInterventions.count { it.interventionNumber == 1 }
        val interventionTwo = filteredInterventions.count { it.interventionNumber == 2 }
        val ignoredCount = filteredInterventions.count { it.ignored }

        val focusRescueActivations = filteredSessions.count { session ->
            session.focusRescueStartTime != null ||
            session.focusRescueState in listOf("ACTIVE", "COMPLETED", "EXITED")
        }
        val focusRescueCompletions = filteredSessions.count { it.focusRescueState == "COMPLETED" }
        val focusRescueExits = filteredSessions.count { it.focusRescueState == "EXITED" }

        // Compute average Focus Rescue duration across completed and exited sessions
        val completedOrExitedRescueSessions = filteredSessions.filter { session ->
            session.focusRescueStartTime != null &&
            (session.focusRescueState == "COMPLETED" || session.focusRescueState == "EXITED")
        }

        val durationsSeconds = completedOrExitedRescueSessions.mapNotNull { session ->
            val start = session.focusRescueStartTime ?: return@mapNotNull null
            val end = session.focusRescueCompletedAt ?: session.focusRescueEndTime ?: return@mapNotNull null
            if (end >= start) {
                (end - start) / 1000L
            } else null
        }

        val avgDurationSeconds = if (durationsSeconds.isNotEmpty()) {
            durationsSeconds.average().toLong()
        } else {
            0L
        }

        // Compute most frequently restricted application category safely
        val categoryCounts = mutableMapOf<AppCategory, Int>()
        filteredSessions.forEach { session ->
            session.selectedRestrictedAppPackages.forEach { pkg ->
                val category = AppCategoryClassifier.classify(pkg)
                categoryCounts[category] = (categoryCounts[category] ?: 0) + 1
            }
        }

        val mostFrequentCategory = categoryCounts.maxByOrNull { it.value }?.let { (category, count) ->
            if (count > 0) {
                when (category) {
                    AppCategory.SOCIAL -> "Social"
                    AppCategory.PRODUCTIVE -> "Productive"
                    AppCategory.OTHER -> "Other"
                }
            } else null
        }

        // Group study sessions into daily completion trends
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val sessionsByDate = filteredSessions.groupBy { session ->
            dateFormat.format(Date(session.createdAt))
        }

        val sessionCompletionTrend = sessionsByDate.map { (dateStr, sessionsOnDate) ->
            val total = sessionsOnDate.size
            val completed = sessionsOnDate.count { it.currentState == "SESSION_COMPLETED" }
            val cancelled = sessionsOnDate.count { it.currentState == "SESSION_CANCELLED" }
            val timestamp = sessionsOnDate.minOfOrNull { it.createdAt } ?: 0L
            DailySessionTrend(
                date = dateStr,
                timestamp = timestamp,
                totalSessions = total,
                completedSessions = completed,
                cancelledSessions = cancelled
            )
        }.sortedBy { it.date }

        // Group interventions into daily trend breakdown
        val interventionsByDate = filteredInterventions.groupBy { intervention ->
            dateFormat.format(Date(intervention.triggeredAt))
        }

        val interventionTrend = interventionsByDate.map { (dateStr, interventionsOnDate) ->
            val total = interventionsOnDate.size
            val s1 = interventionsOnDate.count { it.interventionNumber == 1 }
            val s2 = interventionsOnDate.count { it.interventionNumber == 2 }
            val ign = interventionsOnDate.count { it.ignored }
            val timestamp = interventionsOnDate.minOfOrNull { it.triggeredAt } ?: 0L
            DailyInterventionTrend(
                date = dateStr,
                timestamp = timestamp,
                stageOneCount = s1,
                stageTwoCount = s2,
                ignoredCount = ign,
                totalCount = total
            )
        }.sortedBy { it.date }

        return StudyRescueAnalyticsSummary(
            totalSessions = totalSessions,
            completedSessions = completedSessions,
            cancelledSessions = cancelledSessions,
            activeOrOtherSessions = activeOrOtherSessions,
            totalDistractionEvents = totalDistractions,
            interventionOneCount = interventionOne,
            interventionTwoCount = interventionTwo,
            ignoredInterventionCount = ignoredCount,
            focusRescueActivationCount = focusRescueActivations,
            focusRescueCompletionCount = focusRescueCompletions,
            focusRescueExitCount = focusRescueExits,
            averageFocusRescueDurationSeconds = avgDurationSeconds,
            mostFrequentlyRestrictedCategory = mostFrequentCategory,
            sessionCompletionTrend = sessionCompletionTrend,
            interventionTrend = interventionTrend
        )
    }
}
