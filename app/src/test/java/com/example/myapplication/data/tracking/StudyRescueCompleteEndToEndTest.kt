package com.example.myapplication.data.tracking

import com.example.myapplication.data.local.FakeStudyRescueDao
import com.example.myapplication.data.local.StudyRescueRepository
import com.example.myapplication.data.local.StudySessionEntity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Complete End-to-End QA Test Suite for Study Rescue and Focus Rescue.
 * Validates the full 25-step user journey, security invariants, lifecycle recovery, and edge cases.
 */
class StudyRescueCompleteEndToEndTest {

    private lateinit var fakeDao: FakeStudyRescueDao
    private lateinit var repository: StudyRescueRepository
    private lateinit var sessionManager: StudySessionManager
    private lateinit var policy: StudyRescuePolicy
    private lateinit var controller: FocusRescueController
    private lateinit var enforcer: FocusRescueEnforcer

    private val redirectedPackages = mutableListOf<String>()
    private val selectedRestrictedApps = setOf("com.instagram.android", "com.google.android.youtube", "com.twitter.android")

    @Before
    fun setUp() {
        fakeDao = FakeStudyRescueDao()
        repository = StudyRescueRepository(fakeDao)
        sessionManager = StudySessionManager(repository = repository, interventionTimeoutMs = 60_000L)

        policy = DefaultStudyRescuePolicy(
            sessionManagerProvider = { sessionManager },
            configProvider = {
                FocusRescueConfig(
                    restrictedPackages = selectedRestrictedApps,
                    maxConsecutiveRedirects = 5,
                    redirectCooldownMs = 1500L
                )
            }
        )

        controller = sessionManager.focusRescueController

        redirectedPackages.clear()
        enforcer = FocusRescueEnforcer(
            policyProvider = { policy },
            redirectLauncher = { targetPackage ->
                redirectedPackages.add(targetPackage)
                true
            }
        )
    }

    // ── 1. COMPLETE 25-STEP USER JOURNEY FLOW ─────────────────────────────────

