package com.example.myapplication.data.analysis

import com.example.myapplication.data.local.InterventionRecord
import com.example.myapplication.data.local.StudySessionEntity
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class StudyRescueAnalyticsTest {

    @Test
    fun testEmptyAnalyticsReturnsZeroedSummary() {
        val summary = StudyRescueAnalyticsEngine.calculateSummary(
            allSessions = emptyList(),
            allInterventions = emptyList(),
            timeRange = AnalyticsTimeRange.ALL_TIME
        )

        assertTrue(summary.isEmpty)
        assertEquals(0, summary.totalSessions)
        assertEquals(0, summary.completedSessions)
        assertEquals(0, summary.cancelledSessions)
        assertEquals(0, summary.activeOrOtherSessions)
        assertEquals(0, summary.totalDistractionEvents)
        assertEquals(0, summary.interventionOneCount)
        assertEquals(0, summary.interventionTwoCount)
        assertEquals(0, summary.ignoredInterventionCount)
        assertEquals(0, summary.focusRescueActivationCount)
        assertEquals(0, summary.focusRescueCompletionCount)
        assertEquals(0, summary.focusRescueExitCount)
        assertEquals(0L, summary.averageFocusRescueDurationSeconds)
        assertNull(summary.mostFrequentlyRestrictedCategory)
        assertTrue(summary.sessionCompletionTrend.isEmpty())
        assertTrue(summary.interventionTrend.isEmpty())
        assertEquals(0, summary.completionRatePercentage)
        assertEquals(0, summary.cancellationRatePercentage)
    }

    @Test
    fun testSingleSessionCompleted() {
        val now = System.currentTimeMillis()
        val session = StudySessionEntity(
            sessionId = "session-1",
            taskTitle = "Math Exam",
            plannedDurationMillis = 25 * 60 * 1000L,
            startTimeMillis = now - 30 * 60 * 1000L,
            endTimeMillis = now - 5 * 60 * 1000L,
            currentState = "SESSION_COMPLETED",
            createdAt = now - 30 * 60 * 1000L,
            updatedAt = now - 5 * 60 * 1000L
        )

        val summary = StudyRescueAnalyticsEngine.calculateSummary(
            allSessions = listOf(session),
            allInterventions = emptyList(),
            timeRange = AnalyticsTimeRange.ALL_TIME,
            nowMillis = now
        )

        assertFalse(summary.isEmpty)
        assertEquals(1, summary.totalSessions)
        assertEquals(1, summary.completedSessions)
        assertEquals(0, summary.cancelledSessions)
        assertEquals(100, summary.completionRatePercentage)
        assertEquals(0, summary.cancellationRatePercentage)
        assertEquals(1, summary.sessionCompletionTrend.size)
        assertEquals(1, summary.sessionCompletionTrend[0].completedSessions)
        assertEquals(0, summary.sessionCompletionTrend[0].cancelledSessions)
    }

    @Test
    fun testMultipleSessionsWithDifferentStates() {
        val now = System.currentTimeMillis()
        val s1 = StudySessionEntity("s1", "Task 1", 1500L, now - 4000L, now - 2000L, "SESSION_COMPLETED", createdAt = now - 4000L)
        val s2 = StudySessionEntity("s2", "Task 2", 1500L, now - 3000L, now - 1000L, "SESSION_COMPLETED", createdAt = now - 3000L)
        val s3 = StudySessionEntity("s3", "Task 3", 1500L, now - 2000L, now - 500L, "SESSION_CANCELLED", createdAt = now - 2000L)
        val s4 = StudySessionEntity("s4", "Task 4", 1500L, now - 1000L, null, "SESSION_ACTIVE", createdAt = now - 1000L)

        val summary = StudyRescueAnalyticsEngine.calculateSummary(
            allSessions = listOf(s1, s2, s3, s4),
            allInterventions = emptyList(),
            timeRange = AnalyticsTimeRange.ALL_TIME,
            nowMillis = now
        )

        assertEquals(4, summary.totalSessions)
        assertEquals(2, summary.completedSessions)
        assertEquals(1, summary.cancelledSessions)
        assertEquals(1, summary.activeOrOtherSessions)
        assertEquals(50, summary.completionRatePercentage)
        assertEquals(25, summary.cancellationRatePercentage)
    }

    @Test
    fun testIgnoredInterventionsAggregation() {
        val now = System.currentTimeMillis()
        val i1 = InterventionRecord(
            interventionId = "i1",
            sessionId = "s1",
            interventionNumber = 1,
            interventionType = "STAGE_1_GENTLE",
            triggeredAt = now - 5000L,
            respondedAt = now - 4000L,
            response = "RESUME",
            ignored = false
        )
        val i2 = InterventionRecord(
            interventionId = "i2",
            sessionId = "s1",
            interventionNumber = 1,
            interventionType = "STAGE_1_GENTLE",
            triggeredAt = now - 3000L,
            response = "TIMEOUT",
            ignored = true
        )
        val i3 = InterventionRecord(
            interventionId = "i3",
            sessionId = "s1",
            interventionNumber = 2,
            interventionType = "STAGE_2_ESCALATED",
            triggeredAt = now - 2000L,
            response = "TIMEOUT",
            ignored = true
        )

        val summary = StudyRescueAnalyticsEngine.calculateSummary(
            allSessions = emptyList(),
            allInterventions = listOf(i1, i2, i3),
            timeRange = AnalyticsTimeRange.ALL_TIME,
            nowMillis = now
        )

        assertEquals(3, summary.totalDistractionEvents)
        assertEquals(2, summary.interventionOneCount)
        assertEquals(1, summary.interventionTwoCount)
        assertEquals(2, summary.ignoredInterventionCount)
        assertEquals(1, summary.interventionTrend.size)
        assertEquals(2, summary.interventionTrend[0].stageOneCount)
        assertEquals(1, summary.interventionTrend[0].stageTwoCount)
        assertEquals(2, summary.interventionTrend[0].ignoredCount)
        assertEquals(3, summary.interventionTrend[0].totalCount)
    }

    @Test
    fun testFocusRescueCompletionAndDuration() {
        val now = System.currentTimeMillis()
        val s1 = StudySessionEntity(
            sessionId = "s1",
            taskTitle = "Study 1",
            plannedDurationMillis = 60000L,
            startTimeMillis = now - 100000L,
            currentState = "SESSION_ACTIVE",
            focusRescueStartTime = 1_000_000L,
            focusRescueEndTime = 1_060_000L,
            focusRescueCompletedAt = 1_060_000L, // 60s
            focusRescueState = "COMPLETED",
            createdAt = now - 100000L
        )

        val s2 = StudySessionEntity(
            sessionId = "s2",
            taskTitle = "Study 2",
            plannedDurationMillis = 60000L,
            startTimeMillis = now - 50000L,
            currentState = "SESSION_ACTIVE",
            focusRescueStartTime = 2_000_000L,
            focusRescueEndTime = 2_120_000L,
            focusRescueCompletedAt = 2_120_000L, // 120s
            focusRescueState = "COMPLETED",
            createdAt = now - 50000L
        )

        val summary = StudyRescueAnalyticsEngine.calculateSummary(
            allSessions = listOf(s1, s2),
            allInterventions = emptyList(),
            timeRange = AnalyticsTimeRange.ALL_TIME,
            nowMillis = now
        )

        assertEquals(2, summary.focusRescueActivationCount)
        assertEquals(2, summary.focusRescueCompletionCount)
        assertEquals(0, summary.focusRescueExitCount)
        assertEquals(90L, summary.averageFocusRescueDurationSeconds) // (60 + 120) / 2
    }

    @Test
    fun testFocusRescueManualExit() {
        val now = System.currentTimeMillis()
        val s1 = StudySessionEntity(
            sessionId = "s1",
            taskTitle = "Study 1",
            plannedDurationMillis = 60000L,
            startTimeMillis = now - 80000L,
            currentState = "SESSION_ACTIVE",
            focusRescueStartTime = 1_000_000L,
            focusRescueEndTime = 1_045_000L,
            focusRescueCompletedAt = 1_045_000L, // 45s
            focusRescueState = "EXITED",
            focusRescueExitReason = "MANUAL_EXIT",
            createdAt = now - 80000L
        )

        val summary = StudyRescueAnalyticsEngine.calculateSummary(
            allSessions = listOf(s1),
            allInterventions = emptyList(),
            timeRange = AnalyticsTimeRange.ALL_TIME,
            nowMillis = now
        )

        assertEquals(1, summary.focusRescueActivationCount)
        assertEquals(0, summary.focusRescueCompletionCount)
        assertEquals(1, summary.focusRescueExitCount)
        assertEquals(45L, summary.averageFocusRescueDurationSeconds)
    }

    @Test
    fun testDateFilteringPast7DaysVsAllTime() {
        val now = System.currentTimeMillis()
        val oneDayAgo = now - TimeUnit.DAYS.toMillis(1)
        val fiveDaysAgo = now - TimeUnit.DAYS.toMillis(5)
        val tenDaysAgo = now - TimeUnit.DAYS.toMillis(10)

        val s1 = StudySessionEntity("s1", "Today", 1000L, oneDayAgo, oneDayAgo + 500L, "SESSION_COMPLETED", createdAt = oneDayAgo)
        val s2 = StudySessionEntity("s2", "FiveDays", 1000L, fiveDaysAgo, fiveDaysAgo + 500L, "SESSION_COMPLETED", createdAt = fiveDaysAgo)
        val s3 = StudySessionEntity("s3", "TenDays", 1000L, tenDaysAgo, tenDaysAgo + 500L, "SESSION_COMPLETED", createdAt = tenDaysAgo)

        val past7DaysSummary = StudyRescueAnalyticsEngine.calculateSummary(
            allSessions = listOf(s1, s2, s3),
            allInterventions = emptyList(),
            timeRange = AnalyticsTimeRange.PAST_7_DAYS,
            nowMillis = now
        )

        val allTimeSummary = StudyRescueAnalyticsEngine.calculateSummary(
            allSessions = listOf(s1, s2, s3),
            allInterventions = emptyList(),
            timeRange = AnalyticsTimeRange.ALL_TIME,
            nowMillis = now
        )

        assertEquals(2, past7DaysSummary.totalSessions)
        assertEquals(3, allTimeSummary.totalSessions)
    }

    @Test
    fun testCategoryAggregationSafelyIdentifiesMostRestricted() {
        val now = System.currentTimeMillis()
        val s1 = StudySessionEntity(
            sessionId = "s1",
            taskTitle = "Session 1",
            plannedDurationMillis = 1000L,
            startTimeMillis = now,
            currentState = "SESSION_COMPLETED",
            selectedRestrictedAppPackages = listOf("com.instagram.android", "com.google.android.youtube"), // 2 Social
            createdAt = now
        )

        val s2 = StudySessionEntity(
            sessionId = "s2",
            taskTitle = "Session 2",
            plannedDurationMillis = 1000L,
            startTimeMillis = now,
            currentState = "SESSION_COMPLETED",
            selectedRestrictedAppPackages = listOf("com.slack"), // 1 Productive
            createdAt = now
        )

        val s3 = StudySessionEntity(
            sessionId = "s3",
            taskTitle = "Session 3",
            plannedDurationMillis = 1000L,
            startTimeMillis = now,
            currentState = "SESSION_COMPLETED",
            selectedRestrictedAppPackages = listOf("com.twitter.android"), // 1 Social -> Total Social: 3
            createdAt = now
        )

        val summary = StudyRescueAnalyticsEngine.calculateSummary(
            allSessions = listOf(s1, s2, s3),
            allInterventions = emptyList(),
            timeRange = AnalyticsTimeRange.ALL_TIME,
            nowMillis = now
        )

        assertEquals("Social", summary.mostFrequentlyRestrictedCategory)
    }

    @Test
    fun testAggregationAccuracyWithComprehensiveDataset() {
        val now = System.currentTimeMillis()
        val s1 = StudySessionEntity(
            sessionId = "s1",
            taskTitle = "Deep Work",
            plannedDurationMillis = 30 * 60 * 1000L,
            startTimeMillis = now - 60000L,
            endTimeMillis = now - 30000L,
            currentState = "SESSION_COMPLETED",
            selectedRestrictedAppPackages = listOf("com.facebook.katana"),
            createdAt = now - 60000L,
            focusRescueStartTime = 1000L,
            focusRescueCompletedAt = 4000L,
            focusRescueState = "COMPLETED"
        )
        val s2 = StudySessionEntity(
            sessionId = "s2",
            taskTitle = "Cancelled Session",
            plannedDurationMillis = 30 * 60 * 1000L,
            startTimeMillis = now - 20000L,
            endTimeMillis = now - 10000L,
            currentState = "SESSION_CANCELLED",
            createdAt = now - 20000L
        )

        val i1 = InterventionRecord("i1", "s1", 1, "STAGE_1", now - 50000L, response = "RESUME", ignored = false)
        val i2 = InterventionRecord("i2", "s1", 2, "STAGE_2", now - 40000L, response = "TIMEOUT", ignored = true)

        val summary = StudyRescueAnalyticsEngine.calculateSummary(
            allSessions = listOf(s1, s2),
            allInterventions = listOf(i1, i2),
            timeRange = AnalyticsTimeRange.ALL_TIME,
            nowMillis = now
        )

        assertEquals(2, summary.totalSessions)
        assertEquals(1, summary.completedSessions)
        assertEquals(1, summary.cancelledSessions)
        assertEquals(0, summary.activeOrOtherSessions)
        assertEquals(2, summary.totalDistractionEvents)
        assertEquals(1, summary.interventionOneCount)
        assertEquals(1, summary.interventionTwoCount)
        assertEquals(1, summary.ignoredInterventionCount)
        assertEquals(1, summary.focusRescueActivationCount)
        assertEquals(1, summary.focusRescueCompletionCount)
        assertEquals(0, summary.focusRescueExitCount)
        assertEquals(3L, summary.averageFocusRescueDurationSeconds) // (4000 - 1000) / 1000 = 3s
        assertEquals("Social", summary.mostFrequentlyRestrictedCategory)
        assertEquals(50, summary.completionRatePercentage)
        assertEquals(50, summary.cancellationRatePercentage)
    }
}
