package com.example.myapplication.data.tracking

import com.example.myapplication.data.local.FakeStudyRescueDao
import com.example.myapplication.data.local.StudyRescueRepository
import com.example.myapplication.data.local.StudySessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * STRICT Verification Unit Tests for Module 9: FocusRescueController
 *
 * Verifies all 20 required specifications:
 * 1. exact end timestamp calculation
 * 2. 15m duration
 * 3. 25m duration
 * 4. 45m duration
 * 5. 60m duration
 * 6. 90m duration if supported
 * 7. remaining time
 * 8. expiration
 * 9. process recovery
 * 10. expired process recovery
 * 11. duplicate start
 * 12. user exit
 * 13. completion
 * 14. completion idempotency
 * 15. cooldown active
 * 16. cooldown expiration
 * 17. cooldown process recovery
 * 18. two-intervention eligibility
 * 19. direct start while not eligible
 * 20. simultaneous/repeated expiration handling
 */
class FocusRescueControllerTest {

    private lateinit var fakeDao: FakeStudyRescueDao
    private lateinit var repository: StudyRescueRepository
    private lateinit var sessionManager: StudySessionManager
    private lateinit var controller: FocusRescueController

    private val testCooldownMs = 60_000L // 1 minute for tests

    @Before
    fun setUp() {
        fakeDao = FakeStudyRescueDao()
        repository = StudyRescueRepository(fakeDao)
        sessionManager = StudySessionManager(repository)
        controller = DefaultFocusRescueController(
            sessionManagerProvider = { sessionManager },
            repository = repository,
            cooldownDurationMs = testCooldownMs
        )
    }

    private fun prepareEligibleSession(durationMinutes: Int = 25, taskTitle: String = "Test Study Task") {
        sessionManager.startSession(taskTitle, durationMinutes, setOf("com.instagram.android"))
        val baseTime = 100_000L

        // Trigger two distinct strikes separated by > 2000ms debounce window
        sessionManager.onDistractionDetected("com.instagram.android", timestamp = baseTime)
        sessionManager.onInterventionDismissed(sessionManager.activeSessionFlow.value!!.sessionId, 1)

        sessionManager.onDistractionDetected("com.instagram.android", timestamp = baseTime + 5000L)
        sessionManager.onInterventionDismissed(sessionManager.activeSessionFlow.value!!.sessionId, 2)

        assertEquals(StudyRescueState.FOCUS_RESCUE_READY, sessionManager.stateFlow.value)
    }

    // ── 1. Exact End Timestamp Calculation ───────────────────────────────────

    @Test
    fun test1_ExactEndTimestampCalculation() {
        prepareEligibleSession(25)
        val startTime = 250_000L
        val plannedMillis = 25 * 60 * 1000L

        val result = controller.startFocusRescue(startTime)
        assertTrue(result is FocusRescueStartResult.Success)

        val ts = (result as FocusRescueStartResult.Success).timestamps
        assertEquals(startTime, ts.startTimeMillis)
        assertEquals(plannedMillis, ts.plannedDurationMillis)
        assertEquals(startTime + plannedMillis, ts.endTimeMillis)

        // Verify entity in state manager and Room
        val session = sessionManager.activeSessionFlow.value
        assertNotNull(session)
        assertEquals(startTime, session?.focusRescueStartTime)
        assertEquals(startTime + plannedMillis, session?.focusRescueEndTime)
    }

    // ── 2. 15m Duration ──────────────────────────────────────────────────────

    @Test
    fun test2_15MinuteDuration() {
        val minutes = 15
        prepareEligibleSession(minutes)
        val startTime = 50_000L
        val expectedDurationMillis = 15 * 60 * 1000L

        val result = controller.startFocusRescue(startTime)
        assertTrue(result is FocusRescueStartResult.Success)
        val ts = (result as FocusRescueStartResult.Success).timestamps

        assertEquals(expectedDurationMillis, ts.plannedDurationMillis)
        assertEquals(startTime + expectedDurationMillis, ts.endTimeMillis)
    }