    @Test
    fun testComplete25StepFlow_FromSessionStartToRecovery() {
        var currentTime = 100_000L
        val plannedMinutes = 25
        val plannedDurationMillis = plannedMinutes * 60 * 1000L

        // Step 1: User starts a study session
        val started = sessionManager.startSession("Final Exam Study", plannedMinutes, selectedRestrictedApps)
        assertTrue("Step 1: Session start must return true", started)
        assertEquals("Step 1: State must be SESSION_ACTIVE", StudyRescueState.SESSION_ACTIVE, sessionManager.stateFlow.value)
        val activeSession = sessionManager.activeSessionFlow.value
        assertNotNull(activeSession)
        val sessionId = activeSession!!.sessionId

        // Step 2: User selects distracting applications
        assertEquals("Step 2: Selected apps match config", selectedRestrictedApps, activeSession.selectedRestrictedAppPackages.toSet())

        // Step 3 & 4: User opens a selected distracting app & existing tracking detects behavior
        val d1 = sessionManager.onDistractionDetected("com.instagram.android", reason = DistractionReason.RESTRICTED_APP_OPENED, timestamp = currentTime)
        assertTrue("Step 4: Distraction must be detected", d1)

        // Step 5: Intervention one appears
        assertEquals("Step 5: State must be INTERVENTION_ONE_PENDING", StudyRescueState.INTERVENTION_ONE_PENDING, sessionManager.stateFlow.value)
        assertEquals(1, sessionManager.snapshotFlow.value.distractionCount)

        // Step 6: User ignores intervention one (e.g. dismissed / timed out)
        currentTime += 1500L
        val dismissed1 = sessionManager.onInterventionDismissed(sessionId, 1)
        assertTrue("Step 6: Intervention 1 dismissed/ignored", dismissed1)
        assertEquals("Step 6: Ignored count is now 1", 1, sessionManager.snapshotFlow.value.ignoredInterventionsCount)
        assertEquals("Step 6: State returns to SESSION_ACTIVE", StudyRescueState.SESSION_ACTIVE, sessionManager.stateFlow.value)

        // Step 7: User triggers another valid distraction
        currentTime += 2500L // Exceeds debounce window
        val d2 = sessionManager.onDistractionDetected("com.google.android.youtube", reason = DistractionReason.RESTRICTED_APP_OPENED, timestamp = currentTime)
        assertTrue("Step 7: Second distraction detected", d2)

        // Step 8: Intervention two appears
        assertEquals("Step 8: State must be INTERVENTION_TWO_PENDING", StudyRescueState.INTERVENTION_TWO_PENDING, sessionManager.stateFlow.value)

        // Step 9: User ignores intervention two
        currentTime += 1500L
        val dismissed2 = sessionManager.onInterventionDismissed(sessionId, 2)
        assertTrue("Step 9: Intervention 2 dismissed/ignored", dismissed2)
        assertEquals("Step 9: Ignored count is now 2", 2, sessionManager.snapshotFlow.value.ignoredInterventionsCount)

        // Step 10: Focus Rescue becomes eligible
        assertEquals("Step 10: State must be FOCUS_RESCUE_READY", StudyRescueState.FOCUS_RESCUE_READY, sessionManager.stateFlow.value)
        assertTrue("Step 10: Focus Rescue is eligible", sessionManager.snapshotFlow.value.isFocusRescueEligible)

        // Step 11: User starts Focus Rescue
        currentTime += 1000L
        val startResult = controller.startFocusRescue(currentTime)
        assertTrue("Step 11: Focus Rescue start must succeed", startResult is FocusRescueStartResult.Success)
        assertEquals("Step 11: State must be FOCUS_RESCUE_ACTIVE", StudyRescueState.FOCUS_RESCUE_ACTIVE, sessionManager.stateFlow.value)
        assertTrue("Step 11: Controller is active", controller.isFocusRescueActive)

        // Step 12: Focus Rescue screen displays original selected duration
        val timestamps = (startResult as FocusRescueStartResult.Success).timestamps
        assertEquals("Step 12: Timestamps duration must match planned session duration", plannedDurationMillis, timestamps.plannedDurationMillis)

        // Step 13: Circular timer counts down using absolute timestamps
        currentTime += 60_000L // 1 minute passed
        val remaining = timestamps.calculateRemainingMillis(currentTime)
        assertEquals("Step 13: Remaining time must equal plannedDuration - 1 minute", plannedDurationMillis - 60_000L, remaining)
        assertFalse("Step 13: Timestamps must not be expired yet", timestamps.isExpired(currentTime))

        // Step 14 & 15: User opens a restricted app -> AccessibilityService redirects to Focus Rescue
        val enforcementRestricted = enforcer.onWindowEvent("com.instagram.android")
        assertTrue("Step 15: Enforcer must enforce redirect for restricted app", enforcementRestricted is EnforcementResult.Redirected)
        assertTrue("Step 15: Target package must be in redirected list", redirectedPackages.contains("com.instagram.android"))

        // Step 16 & 17: User opens an allowed app -> Remains accessible
        val enforcementAllowed = enforcer.onWindowEvent("com.android.chrome")
        assertTrue("Step 17: Enforcer must allow non-restricted app", enforcementAllowed is EnforcementResult.Allowed)

        // Step 18: User exits Focus Rescue
        currentTime += 30_000L
        val exitResult = controller.exitFocusRescue("MANUAL_EXIT", currentTime)
        assertTrue("Step 18: Manual exit must succeed", exitResult)
        assertFalse("Step 18: Controller is inactive after exit", controller.isFocusRescueActive)
        assertEquals("Step 18: State returns to SESSION_ACTIVE", StudyRescueState.SESSION_ACTIVE, sessionManager.stateFlow.value)

        // Step 19: Restrictions stop
        val enforcementAfterExit = enforcer.onWindowEvent("com.instagram.android")
        assertFalse("Step 19: No redirection occurs after Focus Rescue exit", enforcementAfterExit is EnforcementResult.Redirected)

        // Step 20: User starts Focus Rescue again after cooldown if permitted
        val startDuringCooldown = controller.startFocusRescue(currentTime + 5000L)
        assertTrue("Step 20: Re-entry during cooldown rejected", startDuringCooldown is FocusRescueStartResult.CooldownActive)

        // Fast-forward past cooldown
        val cooldownUntil = sessionManager.activeSessionFlow.value!!.cooldownUntil!!
        currentTime = cooldownUntil + 5000L
        assertFalse("Step 20: In cooldown must be false after duration passes", controller.isInCooldown(currentTime))

        // Make session eligible again
        sessionManager.onDistractionDetected("com.instagram.android", reason = DistractionReason.RESTRICTED_APP_OPENED, timestamp = currentTime)
        sessionManager.onInterventionDismissed(sessionId, 1)
        sessionManager.onDistractionDetected("com.instagram.android", reason = DistractionReason.RESTRICTED_APP_OPENED, timestamp = currentTime + 3000L)
        sessionManager.onInterventionDismissed(sessionId, 2)
        assertEquals(StudyRescueState.FOCUS_RESCUE_READY, sessionManager.stateFlow.value)

        val startAfterCooldown = controller.startFocusRescue(currentTime + 4000L)
        assertTrue("Step 20: Re-entry after cooldown succeeds", startAfterCooldown is FocusRescueStartResult.Success)

        // Step 21: Timer completes automatically
        val ts2 = (startAfterCooldown as FocusRescueStartResult.Success).timestamps
        val autoEndTime = ts2.endTimeMillis
        val completeResult = controller.completeFocusRescue(autoEndTime)
        assertTrue("Step 21: Automatic completion must succeed", completeResult)

        // Step 22: Focus Rescue ends
        assertFalse("Step 22: Controller is inactive", controller.isFocusRescueActive)
        assertEquals(FocusRescueLifecycleState.COMPLETED, controller.lifecycleStateFlow.value)

        // Step 23: Study session remains active unless separately completed
        assertEquals("Step 23: Parent study session remains active", StudyRescueState.SESSION_ACTIVE, sessionManager.stateFlow.value)
        assertTrue("Step 23: Study session is active", sessionManager.isSessionActive())

        // Step 24 & 25: App is restarted / Process death recovery
        val completedSessionEntity = sessionManager.activeSessionFlow.value!!
        val restored = controller.restoreFromSession(completedSessionEntity, currentTimeMillis = autoEndTime + 5000L)
        assertFalse("Step 25: Expired session returns false (completed)", restored)
        assertFalse("Step 25: Controller is not active after expired restore", controller.isFocusRescueActive)
    }

