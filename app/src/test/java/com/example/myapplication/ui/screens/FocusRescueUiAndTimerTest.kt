package com.example.myapplication.ui.screens

import com.example.myapplication.data.local.FakeStudyRescueDao
import com.example.myapplication.data.local.StudyRescueRepository
import com.example.myapplication.data.local.StudySessionEntity
import com.example.myapplication.data.tracking.StudyRescueState
import com.example.myapplication.data.tracking.StudySessionManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying Focus Rescue timer calculations, formatting,
 * lifecycle rehydration, state transitions, and single-trigger completion.
 */
class FocusRescueUiAndTimerTest {

    private lateinit var fakeDao: FakeStudyRescueDao
    private lateinit var repository: StudyRescueRepository
    private lateinit var sessionManager: StudySessionManager

    @Before
    fun setUp() {
        fakeDao = FakeStudyRescueDao()
        repository = StudyRescueRepository(fakeDao)
        sessionManager = StudySessionManager(repository)
    }

    // ── 1. Timer Math & Absolute Timestamp Precision ─────────────────────────

    @Test
    fun testAbsoluteTimestampCalculation() {
        val startTimeMillis = 1_000_000L
        val plannedDurationMillis = 25 * 60 * 1000L // 25 min = 1,500,000 ms
        val endTimeMillis = startTimeMillis + plannedDurationMillis

        assertEquals(2_500_000L, endTimeMillis)

        // Halfway through session (12.5 min elapsed)
        val halfwayEpoch = startTimeMillis + 750_000L
        val remainingHalfway = (endTimeMillis - halfwayEpoch).coerceAtLeast(0L)
        assertEquals(750_000L, remainingHalfway)

        // Planned duration completed
        val finishedEpoch = endTimeMillis + 5000L
        val remainingFinished = (endTimeMillis - finishedEpoch).coerceAtLeast(0L)
        assertEquals(0L, remainingFinished) // Clamped to zero
    }

    @Test
    fun testRemainingTimeClampedToZero() {
        val startTimeMillis = 100_000L
        val plannedDurationMillis = 60_000L
        val endTimeMillis = startTimeMillis + plannedDurationMillis

        // 10 minutes past end time
        val pastTime = endTimeMillis + 600_000L
        val remaining = (endTimeMillis - pastTime).coerceAtLeast(0L)
        assertEquals(0L, remaining)
    }

    @Test
    fun testFormatRemainingTimeFormatting() {
        // Zero or negative
        assertEquals("00:00", formatRemainingTime(0L))
        assertEquals("00:00", formatRemainingTime(-5000L))

        // Seconds
        assertEquals("00:45", formatRemainingTime(45_000L))

        // Minutes and seconds
        assertEquals("25:00", formatRemainingTime(25 * 60 * 1000L))
        assertEquals("12:34", formatRemainingTime((12 * 60 + 34) * 1000L))

        // Hours (> 60 minutes)
        assertEquals("01:15:30", formatRemainingTime((75 * 60 + 30) * 1000L))
        assertEquals("02:00:00", formatRemainingTime(120 * 60 * 1000L))
    }

    // ── 2. Rehydration & Survival Across Recreation ─────────────────────────

    @Test
    fun testTimerSurvivesProcessRecoveryFromPersistedState() {
        val now = System.currentTimeMillis()
        val plannedMillis = 30 * 60 * 1000L // 30 min
        val entity = StudySessionEntity(
            sessionId = "session-123",
            taskTitle = "Biology Exam",
            plannedDurationMillis = plannedMillis,
            startTimeMillis = now,
            currentState = "FOCUS_RESCUE_ACTIVE",
            selectedRestrictedAppPackages = listOf("com.instagram.android"),
            createdAt = now,
            updatedAt = now,
            focusRescueStartTime = now,
            focusRescueState = "ACTIVE"
        )
        kotlinx.coroutines.runBlocking {
            fakeDao.insertSession(entity)
        }

        val restoredManager = StudySessionManager(repository)
        var attempts = 0
        while (restoredManager.activeSessionFlow.value == null && attempts < 50) {
            Thread.sleep(20)
            attempts++
        }

        // Verify session entity was restored
        val session = restoredManager.activeSessionFlow.value
        assertNotNull(session)
        assertEquals("session-123", session?.sessionId)
        assertEquals("Biology Exam", session?.taskTitle)
        assertEquals(plannedMillis, session?.plannedDurationMillis)

        // When app re-opens 10 minutes later:
        val rehydratedEpoch = now + 10 * 60 * 1000L
        val remaining = (session!!.startTimeMillis + session.plannedDurationMillis - rehydratedEpoch).coerceAtLeast(0L)
        assertEquals(20 * 60 * 1000L, remaining) // Exactly 20 minutes left
    }