    // ── 3. 25m Duration ──────────────────────────────────────────────────────

    @Test
    fun test3_25MinuteDuration() {
        val minutes = 25
        prepareEligibleSession(minutes)
        val startTime = 60_000L
        val expectedDurationMillis = 25 * 60 * 1000L

        val result = controller.startFocusRescue(startTime)
        assertTrue(result is FocusRescueStartResult.Success)
        val ts = (result as FocusRescueStartResult.Success).timestamps

        assertEquals(expectedDurationMillis, ts.plannedDurationMillis)
        assertEquals(startTime + expectedDurationMillis, ts.endTimeMillis)
    }

    // ── 4. 45m Duration ──────────────────────────────────────────────────────

    @Test
    fun test4_45MinuteDuration() {
        val minutes = 45
        prepareEligibleSession(minutes)
        val startTime = 70_000L
        val expectedDurationMillis = 45 * 60 * 1000L

        val result = controller.startFocusRescue(startTime)
        assertTrue(result is FocusRescueStartResult.Success)
        val ts = (result as FocusRescueStartResult.Success).timestamps

        assertEquals(expectedDurationMillis, ts.plannedDurationMillis)
        assertEquals(startTime + expectedDurationMillis, ts.endTimeMillis)
    }

    // ── 5. 60m Duration ──────────────────────────────────────────────────────

    @Test
    fun test5_60MinuteDuration() {
        val minutes = 60
        prepareEligibleSession(minutes)
        val startTime = 80_000L
        val expectedDurationMillis = 60 * 60 * 1000L

        val result = controller.startFocusRescue(startTime)
        assertTrue(result is FocusRescueStartResult.Success)
        val ts = (result as FocusRescueStartResult.Success).timestamps

        assertEquals(expectedDurationMillis, ts.plannedDurationMillis)
        assertEquals(startTime + expectedDurationMillis, ts.endTimeMillis)
    }

    // ── 6. 90m Duration ──────────────────────────────────────────────────────

    @Test
    fun test6_90MinuteDuration() {
        val minutes = 90
        prepareEligibleSession(minutes)
        val startTime = 90_000L
        val expectedDurationMillis = 90 * 60 * 1000L

        val result = controller.startFocusRescue(startTime)
        assertTrue(result is FocusRescueStartResult.Success)
        val ts = (result as FocusRescueStartResult.Success).timestamps

        assertEquals(expectedDurationMillis, ts.plannedDurationMillis)
        assertEquals(startTime + expectedDurationMillis, ts.endTimeMillis)
    }

    // ── 7. Remaining Time Calculation ────────────────────────────────────────

    @Test
    fun test7_RemainingTimeCalculation() {
        prepareEligibleSession(25) // 25 min = 1,500,000 ms
        val startTime = 1_000_000L
        controller.startFocusRescue(startTime)

        // At exact start
        assertEquals(1_500_000L, controller.getRemainingMillis(startTime))

        // 10 minutes in (600,000 ms elapsed)
        val t1 = startTime + 600_000L
        assertEquals(900_000L, controller.getRemainingMillis(t1))

        // Exact end time
        val tEnd = startTime + 1_500_000L
        assertEquals(0L, controller.getRemainingMillis(tEnd))

        // Past end time: must clamp to 0
        val tPast = tEnd + 10_000L
        assertEquals(0L, controller.getRemainingMillis(tPast))
    }

    // ── 8. Expiration Detection ──────────────────────────────────────────────