    // ── 2. SECURITY TESTS ─────────────────────────────────────────────────────

    @Test
    fun testSecurity_EmergencyCallingNeverBlocked() {
        // Activate Focus Rescue
        sessionManager.startSession("Quantum Mechanics", 25, selectedRestrictedApps)
        val sId = sessionManager.activeSessionFlow.value!!.sessionId
        sessionManager.onDistractionDetected("com.instagram.android", reason = DistractionReason.RESTRICTED_APP_OPENED, timestamp = 1000L)
        sessionManager.onInterventionDismissed(sId, 1)
        sessionManager.onDistractionDetected("com.instagram.android", reason = DistractionReason.RESTRICTED_APP_OPENED, timestamp = 3000L)
        sessionManager.onInterventionDismissed(sId, 2)
        controller.startFocusRescue(4000L)
        assertTrue(controller.isFocusRescueActive)

        val emergencyPackages = listOf(
            "com.android.phone",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.android.server.telecom",
            "com.android.incallui",
            "com.emergency.sos"
        )

        for (pkg in emergencyPackages) {
            val decision = policy.evaluate(pkg, timestamp = 5000L)
            assertTrue("Emergency package $pkg must be ALLOWED", decision is PolicyDecision.Allow)
            assertEquals(AllowReason.EMERGENCY_OR_PHONE, (decision as PolicyDecision.Allow).reason)
            assertTrue("Enforcer must allow emergency package", enforcer.onWindowEvent(pkg) is EnforcementResult.Allowed)
        }
    }

