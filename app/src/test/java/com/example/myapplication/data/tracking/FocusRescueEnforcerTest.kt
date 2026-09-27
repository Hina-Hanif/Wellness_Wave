package com.example.myapplication.data.tracking

import com.example.myapplication.data.local.FakeStudyRescueDao
import com.example.myapplication.data.local.StudyRescueRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying FocusRescueEnforcer integration with StudyRescuePolicy:
 * - Redirection behavior when Focus Rescue is active vs inactive
 * - Absolute immunity of emergency, phone, settings, keyboards, launchers, and Wellness Wave
 * - Allowed vs restricted app handling
 * - Cooldown and duplicate event suppression
 * - Redirect loop breaker safeguards
 * - Null/blank package safety
 * - Launcher failure error handling
 */
class FocusRescueEnforcerTest {

    private lateinit var fakeDao: FakeStudyRescueDao
    private lateinit var repository: StudyRescueRepository
    private lateinit var sessionManager: StudySessionManager
    private lateinit var policy: StudyRescuePolicy
    private val redirectedPackages = mutableListOf<String>()
    private var launcherShouldSucceed = true

    private lateinit var enforcer: FocusRescueEnforcer

    @Before
    fun setUp() {
        fakeDao = FakeStudyRescueDao()
        repository = StudyRescueRepository(fakeDao)
        sessionManager = StudySessionManager(repository)
        redirectedPackages.clear()
        launcherShouldSucceed = true

        policy = DefaultStudyRescuePolicy(
            sessionManagerProvider = { sessionManager },
            configProvider = {
                FocusRescueConfig(
                    restrictedPackages = setOf("com.instagram.android", "com.zhiliaoapp.musically"),
                    maxConsecutiveRedirects = 5,
                    redirectCooldownMs = 1500L
                )
            }
        )

        enforcer = FocusRescueEnforcer(
            policyProvider = { policy },
            redirectLauncher = { targetPackage ->
                if (launcherShouldSucceed) {
                    redirectedPackages.add(targetPackage)
                    true
                } else {
                    false
                }
            }
        )
    }

    private fun activateFocusRescueSession(
        durationMinutes: Int = 25,
        restrictedApps: Set<String> = setOf("com.instagram.android", "com.zhiliaoapp.musically")
    ) {
        sessionManager.startSession("Study Math", durationMinutes, restrictedApps)
        val baseTime = 100_000L

        // Trigger two ignored interventions to make Focus Rescue ready
        sessionManager.onDistractionDetected(restrictedApps.first(), timestamp = baseTime)
        sessionManager.onInterventionDismissed(sessionManager.activeSessionFlow.value!!.sessionId, 1)

        sessionManager.onDistractionDetected(restrictedApps.first(), timestamp = baseTime + 5000L)
        sessionManager.onInterventionDismissed(sessionManager.activeSessionFlow.value!!.sessionId, 2)

        assertEquals(StudyRescueState.FOCUS_RESCUE_READY, sessionManager.stateFlow.value)
        val started = sessionManager.startFocusRescue()
        assertTrue(started)
        assertEquals(StudyRescueState.FOCUS_RESCUE_ACTIVE, sessionManager.stateFlow.value)
    }

    // ── 1. Focus Rescue Inactive ─────────────────────────────────────────────

    @Test
    fun testFocusRescueInactiveDoesNotRedirect() {
        // No session or normal session (not FOCUS_RESCUE_ACTIVE)
        sessionManager.startSession("Normal Study", 25, setOf("com.instagram.android"))
        assertEquals(StudyRescueState.SESSION_ACTIVE, sessionManager.stateFlow.value)

        val result = enforcer.onWindowEvent("com.instagram.android")
        assertTrue(result is EnforcementResult.Ignored)
        assertEquals(IgnoreReason.FOCUS_RESCUE_INACTIVE, (result as EnforcementResult.Ignored).reason)
        assertEquals(0, redirectedPackages.size)
    }

    // ── 2. Restricted App Redirects When Active ──────────────────────────────