    @Test
    fun test8_ExpirationDetection() {
        prepareEligibleSession(25)
        val startTime = 100_000L
        controller.startFocusRescue(startTime)

        val endTime = startTime + 25 * 60 * 1000L

        // Before expiry: should not complete
        val beforeExpiry = endTime - 1000L
        val completedBefore = controller.checkAndHandleExpiration(beforeExpiry)
        assertFalse(completedBefore)
        assertTrue(controller.isFocusRescueActive)

        // At or after expiry: immediately completes
        val completedAfter = controller.checkAndHandleExpiration(endTime + 500L)
        assertTrue(completedAfter)
        assertFalse(controller.isFocusRescueActive)

        // Study session remains active! Focus Rescue completion does NOT end entire study session
        assertEquals(StudyRescueState.SESSION_ACTIVE, sessionManager.stateFlow.value)
        val session = sessionManager.activeSessionFlow.value
        assertEquals("SESSION_ACTIVE", session?.currentState)
        assertEquals("COMPLETED", session?.focusRescueState)
    }

    // ── 9. Process Recovery ──────────────────────────────────────────────────

    @Test
    fun test9_ProcessRecovery() {
        val now = System.currentTimeMillis()
        val plannedMillis = 30 * 60 * 1000L
        val startTime = now - 5 * 60 * 1000L // Started 5 mins ago
        val endTime = startTime + plannedMillis

        val entity = StudySessionEntity(
            sessionId = "session-recovered-1",
            taskTitle = "Exam Prep",
            plannedDurationMillis = plannedMillis,
            startTimeMillis = startTime,
            currentState = "FOCUS_RESCUE_ACTIVE",
            selectedRestrictedAppPackages = listOf("com.instagram.android"),
            createdAt = startTime,
            updatedAt = startTime,
            focusRescueStartTime = startTime,
            focusRescueEndTime = endTime,
            focusRescueState = "ACTIVE"
        )
        runBlocking {
            fakeDao.insertSession(entity)
        }

        val restoredManager = StudySessionManager(repository)
        var attempts = 0
        while (restoredManager.activeSessionFlow.value == null && attempts < 50) {
            Thread.sleep(20)
            attempts++
        }

        val restoredController = DefaultFocusRescueController(
            sessionManagerProvider = { restoredManager },
            repository = repository
        )

        val restored = restoredController.restoreFromSession(entity, now)
        assertTrue(restored)

        val ts = restoredController.currentTimestamps
        assertNotNull(ts)
        assertEquals(startTime, ts?.startTimeMillis)
        assertEquals(plannedMillis, ts?.plannedDurationMillis)
        assertEquals(endTime, ts?.endTimeMillis)

        // Remaining time accounts for 5 minutes already elapsed
        val remaining = restoredController.getRemainingMillis(now)
        assertEquals(25 * 60 * 1000L, remaining)
    }

    // ── 10. Expired Process Recovery ─────────────────────────────────────────

    @Test
    fun test10_ExpiredProcessRecovery() {
        val now = System.currentTimeMillis()
        val studyPlannedMillis = 60 * 60 * 1000L // 60 min study session
        val startTime = now - 30 * 60 * 1000L // Started 30 mins ago
        val focusRescueEndTime = startTime + 15 * 60 * 1000L // Focus Rescue expired 15 mins ago

        val entity = StudySessionEntity(
            sessionId = "session-expired-closed",
            taskTitle = "History Study",
            plannedDurationMillis = studyPlannedMillis,
            startTimeMillis = startTime,
            currentState = "FOCUS_RESCUE_ACTIVE",
            selectedRestrictedAppPackages = listOf("com.instagram.android"),
            createdAt = startTime,
            updatedAt = startTime,
            focusRescueStartTime = startTime,
            focusRescueEndTime = focusRescueEndTime,
            focusRescueState = "ACTIVE"
        )
        runBlocking {
            fakeDao.insertSession(entity)
        }

        val restoredManager = StudySessionManager(repository)
        var attempts = 0
        while (restoredManager.activeSessionFlow.value == null && attempts < 50) {
            Thread.sleep(20)
            attempts++
        }

        val restoredController = DefaultFocusRescueController(
            sessionManagerProvider = { restoredManager },
            repository = repository
        )

        // Restoring an expired Focus Rescue session immediately marks it completed
        val restored = restoredController.restoreFromSession(entity, now)
        assertFalse(restored) // Was expired and completed
        assertFalse(restoredController.isFocusRescueActive)

        val updated = restoredManager.activeSessionFlow.value
        assertEquals("COMPLETED", updated?.focusRescueState)
    }