    @Test
    fun testSecurity_SystemSettingsNeverBlocked() {
        sessionManager.startSession("Math", 25, selectedRestrictedApps)
        val sId = sessionManager.activeSessionFlow.value!!.sessionId
        sessionManager.onDistractionDetected("com.instagram.android", reason = DistractionReason.RESTRICTED_APP_OPENED, timestamp = 1000L)
        sessionManager.onInterventionDismissed(sId, 1)
        sessionManager.onDistractionDetected("com.instagram.android", reason = DistractionReason.RESTRICTED_APP_OPENED, timestamp = 3000L)
        sessionManager.onInterventionDismissed(sId, 2)
        controller.startFocusRescue(4000L)

        val settingsPackages = listOf(
            "com.android.settings",
            "com.google.android.settings",
            "com.android.settings.intelligence"
        )

        for (pkg in settingsPackages) {
            val decision = policy.evaluate(pkg, timestamp = 5000L)
            assertTrue("Settings package $pkg must be ALLOWED", decision is PolicyDecision.Allow)
            assertEquals(AllowReason.SYSTEM_SETTINGS, (decision as PolicyDecision.Allow).reason)
            assertTrue("Enforcer must allow settings package", enforcer.onWindowEvent(pkg) is EnforcementResult.Allowed)
        }
    }

    @Test
    fun testSecurity_SystemUiAndKeyboardsNeverBlocked() {
        sessionManager.startSession("Math", 25, selectedRestrictedApps)
        val sId = sessionManager.activeSessionFlow.value!!.sessionId
        sessionManager.onDistractionDetected("com.instagram.android", reason = DistractionReason.RESTRICTED_APP_OPENED, timestamp = 1000L)
        sessionManager.onInterventionDismissed(sId, 1)
        sessionManager.onDistractionDetected("com.instagram.android", reason = DistractionReason.RESTRICTED_APP_OPENED, timestamp = 3000L)
        sessionManager.onInterventionDismissed(sId, 2)
        controller.startFocusRescue(4000L)

        val systemUiPackages = listOf(
            "com.android.systemui",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard"
        )

        for (pkg in systemUiPackages) {
            val decision = policy.evaluate(pkg, timestamp = 5000L)
            assertTrue("System UI / IME $pkg must be ALLOWED", decision is PolicyDecision.Allow)
            assertTrue("Enforcer must allow system UI package", enforcer.onWindowEvent(pkg) is EnforcementResult.Allowed)
        }
    }

    @Test
    fun testSecurity_WellnessWaveNeverRedirectsItself() {
        sessionManager.startSession("Math", 25, selectedRestrictedApps)
        val sId = sessionManager.activeSessionFlow.value!!.sessionId
        sessionManager.onDistractionDetected("com.instagram.android", reason = DistractionReason.RESTRICTED_APP_OPENED, timestamp = 1000L)
        sessionManager.onInterventionDismissed(sId, 1)
        sessionManager.onDistractionDetected("com.instagram.android", reason = DistractionReason.RESTRICTED_APP_OPENED, timestamp = 3000L)
        sessionManager.onInterventionDismissed(sId, 2)
        controller.startFocusRescue(4000L)

        val decision = policy.evaluate("com.example.myapplication", timestamp = 5000L)
        assertTrue(decision is PolicyDecision.Allow)
        assertEquals(AllowReason.WELLNESS_WAVE_APP, (decision as PolicyDecision.Allow).reason)
        assertTrue(enforcer.onWindowEvent("com.example.myapplication") is EnforcementResult.Allowed)
    }

