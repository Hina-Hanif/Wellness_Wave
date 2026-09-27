package com.example.myapplication.data.tracking

import com.example.myapplication.data.local.FakeStudyRescueDao
import com.example.myapplication.data.local.StudyRescueRepository
import com.example.myapplication.data.local.StudySessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying Focus Rescue completion, manual exit, cooldown,
 * and lifecycle recovery under all edge cases:
 * - Automatic timer completion
 * - Manual exit
 * - Session cancellation
 * - Process death recovery
 * - Expired Focus Rescue while closed
 * - Cooldown persistence and expiration
 * - Repeated accessibility events & loop breaking
 * - Service restart
 * - Escape hatch (Settings & Emergency access)
 */
class FocusRescueCompletionAndRecoveryTest {

    private lateinit var fakeDao: FakeStudyRescueDao
    private lateinit var repository: StudyRescueRepository
    private lateinit var sessionManager: StudySessionManager
    private lateinit var controller: FocusRescueController
    private lateinit var policy: StudyRescuePolicy
    private val redirectedPackages = mutableListOf<String>()
    private lateinit var enforcer: FocusRescueEnforcer

    private val testCooldownMs = DefaultFocusRescueController.DEFAULT_COOLDOWN_MS

    @Before
    fun setUp() {
        fakeDao = FakeStudyRescueDao()
        repository = StudyRescueRepository(fakeDao)
        sessionManager = StudySessionManager(repository)
        controller = sessionManager.focusRescueController
        policy = DefaultStudyRescuePolicy(
            sessionManagerProvider = { sessionManager },
            configProvider = {
                FocusRescueConfig(
                    restrictedPackages = setOf("com.instagram.android"),
                    maxConsecutiveRedirects = 5,
                    redirectCooldownMs = 1500L
                )
            }
        )
        redirectedPackages.clear()
        enforcer = FocusRescueEnforcer(
            policyProvider = { policy },
            redirectLauncher = { targetPackage ->
                redirectedPackages.add(targetPackage)
                true
            }
        )
    }

    private fun prepareAndStartFocusRescue(
        durationMinutes: Int = 25,
        startTime: Long = 100_000L
    ) {
        sessionManager.startSession("Calculus Prep", durationMinutes, setOf("com.instagram.android"))
        val baseTime = 50_000L

        // Trigger two distinct strikes
        sessionManager.onDistractionDetected("com.instagram.android", timestamp = baseTime)
        sessionManager.onInterventionDismissed(sessionManager.activeSessionFlow.value!!.sessionId, 1)

        sessionManager.onDistractionDetected("com.instagram.android", timestamp = baseTime + 5000L)
        sessionManager.onInterventionDismissed(sessionManager.activeSessionFlow.value!!.sessionId, 2)

        assertEquals(StudyRescueState.FOCUS_RESCUE_READY, sessionManager.stateFlow.value)
        val result = controller.startFocusRescue(startTime)
        assertTrue(result is FocusRescueStartResult.Success)
        assertEquals(StudyRescueState.FOCUS_RESCUE_ACTIVE, sessionManager.stateFlow.value)
        assertTrue(policy.isFocusRescueActive())
    }

    // ── 1. Timer Automatic Completion ────────────────────────────────────────

    @Test
    fun testTimerAutomaticCompletionRemovesRestrictionsAndKeepsStudySessionActive() {
        val startTime = 100_000L
        val durationMinutes = 25
        val plannedMillis = durationMinutes * 60 * 1000L
        prepareAndStartFocusRescue(durationMinutes, startTime)

        val endTime = startTime + plannedMillis

        // Before expiry: restricted app redirected
        val beforeResult = enforcer.onWindowEvent("com.instagram.android", timestamp = endTime - 1000L)
        assertTrue(beforeResult is EnforcementResult.Redirected)
        assertEquals(1, redirectedPackages.size)

        // At expiration: automatic completion triggered
        val completed = controller.checkAndHandleExpiration(endTime + 100L)
        assertTrue(completed)

        // Rule 5: Study session remains active! Focus Rescue completion does NOT finish the entire study session
        assertEquals(StudyRescueState.SESSION_ACTIVE, sessionManager.stateFlow.value)
        assertEquals(FocusRescueLifecycleState.COMPLETED, controller.lifecycleState)
        assertFalse(controller.isFocusRescueActive)
        assertFalse(policy.isFocusRescueActive())

        // Restrictions removed: restricted app is no longer redirected
        val afterResult = enforcer.onWindowEvent("com.instagram.android", timestamp = endTime + 500L)
        assertTrue(afterResult is EnforcementResult.Ignored)
        assertEquals(IgnoreReason.FOCUS_RESCUE_INACTIVE, (afterResult as EnforcementResult.Ignored).reason)
        assertEquals(1, redirectedPackages.size) // No new redirect
    }

    // ── 2. Manual Exit ───────────────────────────────────────────────────────