    // ── 11. Duplicate Start Prevention ───────────────────────────────────────

    @Test
    fun test11_DuplicateStartPrevention() {
        prepareEligibleSession(25)
        val startTime = 100_000L

        // First start succeeds
        val result1 = controller.startFocusRescue(startTime)
        assertTrue(result1 is FocusRescueStartResult.Success)

        // Second start while already active is rejected
        val result2 = controller.startFocusRescue(startTime + 1000L)
        assertEquals(FocusRescueStartResult.AlreadyActive, result2)

        // Authoritative start timestamp remains unchanged
        assertEquals(startTime, controller.currentTimestamps?.startTimeMillis)
    }

    // ── 12. User Exit Handling ───────────────────────────────────────────────

    @Test
    fun test12_UserExitHandling() {
        prepareEligibleSession(25)
        val startTime = 100_000L
        controller.startFocusRescue(startTime)
        assertTrue(controller.isFocusRescueActive)

        val exitTime = startTime + 5 * 60 * 1000L
        val exitResult = controller.exitFocusRescue("USER_EXITED", exitTime)
        assertTrue(exitResult)

        assertFalse(controller.isFocusRescueActive)
        assertEquals(StudyRescueState.SESSION_ACTIVE, sessionManager.stateFlow.value)

        val session = sessionManager.activeSessionFlow.value
        assertEquals("SESSION_ACTIVE", session?.currentState)
        assertEquals("EXITED", session?.focusRescueState)
        assertEquals("USER_EXITED", session?.focusRescueExitReason)
        assertEquals(exitTime, session?.focusRescueEndTime)
        assertEquals(exitTime + testCooldownMs, session?.cooldownUntil)
    }

    // ── 13. Completion Behavior ──────────────────────────────────────────────

    @Test
    fun test13_CompletionBehavior() {
        prepareEligibleSession(25)
        val startTime = 100_000L
        controller.startFocusRescue(startTime)

        val completionTime = startTime + 25 * 60 * 1000L
        val completed = controller.completeFocusRescue(completionTime)
        assertTrue(completed)
        assertFalse(controller.isFocusRescueActive)

        // Returns to SESSION_ACTIVE, does not complete entire study session
        assertEquals(StudyRescueState.SESSION_ACTIVE, sessionManager.stateFlow.value)
        val session = sessionManager.activeSessionFlow.value
        assertEquals("SESSION_ACTIVE", session?.currentState)
        assertEquals("COMPLETED", session?.focusRescueState)
        assertEquals("COMPLETED", session?.focusRescueExitReason)
        assertEquals(completionTime, session?.focusRescueCompletedAt)
        assertEquals(completionTime + testCooldownMs, session?.cooldownUntil)
    }

    // ── 14. Completion Idempotency ───────────────────────────────────────────

    @Test
    fun test14_CompletionIdempotency() {
        prepareEligibleSession(25)
        val startTime = 100_000L
        controller.startFocusRescue(startTime)

        val completionTime = startTime + 25 * 60 * 1000L

        // First completion
        val first = controller.completeFocusRescue(completionTime)
        assertTrue(first)
        assertFalse(controller.isFocusRescueActive)

        // Subsequent completions must be idempotent no-ops returning true without modifying timestamps
        val second = controller.completeFocusRescue(completionTime + 1000L)
        assertTrue(second)
        val third = controller.completeFocusRescue(completionTime + 2000L)
        assertTrue(third)

        val session = sessionManager.activeSessionFlow.value
        assertEquals("COMPLETED", session?.focusRescueState)
        assertEquals(completionTime, session?.focusRescueCompletedAt) // Original timestamp preserved
    }