    @Test
    fun testSecurity_LoopBreakerPreventsInfiniteRedirectLoop() {
        sessionManager.startSession("Math", 25, selectedRestrictedApps)
        val sId = sessionManager.activeSessionFlow.value!!.sessionId
        sessionManager.onDistractionDetected("com.instagram.android", reason = DistractionReason.RESTRICTED_APP_OPENED, timestamp = 1000L)
        sessionManager.onInterventionDismissed(sId, 1)
        sessionManager.onDistractionDetected("com.instagram.android", reason = DistractionReason.RESTRICTED_APP_OPENED, timestamp = 3000L)
        sessionManager.onInterventionDismissed(sId, 2)
        controller.startFocusRescue(4000L)

        var time = 5000L
        for (i in 1..5) {
            val decision = policy.evaluate("com.instagram.android", timestamp = time)
            assertTrue("Redirect #$i must succeed", decision is PolicyDecision.Redirect)
            policy.recordRedirectSuccess("com.instagram.android", timestamp = time)
            time += 2000L
        }

        // 6th consecutive attempt must be suppressed by the policy loop breaker
        val loopDecision = policy.evaluate("com.instagram.android", timestamp = time)
        assertTrue("Loop breaker must trip and ignore", loopDecision is PolicyDecision.Ignore)
        assertEquals(IgnoreReason.REDIRECT_LOOP_PREVENTED, (loopDecision as PolicyDecision.Ignore).reason)
    }

    // ── 3. LIFECYCLE RECOVERY TESTS ───────────────────────────────────────────

    @Test
    fun testLifecycle_ProcessDeathRestoresActiveFocusRescueWithExactRemainingTime() {
        val now = 200_000L
        val session = StudySessionEntity(
            sessionId = "s1",
            taskTitle = "Deep Focus",
            plannedDurationMillis = 15 * 60 * 1000L,
            startTimeMillis = now - 5 * 60 * 1000L,
            currentState = "FOCUS_RESCUE_ACTIVE",
            focusRescueStartTime = now - 2 * 60 * 1000L, // Started 2 mins ago
            focusRescueEndTime = now + 13 * 60 * 1000L,   // 13 mins remaining
            focusRescueState = "ACTIVE",
            createdAt = now - 5 * 60 * 1000L
        )

        val config = StudySessionConfig(
            sessionId = session.sessionId,
            taskTitle = session.taskTitle,
            plannedDurationMinutes = 15,
            startTimestamp = session.startTimeMillis,
            selectedDistractingApps = session.selectedRestrictedAppPackages.toSet()
        )
        sessionManager.stateMachine.restoreSession(config, StudyRescueState.FOCUS_RESCUE_ACTIVE)
        sessionManager.updateActiveSessionDirectly(session)

        val restored = controller.restoreFromSession(session, currentTimeMillis = now)
        assertTrue(restored)
        assertTrue(controller.isFocusRescueActive)
        assertEquals(13 * 60 * 1000L, controller.getRemainingMillis(now))
    }

    @Test
    fun testLifecycle_ProcessDeathWithPastTimestampAutoCompletesAndClearsRestrictions() {
        val now = 500_000L
        val session = StudySessionEntity(
            sessionId = "s1",
            taskTitle = "Deep Focus",
            plannedDurationMillis = 10 * 60 * 1000L,
            startTimeMillis = 100_000L,
            currentState = "FOCUS_RESCUE_ACTIVE",
            focusRescueStartTime = 100_000L,
            focusRescueEndTime = 200_000L, // Expired 300 seconds ago
            focusRescueState = "ACTIVE",
            createdAt = 100_000L
        )

        val config = StudySessionConfig(
            sessionId = session.sessionId,
            taskTitle = session.taskTitle,
            plannedDurationMinutes = 10,
            startTimestamp = session.startTimeMillis,
            selectedDistractingApps = session.selectedRestrictedAppPackages.toSet()
        )
        sessionManager.stateMachine.restoreSession(config, StudyRescueState.FOCUS_RESCUE_ACTIVE)
        sessionManager.updateActiveSessionDirectly(session)

        val restored = controller.restoreFromSession(session, currentTimeMillis = now)
        assertFalse("Expired session does not stay active", restored)
        assertFalse(controller.isFocusRescueActive)
        assertEquals(FocusRescueLifecycleState.COMPLETED, controller.lifecycleStateFlow.value)
    }