    @Test
    fun testRestrictedAppRedirectsWhenFocusRescueActive() {
        activateFocusRescueSession()

        val timestamp = 200_000L
        val result = enforcer.onWindowEvent("com.instagram.android", timestamp = timestamp)

        assertTrue(result is EnforcementResult.Redirected)
        val redirected = result as EnforcementResult.Redirected
        assertEquals("com.instagram.android", redirected.targetPackage)
        assertEquals(1, redirected.redirectCount)

        assertEquals(1, redirectedPackages.size)
        assertEquals("com.instagram.android", redirectedPackages.first())
    }

    // ── 3. Emergency & Phone Dialer Immunity ─────────────────────────────────

    @Test
    fun testEmergencyAndPhoneDialerNeverRedirect() {
        activateFocusRescueSession()

        val emergencyPackages = listOf(
            "com.android.phone",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.android.server.telecom",
            "com.android.emergency"
        )

        for (pkg in emergencyPackages) {
            val result = enforcer.onWindowEvent(pkg)
            assertTrue("Expected Allowed for $pkg", result is EnforcementResult.Allowed)
            assertEquals(AllowReason.EMERGENCY_OR_PHONE, (result as EnforcementResult.Allowed).reason)
        }
        assertEquals(0, redirectedPackages.size)
    }

    // ── 4. System Settings Immunity ──────────────────────────────────────────

    @Test
    fun testSystemSettingsNeverRedirect() {
        activateFocusRescueSession()

        val settingsPackages = listOf(
            "com.android.settings",
            "com.google.android.settings",
            "com.android.settings.accessibility"
        )

        for (pkg in settingsPackages) {
            val result = enforcer.onWindowEvent(pkg)
            assertTrue("Expected Allowed for $pkg", result is EnforcementResult.Allowed)
            assertEquals(AllowReason.SYSTEM_SETTINGS, (result as EnforcementResult.Allowed).reason)
        }
        assertEquals(0, redirectedPackages.size)
    }

    // ── 5. Wellness Wave App Immunity ────────────────────────────────────────

    @Test
    fun testWellnessWaveAppNeverRedirect() {
        activateFocusRescueSession()

        val result = enforcer.onWindowEvent("com.example.myapplication")
        assertTrue(result is EnforcementResult.Allowed)
        assertEquals(AllowReason.WELLNESS_WAVE_APP, (result as EnforcementResult.Allowed).reason)
        assertEquals(0, redirectedPackages.size)
    }

    // ── 6. Launchers and Keyboards Immunity ───────────────────────────────────

    @Test
    fun testLaunchersAndKeyboardsNeverRedirect() {
        activateFocusRescueSession()

        val protectedSystemPackages = listOf(
            "com.google.android.apps.nexuslauncher",
            "com.android.systemui",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard"
        )

        for (pkg in protectedSystemPackages) {
            val result = enforcer.onWindowEvent(pkg)
            assertTrue("Expected Allowed for $pkg", result is EnforcementResult.Allowed)
        }
        assertEquals(0, redirectedPackages.size)
    }

    // ── 7. Unrestricted Apps Allowed ─────────────────────────────────────────

    @Test
    fun testUnrestrictedAppNeverRedirect() {
        activateFocusRescueSession()

        val unrestrictedPackages = listOf(
            "com.android.calculator2",
            "com.example.notes",
            "org.wikipedia"
        )

        for (pkg in unrestrictedPackages) {
            val result = enforcer.onWindowEvent(pkg)
            assertTrue("Expected Allowed for $pkg", result is EnforcementResult.Allowed)
        }
        assertEquals(0, redirectedPackages.size)
    }

    // ── 8. Cooldown Duplicate Event Suppression ──────────────────────────────