    // ── 15. Cooldown Active ──────────────────────────────────────────────────

    @Test
    fun test15_CooldownActive() {
        prepareEligibleSession(25)
        val startTime = 100_000L
        controller.startFocusRescue(startTime)

        val exitTime = startTime + 10_000L
        controller.exitFocusRescue("USER_EXITED", exitTime)

        // During cooldown window
        val checkTime = exitTime + 20_000L
        assertTrue(controller.isInCooldown(checkTime))
        assertEquals(testCooldownMs - 20_000L, controller.getRemainingCooldownMillis(checkTime))

        val attempt = controller.startFocusRescue(checkTime)
        assertTrue(attempt is FocusRescueStartResult.CooldownActive)
        assertEquals(testCooldownMs - 20_000L, (attempt as FocusRescueStartResult.CooldownActive).remainingCooldownMillis)
    }

    // ── 16. Cooldown Expiration ──────────────────────────────────────────────

    @Test
    fun test16_CooldownExpiration() {
        prepareEligibleSession(25)
        val startTime = 100_000L
        controller.startFocusRescue(startTime)

        val exitTime = startTime + 10_000L
        controller.exitFocusRescue("USER_EXITED", exitTime)

        // After cooldown expiration
        val postCooldownTime = exitTime + testCooldownMs + 1000L
        assertFalse(controller.isInCooldown(postCooldownTime))
        assertEquals(0L, controller.getRemainingCooldownMillis(postCooldownTime))
    }

    // ── 17. Cooldown Process Recovery ────────────────────────────────────────

    @Test
    fun test17_CooldownProcessRecovery() {
        val now = System.currentTimeMillis()
        val cooldownTarget = now + 45_000L // 45 seconds cooldown remaining

        val entity = StudySessionEntity(
            sessionId = "session-cooldown-recovery",
            taskTitle = "Exam",
            plannedDurationMillis = 30 * 60 * 1000L,
            startTimeMillis = now - 10 * 60 * 1000L,
            currentState = "SESSION_ACTIVE",
            createdAt = now - 10 * 60 * 1000L,
            updatedAt = now,
            cooldownUntil = cooldownTarget
        )
        runBlocking {
            fakeDao.insertSession(entity)
        }

        val restoredManager = StudySessionManager(repository)
        var attempts = 0
        while (restoredManager.activeSessionFlow.value == null && attempts < 50) {
            Thread.sleep(20)
            attempts++
        }

        val restoredController = DefaultFocusRescueController(
            sessionManagerProvider = { restoredManager },
            repository = repository,
            cooldownDurationMs = testCooldownMs
        )

        // Cooldown persists across process recreation
        assertTrue(restoredController.isInCooldown(now))
        assertEquals(45_000L, restoredController.getRemainingCooldownMillis(now))

        // After cooldown target passes
        assertFalse(restoredController.isInCooldown(cooldownTarget + 1000L))
        assertEquals(0L, restoredController.getRemainingCooldownMillis(cooldownTarget + 1000L))

        // Normal session tracking is NOT blocked during cooldown
        assertTrue(restoredManager.isSessionActive())
    }

    // ── 18. Two-Intervention Eligibility Requirement ─────────────────────────