    @Test
    fun testLifecycle_IdempotentStateTransitionsOnRecreation() {
        sessionManager.startSession("Math", 25, selectedRestrictedApps)
        val sId = sessionManager.activeSessionFlow.value!!.sessionId
        sessionManager.onDistractionDetected("com.instagram.android", reason = DistractionReason.RESTRICTED_APP_OPENED, timestamp = 1000L)
        sessionManager.onInterventionDismissed(sId, 1)
        sessionManager.onDistractionDetected("com.instagram.android", reason = DistractionReason.RESTRICTED_APP_OPENED, timestamp = 3000L)
        sessionManager.onInterventionDismissed(sId, 2)
        controller.startFocusRescue(4000L)

        // Multiple calls to completeFocusRescue must be idempotent
        val first = controller.completeFocusRescue(5000L)
        val second = controller.completeFocusRescue(5000L)
        assertTrue("First completion succeeds", first)
        assertTrue("Second completion safely succeeds idempotently", second)
        assertFalse(controller.isFocusRescueActive)
    }

    // ── 4. EDGE CASES ─────────────────────────────────────────────────────────

    @Test
    fun testEdgeCase_ZeroAndNegativeDurationHandledSafely() {
        val zeroTimestamps = FocusRescueTimestamps(startTimeMillis = 1000L, plannedDurationMillis = 0L, endTimeMillis = 1000L)
        assertTrue(zeroTimestamps.isExpired(1000L))
        assertEquals(0L, zeroTimestamps.calculateRemainingMillis(1000L))

        val negativeRemaining = zeroTimestamps.calculateRemainingMillis(2000L)
        assertEquals("Remaining millis must never be negative", 0L, negativeRemaining)
    }

    @Test
    fun testEdgeCase_ShortAndLongDurations() {
        // Very short: 1 second
        val shortTs = FocusRescueTimestamps(1000L, 1000L, 2000L)
        assertEquals(1000L, shortTs.calculateRemainingMillis(1000L))
        assertEquals(0L, shortTs.calculateRemainingMillis(2000L))

        // Very long: 12 hours
        val longDuration = 12 * 60 * 60 * 1000L
        val longTs = FocusRescueTimestamps(1000L, longDuration, 1000L + longDuration)
        assertEquals(longDuration, longTs.calculateRemainingMillis(1000L))
        assertFalse(longTs.isExpired(1000L + longDuration - 1))
    }

    @Test
    fun testEdgeCase_RapidAppSwitchingDebouncing() {
        val now = 100_000L
        sessionManager.startSession("Task", 25, selectedRestrictedApps)

        val first = sessionManager.onDistractionDetected("com.instagram.android", reason = DistractionReason.RESTRICTED_APP_OPENED, timestamp = now)
        assertTrue(first)
        assertEquals(1, sessionManager.snapshotFlow.value.distractionCount)

        // Rapid switch within debounce window (default 400ms in state machine)
        sessionManager.onDistractionDetected("com.instagram.android", reason = DistractionReason.RESTRICTED_APP_OPENED, timestamp = now + 100L)
        // Count must remain 1 because it was debounced
        assertEquals("Distraction count must remain 1 due to debouncing", 1, sessionManager.snapshotFlow.value.distractionCount)
    }

    @Test
    fun testEdgeCase_ConcurrentSessionStartRejected() {
        val s1 = sessionManager.startSession("Session 1", 25, selectedRestrictedApps)
        assertTrue(s1)

        val s2 = sessionManager.startSession("Session 2", 25, selectedRestrictedApps)
        assertFalse("Cannot start concurrent session while another is active", s2)
    }

    @Test
    fun testEdgeCase_DeviceClockChangesHandledMonotonically() {
        val start = 100_000L
        val end = 200_000L
        val timestamps = FocusRescueTimestamps(start, 100_000L, end)

        // Clock moves forward past end
        val forwardClock = 300_000L
        assertTrue(timestamps.isExpired(forwardClock))
        assertEquals(0L, timestamps.calculateRemainingMillis(forwardClock))

        // Clock moved backward before start
        val backwardClock = 50_000L
        assertEquals(150_000L, timestamps.calculateRemainingMillis(backwardClock))
    }
}