    @Test
    fun testManualExitRemovesRestrictionsAndAppliesCooldown() {
        val startTime = 100_000L
        prepareAndStartFocusRescue(25, startTime)

        val exitTime = startTime + 10 * 60 * 1000L // User exits 10 min in
        val exitSuccess = controller.exitFocusRescue("USER_EXITED", exitTime)
        assertTrue(exitSuccess)

        // Transitions to SESSION_ACTIVE with EXITED state
        assertEquals(StudyRescueState.SESSION_ACTIVE, sessionManager.stateFlow.value)
        assertEquals(FocusRescueLifecycleState.EXITED, controller.lifecycleState)
        assertFalse(policy.isFocusRescueActive())

        // Cooldown active
        assertTrue(controller.isInCooldown(exitTime + 1000L))
        assertEquals(testCooldownMs - 1000L, controller.getRemainingCooldownMillis(exitTime + 1000L))

        // Restrictions immediately lifted
        val result = enforcer.onWindowEvent("com.instagram.android", timestamp = exitTime + 2000L)
        assertTrue(result is EnforcementResult.Ignored)
        assertEquals(IgnoreReason.FOCUS_RESCUE_INACTIVE, (result as EnforcementResult.Ignored).reason)
    }

    // ── 3. Session Cancellation ──────────────────────────────────────────────

    @Test
    fun testSessionCancellationRemovesRestrictionsAndClearsController() {
        val startTime = 100_000L
        prepareAndStartFocusRescue(25, startTime)

        // Session cancelled by user
        val cancelSuccess = sessionManager.cancelSession()
        assertTrue(cancelSuccess)

        assertEquals(StudyRescueState.SESSION_CANCELLED, sessionManager.stateFlow.value)
        assertFalse(controller.isFocusRescueActive)
        assertFalse(policy.isFocusRescueActive())

        // Controller state is cleared
        assertNull(controller.currentTimestamps)
        assertEquals(FocusRescueLifecycleState.INACTIVE, controller.lifecycleState)

        // Opening restricted app is completely unrestricted
        val result = enforcer.onWindowEvent("com.instagram.android")
        assertTrue(result is EnforcementResult.Ignored)
        assertEquals(0, redirectedPackages.size)
    }

    // ── 4. Process Death Recovery ────────────────────────────────────────────