    @Test
    fun test18_TwoInterventionEligibilityRequirement() {
        sessionManager.startSession("Goal", 25, setOf("com.instagram.android"))
        val baseTime = 100_000L

        // 0 ignored interventions -> Not eligible
        assertEquals(StudyRescueState.SESSION_ACTIVE, sessionManager.stateFlow.value)
        val attempt0 = controller.startFocusRescue(baseTime)
        assertEquals(FocusRescueStartResult.NotEligible, attempt0)

        // 1st distraction detected & dismissed -> 1 ignored intervention
        sessionManager.onDistractionDetected("com.instagram.android", timestamp = baseTime)
        sessionManager.onInterventionDismissed(sessionManager.activeSessionFlow.value!!.sessionId, 1)

        // 1 ignored intervention is INSUFFICIENT -> Still Not eligible
        assertEquals(StudyRescueState.SESSION_ACTIVE, sessionManager.stateFlow.value)
        assertEquals(1, sessionManager.snapshotFlow.value.ignoredInterventionsCount)
        assertFalse(sessionManager.snapshotFlow.value.isFocusRescueEligible)

        val attempt1 = controller.startFocusRescue(baseTime + 1000L)
        assertEquals(FocusRescueStartResult.NotEligible, attempt1)

        // 2nd distraction detected & dismissed -> 2 ignored interventions
        sessionManager.onDistractionDetected("com.instagram.android", timestamp = baseTime + 5000L)
        sessionManager.onInterventionDismissed(sessionManager.activeSessionFlow.value!!.sessionId, 2)

        // 2 ignored interventions -> FOCUS_RESCUE_READY -> Now eligible!
        assertEquals(StudyRescueState.FOCUS_RESCUE_READY, sessionManager.stateFlow.value)
        assertEquals(2, sessionManager.snapshotFlow.value.ignoredInterventionsCount)
        assertTrue(sessionManager.snapshotFlow.value.isFocusRescueEligible)

        val attempt2 = controller.startFocusRescue(baseTime + 6000L)
        assertTrue(attempt2 is FocusRescueStartResult.Success)
        assertEquals(StudyRescueState.FOCUS_RESCUE_ACTIVE, sessionManager.stateFlow.value)
    }

    // ── 19. Direct Start While Not Eligible ──────────────────────────────────

    @Test
    fun test19_DirectStartWhileNotEligible() {
        // Direct start when IDLE (no active session)
        val noSessionResult = controller.startFocusRescue()
        assertEquals(FocusRescueStartResult.NoActiveSession, noSessionResult)

        // Direct start when session is paused
        sessionManager.startSession("Goal", 25, setOf("com.instagram.android"))
        sessionManager.pauseSession()
        assertEquals(StudyRescueState.SESSION_PAUSED, sessionManager.stateFlow.value)

        val pausedResult = controller.startFocusRescue()
        assertEquals(FocusRescueStartResult.SessionPaused, pausedResult)

        // Resume session to SESSION_ACTIVE (0 strikes)
        sessionManager.resumeSession()
        assertEquals(StudyRescueState.SESSION_ACTIVE, sessionManager.stateFlow.value)

        val activeNoStrikesResult = controller.startFocusRescue()
        assertEquals(FocusRescueStartResult.NotEligible, activeNoStrikesResult)
    }

    // ── 20. Simultaneous / Repeated Expiration Handling ──────────────────────

    @Test
    fun test20_SimultaneousAndRepeatedExpirationHandling() = runBlocking {
        prepareEligibleSession(25)
        val startTime = 100_000L
        controller.startFocusRescue(startTime)

        val endTime = startTime + 25 * 60 * 1000L
        val expirationCheckTime = endTime + 10_000L

        // Simulate simultaneous expiration calls (e.g. from ticker, UI, and lifecycle)
        val deferredResults = (1..5).map {
            async(Dispatchers.Default) {
                controller.checkAndHandleExpiration(expirationCheckTime)
            }
        }
        val results = deferredResults.awaitAll()

        // Exactly one call executes the completion transition, the others return false or handle idempotently
        assertTrue(results.any { it })
        assertFalse(controller.isFocusRescueActive)
        assertEquals(StudyRescueState.SESSION_ACTIVE, sessionManager.stateFlow.value)

        // Subsequent check after completion is safe and returns false
        assertFalse(controller.checkAndHandleExpiration(expirationCheckTime + 5000L))

        // Session state and completedAt timestamp remain consistent and uncorrupted
        val session = sessionManager.activeSessionFlow.value
        assertEquals("COMPLETED", session?.focusRescueState)
        assertEquals("SESSION_ACTIVE", session?.currentState)
    }
}