    @Test
    fun testDuplicateEventSuppressedByCooldown() {
        activateFocusRescueSession()

        val t0 = 200_000L
        // First event triggers redirect
        val result1 = enforcer.onWindowEvent("com.instagram.android", timestamp = t0)
        assertTrue(result1 is EnforcementResult.Redirected)
        assertEquals(1, redirectedPackages.size)

        // Second event 100ms later (e.g. window surface change from same app opening)
        val result2 = enforcer.onWindowEvent("com.instagram.android", timestamp = t0 + 100L)
        assertTrue(result2 is EnforcementResult.Ignored)
        assertEquals(IgnoreReason.COOLDOWN_ACTIVE, (result2 as EnforcementResult.Ignored).reason)

        // No second intent launched
        assertEquals(1, redirectedPackages.size)

        // After cooldown expires (1500ms default)
        val result3 = enforcer.onWindowEvent("com.instagram.android", timestamp = t0 + 1600L)
        assertTrue(result3 is EnforcementResult.Redirected)
        assertEquals(2, redirectedPackages.size)
    }

    // ── 9. Loop Breaker Safeguard ────────────────────────────────────────────

    @Test
    fun testRedirectLoopBreakerHaltAfterMaxConsecutive() {
        activateFocusRescueSession()

        var time = 300_000L
        // Trigger 5 consecutive redirects (separated by > 1500ms cooldown)
        for (i in 1..5) {
            val res = enforcer.onWindowEvent("com.instagram.android", timestamp = time)
            assertTrue("Attempt $i should be Redirected", res is EnforcementResult.Redirected)
            time += 2000L
        }
        assertEquals(5, redirectedPackages.size)

        // 6th consecutive attempt exceeds maxConsecutiveRedirects (5)
        val loopResult = enforcer.onWindowEvent("com.instagram.android", timestamp = time)
        assertTrue(loopResult is EnforcementResult.Ignored)
        assertEquals(IgnoreReason.REDIRECT_LOOP_PREVENTED, (loopResult as EnforcementResult.Ignored).reason)

        // Does not launch a 6th redirect (loop halted safely)
        assertEquals(5, redirectedPackages.size)
    }

    // ── 10. Loop Counter Reset on Returning to Wellness Wave ─────────────────

    @Test
    fun testLoopCounterResetWhenReturningToWellnessWave() {
        activateFocusRescueSession()

        var time = 300_000L
        for (i in 1..3) {
            enforcer.onWindowEvent("com.instagram.android", timestamp = time)
            time += 2000L
        }
        assertEquals(3, redirectedPackages.size)

        // User arrives on Focus Rescue screen (Wellness Wave)
        enforcer.onWindowEvent("com.example.myapplication", timestamp = time)

        // Subsequent restricted app opening starts with fresh redirect count
        time += 2000L
        val res = enforcer.onWindowEvent("com.instagram.android", timestamp = time)
        assertTrue(res is EnforcementResult.Redirected)
        assertEquals(1, (res as EnforcementResult.Redirected).redirectCount)
    }

    // ── 11. Null or Blank Package Handling ───────────────────────────────────

    @Test
    fun testNullOrBlankPackageIgnored() {
        activateFocusRescueSession()

        val nullResult = enforcer.onWindowEvent(null)
        assertTrue(nullResult is EnforcementResult.Ignored)
        assertEquals(IgnoreReason.NULL_OR_BLANK_PACKAGE, (nullResult as EnforcementResult.Ignored).reason)

        val blankResult = enforcer.onWindowEvent("   ")
        assertTrue(blankResult is EnforcementResult.Ignored)
        assertEquals(IgnoreReason.NULL_OR_BLANK_PACKAGE, (blankResult as EnforcementResult.Ignored).reason)

        assertEquals(0, redirectedPackages.size)
    }

    // ── 12. Activity Launch Failure Handled Gracefully ───────────────────────

    @Test
    fun testActivityLaunchFailureHandledGracefully() {
        activateFocusRescueSession()
        launcherShouldSucceed = false // Simulate ActivityNotFoundException or launch failure

        val result = enforcer.onWindowEvent("com.instagram.android", timestamp = 400_000L)
        assertTrue(result is EnforcementResult.LaunchFailed)
        val failure = result as EnforcementResult.LaunchFailed
        assertEquals("com.instagram.android", failure.targetPackage)

        // Service does not crash, zero intents in recorded list
        assertEquals(0, redirectedPackages.size)
    }
}