    @Test
    fun testProcessDeathRecoveryRestoresActiveSessionAndRestrictions() {
        val now = System.currentTimeMillis()
        val plannedMillis = 30 * 60 * 1000L
        val startTime = now - 5 * 60 * 1000L // Started 5 mins ago
        val endTime = startTime + plannedMillis

        val entity = StudySessionEntity(
            sessionId = "session-recovered-live",
            taskTitle = "Exam Review",
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

        // Simulate app recreate / restart
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
        val restoredPolicy = DefaultStudyRescuePolicy(
            sessionManagerProvider = { restoredManager }
        )
        val restoredEnforcer = FocusRescueEnforcer(
            policyProvider = { restoredPolicy },
            redirectLauncher = { pkg ->
                redirectedPackages.add(pkg)
                true
            }
        )

        // Restore active focus rescue
        val restored = restoredController.restoreFromSession(entity, now)
        assertTrue(restored)
        assertTrue(restoredController.isFocusRescueActive)

        // Focus rescue timestamps are accurately recovered
        val remaining = restoredController.getRemainingMillis(now)
        assertEquals(25 * 60 * 1000L, remaining)

        // Restrictions are active
        val enforcerResult = restoredEnforcer.onWindowEvent("com.instagram.android", timestamp = now)
        assertTrue(enforcerResult is EnforcementResult.Redirected)
        assertEquals(1, redirectedPackages.size)
    }

    // ── 5. Expired Focus Rescue While Closed ──────────────────────────────────

    @Test
    fun testExpiredFocusRescueDuringProcessDeathAutoCompletes() {
        val now = System.currentTimeMillis()
        val studyPlannedMillis = 60 * 60 * 1000L
        val startTime = now - 40 * 60 * 1000L // Started 40 mins ago
        val focusRescueEndTime = startTime + 15 * 60 * 1000L // Focus Rescue expired 25 mins ago

        val entity = StudySessionEntity(
            sessionId = "session-recovered-expired",
            taskTitle = "Literature",
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
        val restoredPolicy = DefaultStudyRescuePolicy(
            sessionManagerProvider = { restoredManager }
        )
        val restoredEnforcer = FocusRescueEnforcer(
            policyProvider = { restoredPolicy },
            redirectLauncher = { pkg ->
                redirectedPackages.add(pkg)
                true
            }
        )

        // Restoring expired focus rescue immediately completes it
        val restored = restoredController.restoreFromSession(entity, now)
        assertFalse(restored)
        assertFalse(restoredController.isFocusRescueActive)
        assertEquals(FocusRescueLifecycleState.COMPLETED, restoredController.lifecycleState)

        // Restrictions are NOT enforced
        val enforcerResult = restoredEnforcer.onWindowEvent("com.instagram.android", timestamp = now)
        assertTrue(enforcerResult is EnforcementResult.Ignored)
        assertEquals(0, redirectedPackages.size)
    }

    // ── 6. Cooldown Persistence & Expiration ─────────────────────────────────

    @Test
    fun testCooldownPreventsImmediateRepeatedEscalationAndExpires() {
        val startTime = 100_000L
        prepareAndStartFocusRescue(25, startTime)

        val exitTime = startTime + 5000L
        controller.exitFocusRescue("USER_EXITED", exitTime)

        // Immediate attempt to re-escalate during cooldown
        val attempt1 = controller.startFocusRescue(exitTime + 10_000L)
        assertTrue(attempt1 is FocusRescueStartResult.CooldownActive)
        assertEquals(testCooldownMs - 10_000L, (attempt1 as FocusRescueStartResult.CooldownActive).remainingCooldownMillis)

        // Normal study tracking continues during cooldown
        assertTrue(sessionManager.isSessionActive())

        // After cooldown expires
        val postCooldownTime = exitTime + testCooldownMs + 1000L
        assertFalse(controller.isInCooldown(postCooldownTime))
        assertEquals(0L, controller.getRemainingCooldownMillis(postCooldownTime))
    }

    // ── 7. Repeated Accessibility Events & Loop Breaking ─────────────────────

    @Test
    fun testRepeatedAccessibilityEventsDebouncedAndLoopBroken() {
        val startTime = 100_000L
        prepareAndStartFocusRescue(25, startTime)

        val t0 = 200_000L
        // 1st event triggers redirect
        val r1 = enforcer.onWindowEvent("com.instagram.android", timestamp = t0)
        assertTrue(r1 is EnforcementResult.Redirected)
        assertEquals(1, redirectedPackages.size)

        // Duplicate event 100ms later (rapid window state spam) is debounced
        val r2 = enforcer.onWindowEvent("com.instagram.android", timestamp = t0 + 100L)
        assertTrue(r2 is EnforcementResult.Ignored)
        assertEquals(IgnoreReason.COOLDOWN_ACTIVE, (r2 as EnforcementResult.Ignored).reason)
        assertEquals(1, redirectedPackages.size)

        // After max redirects (5), loop breaker halts redirection
        var time = t0 + 2000L
        for (i in 2..5) {
            val r = enforcer.onWindowEvent("com.instagram.android", timestamp = time)
            assertTrue(r is EnforcementResult.Redirected)
            time += 2000L
        }
        assertEquals(5, redirectedPackages.size)

        // 6th event triggers loop breaker
        val r6 = enforcer.onWindowEvent("com.instagram.android", timestamp = time)
        assertTrue(r6 is EnforcementResult.Ignored)
        assertEquals(IgnoreReason.REDIRECT_LOOP_PREVENTED, (r6 as EnforcementResult.Ignored).reason)
        assertEquals(5, redirectedPackages.size)
    }

    // ── 8. Service Restart Safe Rehydration ───────────────────────────────────

    @Test
    fun testServiceRestartSafeRehydration() {
        prepareAndStartFocusRescue(25, 100_000L)

        // Simulate AccessibilityService restart by instantiating a fresh enforcer instance
        val freshEnforcer = FocusRescueEnforcer(
            policyProvider = { policy },
            redirectLauncher = { pkg ->
                redirectedPackages.add(pkg)
                true
            }
        )

        // Correctly recognizes active Focus Rescue state
        val result = freshEnforcer.onWindowEvent("com.instagram.android", timestamp = 150_000L)
        assertTrue(result is EnforcementResult.Redirected)
        assertEquals(1, redirectedPackages.size)
    }

    // ── 9. Escape Hatch (Settings & Emergency) ────────────────────────────────

    @Test
    fun testPermissionRemovalEscapeHatch() {
        prepareAndStartFocusRescue(25, 100_000L)

        // User opens Android Settings to manage / disable accessibility permissions
        val settingsResult = enforcer.onWindowEvent("com.android.settings")
        assertTrue(settingsResult is EnforcementResult.Allowed)
        assertEquals(AllowReason.SYSTEM_SETTINGS, (settingsResult as EnforcementResult.Allowed).reason)

        val accessibilitySettingsResult = enforcer.onWindowEvent("com.android.settings.accessibility")
        assertTrue(accessibilitySettingsResult is EnforcementResult.Allowed)
        assertEquals(AllowReason.SYSTEM_SETTINGS, (accessibilitySettingsResult as EnforcementResult.Allowed).reason)

        // Emergency calling is always allowed
        val dialerResult = enforcer.onWindowEvent("com.android.phone")
        assertTrue(dialerResult is EnforcementResult.Allowed)
        assertEquals(AllowReason.EMERGENCY_OR_PHONE, (dialerResult as EnforcementResult.Allowed).reason)

        // Zero redirects triggered for escape hatches
        assertEquals(0, redirectedPackages.size)
    }
}
