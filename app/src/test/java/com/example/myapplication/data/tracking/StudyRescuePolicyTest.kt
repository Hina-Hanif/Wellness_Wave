package com.example.myapplication.data.tracking

import com.example.myapplication.data.local.FakeStudyRescueDao
import com.example.myapplication.data.local.StudyRescueRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StudyRescuePolicyTest {

    private lateinit var fakeDao: FakeStudyRescueDao
    private lateinit var repository: StudyRescueRepository
    private lateinit var fakeNotifier: FakeStudyRescueNotifier
    private lateinit var sessionManager: StudySessionManager
    private lateinit var policy: StudyRescuePolicy

    private val userRestrictedApps = setOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically",
        "com.google.android.youtube",
        "com.reddit.frontpage",
        "com.slack" // Explicitly restricted by user despite being categorized as PRODUCTIVE
    )

    @Before
    fun setUp() {
        fakeDao = FakeStudyRescueDao()
        repository = StudyRescueRepository(fakeDao)
        fakeNotifier = FakeStudyRescueNotifier()
        sessionManager = StudySessionManager(
            repository = repository,
            notifier = fakeNotifier,
            interventionTimeoutMs = 60_000L
        )
        policy = DefaultStudyRescuePolicy(
            sessionManagerProvider = { sessionManager },
            configProvider = { FocusRescueConfig(maxConsecutiveRedirects = 5, redirectCooldownMs = 1500L) }
        )
    }

    private fun setupActiveFocusRescue() {
        sessionManager.startSession("Quantum Physics", 45, userRestrictedApps)
        val sessionId = sessionManager.activeSessionFlow.value!!.sessionId

        // Simulate 2 ignored interventions
        sessionManager.onDistractionDetected("com.instagram.android", timestamp = 1000L)
        sessionManager.onInterventionDismissed(sessionId, 1)

        sessionManager.onDistractionDetected("com.instagram.android", timestamp = 2000L)
        sessionManager.onInterventionDismissed(sessionId, 2)

        assertEquals(StudyRescueState.FOCUS_RESCUE_READY, sessionManager.stateFlow.value)
        val started = sessionManager.startFocusRescue()
        assertTrue("Focus Rescue must start when READY", started)
        assertEquals(StudyRescueState.FOCUS_RESCUE_ACTIVE, sessionManager.stateFlow.value)
    }

    // ── 1. Explicit Restricted App vs PRODUCTIVE Classification ──────────────
    @Test
    fun testExplicitRestrictedAppOverridesProductiveClassification() {
        setupActiveFocusRescue()

        // com.slack is classified as PRODUCTIVE by AppCategoryClassifier
        assertEquals(AppCategory.PRODUCTIVE, AppCategoryClassifier.classify("com.slack"))

        // But because user explicitly restricted it, the policy MUST restrict and redirect it!
        val slackDecision = policy.evaluate("com.slack", timestamp = 5000L)
        assertTrue("Explicit restricted app must be redirected even if productive", slackDecision is PolicyDecision.Redirect)
        assertFalse("Explicit restricted app cannot be allowed", policy.isPackageAllowed("com.slack"))

        // Conversely, a productive app NOT restricted by the user (e.g. Google Docs) must be allowed
        val docsDecision = policy.evaluate("com.google.android.apps.docs", timestamp = 5000L)
        assertTrue(docsDecision is PolicyDecision.Allow)
        assertEquals(AllowReason.EDUCATIONAL_OR_PRODUCTIVE_APP, (docsDecision as PolicyDecision.Allow).reason)
        assertTrue(policy.isPackageAllowed("com.google.android.apps.docs"))
    }

    // ── 2. Wellness Wave Immunity ────────────────────────────────────────────
    @Test
    fun testWellnessWaveImmunityNeverRestricted() {
        setupActiveFocusRescue()

        val decision = policy.evaluate("com.example.myapplication", timestamp = 5000L)
        assertTrue(decision is PolicyDecision.Allow)
        assertEquals(AllowReason.WELLNESS_WAVE_APP, (decision as PolicyDecision.Allow).reason)
        assertTrue(policy.isPackageAllowed("com.example.myapplication"))
        assertFalse(policy.shouldRedirectToFocusRescue("com.example.myapplication", 5000L))
    }

    // ── 3. Emergency Package Protection ──────────────────────────────────────
    @Test
    fun testEmergencyPackageProtection() {
        setupActiveFocusRescue()

        val emergencyApps = listOf(
            "com.android.phone",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.android.server.telecom",
            "com.android.incallui",
            "com.samsung.android.incallui",
            "com.emergency.sos"
        )

        for (pkg in emergencyApps) {
            val decision = policy.evaluate(pkg, timestamp = 5000L)
            assertTrue("Emergency/Phone $pkg must be allowed", decision is PolicyDecision.Allow)
            assertEquals(AllowReason.EMERGENCY_OR_PHONE, (decision as PolicyDecision.Allow).reason)
            assertTrue(policy.isPackageAllowed(pkg))
            assertFalse(policy.shouldRedirectToFocusRescue(pkg, 5000L))
        }
    }

    // ── 4. Settings Protection ───────────────────────────────────────────────
    @Test
    fun testSystemSettingsNeverRestrictedToPreserveAccessibilityControl() {
        setupActiveFocusRescue()

        val settingsApps = listOf(
            "com.android.settings",
            "com.google.android.settings",
            "com.android.settings.accessibility"
        )

        for (pkg in settingsApps) {
            val decision = policy.evaluate(pkg, timestamp = 5000L)
            assertTrue("Settings $pkg must be allowed", decision is PolicyDecision.Allow)
            assertEquals(AllowReason.SYSTEM_SETTINGS, (decision as PolicyDecision.Allow).reason)
            assertTrue(policy.isPackageAllowed(pkg))
            assertFalse(policy.shouldRedirectToFocusRescue(pkg, 5000L))
        }
    }

    // ── 5. Keyboard Protection ───────────────────────────────────────────────
    @Test
    fun testKeyboardProtection() {
        setupActiveFocusRescue()

        val keyboards = listOf(
            "com.google.android.inputmethod.latin", // Gboard
            "com.touchtype.swiftkey",
            "com.samsung.android.honeyboard",
            "com.custom.keyboard"
        )

        for (pkg in keyboards) {
            val decision = policy.evaluate(pkg, timestamp = 5000L)
            assertTrue("Keyboard $pkg must be allowed", decision is PolicyDecision.Allow)
            assertEquals(AllowReason.SYSTEM_CRITICAL_FUNCTION, (decision as PolicyDecision.Allow).reason)
            assertTrue(policy.isPackageAllowed(pkg))
        }
    }

    // ── 6. Launcher Protection ───────────────────────────────────────────────
    @Test
    fun testLauncherProtection() {
        setupActiveFocusRescue()

        val launchers = listOf(
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",
            "com.miui.home",
            "com.huawei.android.launcher",
            "org.lineageos.trebuchet"
        )

        for (pkg in launchers) {
            val decision = policy.evaluate(pkg, timestamp = 5000L)
            assertTrue("Launcher $pkg must be allowed", decision is PolicyDecision.Allow)
            assertEquals(AllowReason.SYSTEM_CRITICAL_FUNCTION, (decision as PolicyDecision.Allow).reason)
            assertTrue(policy.isPackageAllowed(pkg))
        }
    }

    // ── 7. Wildcard Rejection ────────────────────────────────────────────────
    @Test
    fun testWildcardRejection() {
        assertFalse(PackageNameValidator.isValidPackageName("*"))
        assertFalse(PackageNameValidator.isValidPackageName(".*"))
        assertFalse(PackageNameValidator.isValidPackageName("com.*"))
        assertFalse(PackageNameValidator.isValidPackageName("com.instagram.*"))
        assertFalse(PackageNameValidator.isValidPackageName("com.instagram.*.android"))

        // When passed into sanitizeRestrictedPackages, wildcards are strictly stripped
        val sanitized = PackageNameValidator.sanitizeRestrictedPackages(setOf("*", ".*", "com.instagram.*", "com.instagram.android"))
        assertEquals(setOf("com.instagram.android"), sanitized)
    }

    // ── 8. Invalid Package Rejection ─────────────────────────────────────────
    @Test
    fun testInvalidPackageRejection() {
        assertFalse(PackageNameValidator.isValidPackageName(null))
        assertFalse(PackageNameValidator.isValidPackageName(""))
        assertFalse(PackageNameValidator.isValidPackageName("   "))
        assertFalse(PackageNameValidator.isValidPackageName("singleword")) // No dot
        assertFalse(PackageNameValidator.isValidPackageName("com..double.dot"))
        assertFalse(PackageNameValidator.isValidPackageName("com.space .pkg"))
        assertFalse(PackageNameValidator.isValidPackageName("com.123startwithdigit.app"))
        assertFalse(PackageNameValidator.isValidPackageName("com.app-with-hyphen.pkg")) // Android uses dots/underscores

        // Long package > 255 chars rejected
        val tooLong = "com.example." + "a".repeat(250)
        assertFalse(PackageNameValidator.isValidPackageName(tooLong))
    }

    // ── 9. Uninstalled Package Handling ─────────────────────────────────────
    @Test
    fun testUninstalledPackageHandling() {
        // Does not crash or throw exceptions
        assertTrue(PackageNameValidator.safeIsPackageInstalled(null, "com.nonexistent.app"))
        assertTrue(PackageNameValidator.safeIsPackageInstalled(null, null))
        assertTrue(PackageNameValidator.safeIsPackageInstalled(null, ""))
    }

    // ── 10. Redirect Cooldown ────────────────────────────────────────────────
    @Test
    fun testRedirectCooldown() {
        setupActiveFocusRescue()

        val decision1 = policy.evaluate("com.instagram.android", 10_000L)
        assertTrue(decision1 is PolicyDecision.Redirect)
        policy.recordRedirectSuccess("com.instagram.android", 10_000L)

        // Switch to same restricted package only 500ms later (< 1500ms cooldown)
        val rapidDecision = policy.evaluate("com.instagram.android", 10_500L)
        assertTrue(rapidDecision is PolicyDecision.Ignore)
        assertEquals(IgnoreReason.COOLDOWN_ACTIVE, (rapidDecision as PolicyDecision.Ignore).reason)

        // Switch after cooldown (2000ms later) succeeds
        val afterCooldownDecision = policy.evaluate("com.instagram.android", 12_500L)
        assertTrue(afterCooldownDecision is PolicyDecision.Redirect)
    }

    // ── 11. Loop-Breaker Reset ───────────────────────────────────────────────
    @Test
    fun testLoopBreakerReset() {
        setupActiveFocusRescue()

        var currentTime = 5000L
        val maxRedirects = FocusRescueConfig.DEFAULT_MAX_CONSECUTIVE_REDIRECTS // 5

        // Simulate 5 consecutive redirects
        for (i in 1..maxRedirects) {
            val decision = policy.evaluate("com.instagram.android", currentTime)
            assertTrue("Redirect #$i must succeed", decision is PolicyDecision.Redirect)
            policy.recordRedirectSuccess("com.instagram.android", currentTime)
            currentTime += 2000L
        }

        // 6th consecutive redirect must be suppressed by the loop-breaker
        val loopDecision = policy.evaluate("com.instagram.android", currentTime)
        assertTrue(loopDecision is PolicyDecision.Ignore)
        assertEquals(IgnoreReason.REDIRECT_LOOP_PREVENTED, (loopDecision as PolicyDecision.Ignore).reason)

        // User returning to Wellness Wave resets the counter
        policy.evaluate("com.example.myapplication", currentTime + 500L)

        // Subsequent restricted access works again
        val afterReset = policy.evaluate("com.instagram.android", currentTime + 3000L)
        assertTrue(afterReset is PolicyDecision.Redirect)
    }

    // ── 12. Loop-Breaker Safety & Rolling Time Window ────────────────────────
    @Test
    fun testLoopBreakerSafetyAndRollingTimeWindow() {
        setupActiveFocusRescue()

        var currentTime = 5000L
        for (i in 1..FocusRescueConfig.DEFAULT_MAX_CONSECUTIVE_REDIRECTS) {
            policy.recordRedirectSuccess("com.instagram.android", currentTime)
            currentTime += 1000L
        }

        // Loop breaker is active
        val blocked = policy.evaluate("com.instagram.android", currentTime)
        assertTrue(blocked is PolicyDecision.Ignore)
        assertEquals(IgnoreReason.REDIRECT_LOOP_PREVENTED, (blocked as PolicyDecision.Ignore).reason)

        // After rolling time window expires (10s later), counter resets automatically (no permanent bypass)
        val afterWindow = currentTime + FocusRescueConfig.REDIRECT_WINDOW_RESET_MS + 1000L
        val unblocked = policy.evaluate("com.instagram.android", afterWindow)
        assertTrue("Loop counter must reset after rolling window expires", unblocked is PolicyDecision.Redirect)
    }

    // ── 13. Corrupted Preference Handling ────────────────────────────────────
    @Test
    fun testCorruptedPreferenceHandling() {
        val prefs = FocusRescuePolicyPreferences(null)
        // With null context (or corrupted SharedPreferences), defaults safely to emptySet and true
        assertEquals(emptySet<String>(), prefs.getSelectedRestrictedPackages())
        assertTrue(prefs.isEducationalAppsAllowed())
        val config = prefs.toConfig()
        assertTrue(config.restrictedPackages.isEmpty())
        assertTrue(config.allowEducationalApps)
    }

    // ── 14. Focus Rescue Start Eligibility ───────────────────────────────────
    @Test
    fun testFocusRescueStartEligibilityRequiresTwoIgnoredInterventions() = runBlocking {
        sessionManager.startSession("Physics Study", 30, userRestrictedApps)
        val sessionId = sessionManager.activeSessionFlow.value!!.sessionId

        // Strike 0: cannot start Focus Rescue
        assertFalse(sessionManager.startFocusRescue())
        assertFalse(policy.isFocusRescueActive())

        // Strike 1 (dismiss intervention 1): still cannot start Focus Rescue
        sessionManager.onDistractionDetected("com.instagram.android", timestamp = 1000L)
        sessionManager.onInterventionDismissed(sessionId, 1)
        assertEquals(1, sessionManager.snapshotFlow.value.ignoredInterventionsCount)
        assertFalse(sessionManager.startFocusRescue())
        assertFalse(policy.isFocusRescueActive())

        // Strike 2 (dismiss intervention 2): state becomes FOCUS_RESCUE_READY
        sessionManager.onDistractionDetected("com.instagram.android", timestamp = 2000L)
        sessionManager.onInterventionDismissed(sessionId, 2)
        assertEquals(2, sessionManager.snapshotFlow.value.ignoredInterventionsCount)
        assertEquals(StudyRescueState.FOCUS_RESCUE_READY, sessionManager.stateFlow.value)

        // Now startFocusRescue succeeds!
        val started = sessionManager.startFocusRescue()
        assertTrue(started)
        assertEquals(StudyRescueState.FOCUS_RESCUE_ACTIVE, sessionManager.stateFlow.value)
        assertTrue(policy.isFocusRescueActive())
    }

    // ── 15. Focus Rescue Exit Behavior ───────────────────────────────────────
    @Test
    fun testFocusRescueExitBehavior() = runBlocking {
        setupActiveFocusRescue()
        assertTrue(policy.isFocusRescueActive())
        assertTrue(policy.canExitFocusRescue())

        // User exits Focus Rescue
        val exited = sessionManager.exitFocusRescue("USER_EXIT_BUTTON")
        assertTrue(exited)

        // Session returns to normal SESSION_ACTIVE
        assertEquals(StudyRescueState.SESSION_ACTIVE, sessionManager.stateFlow.value)
        assertFalse(policy.isFocusRescueActive())
        assertFalse(policy.canExitFocusRescue())

        kotlinx.coroutines.delay(50L)

        // Room DB record updated
        val sessionEntity = repository.getSessionById(sessionManager.activeSessionFlow.value!!.sessionId)
        assertEquals("SESSION_ACTIVE", sessionEntity?.currentState)
        assertEquals("EXITED", sessionEntity?.focusRescueState)
        assertEquals("USER_EXIT_BUTTON", sessionEntity?.focusRescueExitReason)
    }
}