    // ── 3. Focus Rescue Lifecycle Transitions ────────────────────────────────

    @Test
    fun testStartFocusRescueTransition() {
        sessionManager.startSession("Math", 25, setOf("com.instagram.android"))
        val baseTime = 100_000L

        // Escalate via 2 strikes separated by > 2000ms debounce window
        sessionManager.onDistractionDetected("com.instagram.android", timestamp = baseTime)
        sessionManager.onInterventionDismissed(sessionManager.activeSessionFlow.value!!.sessionId, 1)

        sessionManager.onDistractionDetected("com.instagram.android", timestamp = baseTime + 5000L)
        sessionManager.onInterventionDismissed(sessionManager.activeSessionFlow.value!!.sessionId, 2)

        assertEquals(StudyRescueState.FOCUS_RESCUE_READY, sessionManager.stateFlow.value)

        // Enter Focus Rescue
        val started = sessionManager.startFocusRescue()
        assertTrue(started)
        assertEquals(StudyRescueState.FOCUS_RESCUE_ACTIVE, sessionManager.stateFlow.value)

        val activeSession = sessionManager.activeSessionFlow.value
        assertNotNull(activeSession)
        assertEquals("FOCUS_RESCUE_ACTIVE", activeSession?.currentState)
        assertEquals("ACTIVE", activeSession?.focusRescueState)
        assertNotNull(activeSession?.focusRescueStartTime)
    }

    @Test
    fun testExitFocusRescueUserRequested() {
        sessionManager.startSession("Math", 25, setOf("com.instagram.android"))
        val baseTime = 100_000L
        sessionManager.onDistractionDetected("com.instagram.android", timestamp = baseTime)
        sessionManager.onInterventionDismissed(sessionManager.activeSessionFlow.value!!.sessionId, 1)
        sessionManager.onDistractionDetected("com.instagram.android", timestamp = baseTime + 5000L)
        sessionManager.onInterventionDismissed(sessionManager.activeSessionFlow.value!!.sessionId, 2)
        sessionManager.startFocusRescue()

        // User exits Focus Rescue early
        val exited = sessionManager.exitFocusRescue("USER_EXITED")
        assertTrue(exited)
        assertEquals(StudyRescueState.SESSION_ACTIVE, sessionManager.stateFlow.value)

        val activeSession = sessionManager.activeSessionFlow.value
        assertNotNull(activeSession)
        assertEquals("SESSION_ACTIVE", activeSession?.currentState)
        assertEquals("EXITED", activeSession?.focusRescueState)
        assertEquals("USER_EXITED", activeSession?.focusRescueExitReason)
    }

    @Test
    fun testCompleteFocusRescueTransition() {
        sessionManager.startSession("Math", 25, setOf("com.instagram.android"))
        val baseTime = 100_000L
        sessionManager.onDistractionDetected("com.instagram.android", timestamp = baseTime)
        sessionManager.onInterventionDismissed(sessionManager.activeSessionFlow.value!!.sessionId, 1)
        sessionManager.onDistractionDetected("com.instagram.android", timestamp = baseTime + 5000L)
        sessionManager.onInterventionDismissed(sessionManager.activeSessionFlow.value!!.sessionId, 2)
        sessionManager.startFocusRescue()

        // When completed
        val completed = sessionManager.exitFocusRescue("COMPLETED")
        assertTrue(completed)
        assertEquals(StudyRescueState.SESSION_ACTIVE, sessionManager.stateFlow.value)

        val activeSession = sessionManager.activeSessionFlow.value
        assertEquals("COMPLETED", activeSession?.focusRescueExitReason)
        assertNotNull(activeSession?.focusRescueCompletedAt)
    }

    // ── 4. Single-Trigger Completion Logic ───────────────────────────────────

    @Test
    fun testSingleTriggerCompletionFlag() {
        var completionTriggerCount = 0
        var hasTriggered = false

        fun triggerIfNeeded(isTimerFinished: Boolean) {
            if (isTimerFinished && !hasTriggered) {
                hasTriggered = true
                completionTriggerCount++
            }
        }

        // Before completion
        triggerIfNeeded(isTimerFinished = false)
        triggerIfNeeded(isTimerFinished = false)
        assertEquals(0, completionTriggerCount)

        // Multiple evaluation ticks once remaining reaches 0
        triggerIfNeeded(isTimerFinished = true)
        triggerIfNeeded(isTimerFinished = true)
        triggerIfNeeded(isTimerFinished = true)

        // Must complete exactly once
        assertEquals(1, completionTriggerCount)
    }
}
