package com.example.myapplication.data.tracking

import com.example.myapplication.data.local.FakeStudyRescueDao
import com.example.myapplication.data.local.StudyRescueRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StudyRescueBehaviorBridgeTest {

    private lateinit var fakeDao: FakeStudyRescueDao
    private lateinit var repository: StudyRescueRepository
    private lateinit var sessionManager: StudySessionManager
    private lateinit var bridge: StudyRescueBehaviorBridge

    private val restrictedApps = setOf(
        "com.instagram.android",
        "com.reddit.frontpage",
        "com.zhiliaoapp.musically"
    )

    @Before
    fun setUp() {
        fakeDao = FakeStudyRescueDao()
        repository = StudyRescueRepository(fakeDao)
        sessionManager = StudySessionManager(repository)
        bridge = StudyRescueBehaviorBridge { sessionManager }
    }

    // ── 1. Inactive / Paused Session Gating ──────────────────────────────────
    @Test
    fun testIgnoresEventsWhenNoSessionActive() {
        assertFalse(sessionManager.isSessionActive())

        // App switch to a restricted app when no session is active
        val handled = bridge.onAppSwitched(
            fromPackage = "com.android.launcher",
            toPackage = "com.instagram.android",
            recentSwitchCount = 1,
            timestamp = 1000L
        )
        assertFalse(handled)
        assertEquals(StudyRescueState.IDLE, sessionManager.stateFlow.value)

        // Rapid switch when no session is active
        val rapidHandled = bridge.onRapidSwitchDetected(
            currentPackage = "com.instagram.android",
            totalSwitches = 10,
            timestamp = 2000L
        )
        assertFalse(rapidHandled)
    }

    @Test
    fun testIgnoresEventsWhenSessionIsPaused() {
        sessionManager.startSession("Study Physics", 30, restrictedApps)
        sessionManager.pauseSession()
        assertEquals(StudyRescueState.SESSION_PAUSED, sessionManager.stateFlow.value)

        val handled = bridge.onAppSwitched(
            fromPackage = "com.example.myapplication",
            toPackage = "com.instagram.android",
            recentSwitchCount = 1,
            timestamp = 1000L
        )
        assertFalse(handled)
        assertEquals(StudyRescueState.SESSION_PAUSED, sessionManager.stateFlow.value)

        val rapidHandled = bridge.onRapidSwitchDetected(
            currentPackage = "com.instagram.android",
            totalSwitches = 10,
            timestamp = 2000L
        )
        assertFalse(rapidHandled)
        assertEquals(StudyRescueState.SESSION_PAUSED, sessionManager.stateFlow.value)
    }

    // ── 2. User Restriction Filtering ───────────────────────────────────────
    @Test
    fun testIgnoresUnrestrictedApps() {
        sessionManager.startSession("Deep Focus", 25, restrictedApps)
        assertTrue(sessionManager.isSessionActive())

        // Switching to Wikipedia or a non-restricted app must NOT be a distraction
        val handled = bridge.onAppSwitched(
            fromPackage = "com.example.myapplication",
            toPackage = "org.wikipedia",
            recentSwitchCount = 1,
            timestamp = 1000L
        )
        assertFalse(handled)
        assertEquals(StudyRescueState.SESSION_ACTIVE, sessionManager.stateFlow.value)
    }

    @Test
    fun testTriggersDistractionForSelectedRestrictedApp() {
        sessionManager.startSession("Deep Focus", 25, restrictedApps)
        assertTrue(sessionManager.isSessionActive())

        val handled = bridge.onAppSwitched(
            fromPackage = "com.example.myapplication",
            toPackage = "com.instagram.android",
            recentSwitchCount = 1,
            timestamp = 1000L
        )
        assertTrue(handled)
        assertEquals(StudyRescueState.INTERVENTION_ONE_PENDING, sessionManager.stateFlow.value)
        assertEquals(1, sessionManager.snapshotFlow.value.distractionCount)
    }

    // ── 3. Protected & Productive Apps Exemption ─────────────────────────────
    @Test
    fun testProtectedSystemAndEmergencyAppsNeverTriggerDistraction() {
        sessionManager.startSession("Math Practice", 45, restrictedApps)

        val protectedList = listOf(
            "com.android.phone",
            "com.google.android.dialer",
            "com.android.systemui",
            "com.android.settings",
            "com.example.myapplication" // Returning to own app
        )

        var time = 1000L
        for (pkg in protectedList) {
            val handled = bridge.onAppSwitched(
                fromPackage = "com.android.launcher",
                toPackage = pkg,
                recentSwitchCount = 1,
                timestamp = time
            )
            assertFalse("Expected $pkg to be protected", handled)
            time += 40_000L // ensure cooldown passed
        }
    }

    @Test
    fun testProductiveAppsNeverTriggerDistraction() {
        sessionManager.startSession("Reading", 30, restrictedApps)

        val productiveList = listOf(
            "com.google.android.apps.docs",
            "com.google.android.apps.drive",
            "com.slack",
            "com.notion.id"
        )

        var time = 1000L
        for (pkg in productiveList) {
            val handled = bridge.onAppSwitched(
                fromPackage = "com.android.launcher",
                toPackage = pkg,
                recentSwitchCount = 1,
                timestamp = time
            )
            assertFalse("Expected productive app $pkg to be ignored", handled)
            time += 40_000L
        }
    }

    // ── 4. Cooldown & Debounce Deduplication ────────────────────────────────
    @Test
    fun testCooldownPreventsDuplicateDistractions() {
        sessionManager.startSession("Coding", 60, restrictedApps)

        // First open of Instagram
        val firstHandled = bridge.onAppSwitched(
            fromPackage = "com.example.myapplication",
            toPackage = "com.instagram.android",
            recentSwitchCount = 1,
            timestamp = 10_000L
        )
        assertTrue(firstHandled)
        assertEquals(1, sessionManager.snapshotFlow.value.distractionCount)

        // Repeat callback 5 seconds later for same package (e.g. sub-window or rapid change)
        val repeatHandled = bridge.onAppSwitched(
            fromPackage = "com.instagram.android",
            toPackage = "com.instagram.android",
            recentSwitchCount = 2,
            timestamp = 15_000L
        )
        assertFalse(repeatHandled)
        assertEquals(1, sessionManager.snapshotFlow.value.distractionCount)

        // Rapid switch to another restricted app within global cooldown (< 10s)
        val anotherRestrictedHandled = bridge.onAppSwitched(
            fromPackage = "com.instagram.android",
            toPackage = "com.reddit.frontpage",
            recentSwitchCount = 3,
            timestamp = 16_000L
        )
        assertFalse(anotherRestrictedHandled)
        assertEquals(1, sessionManager.snapshotFlow.value.distractionCount)

        // After cooldown expires (35 seconds later from initial Instagram trigger)
        val afterCooldownHandled = bridge.onAppSwitched(
            fromPackage = "com.example.myapplication",
            toPackage = "com.instagram.android",
            recentSwitchCount = 4,
            timestamp = 46_000L
        )
        assertTrue(afterCooldownHandled)
        assertEquals(2, sessionManager.snapshotFlow.value.distractionCount)
    }

    // ── 5. Rapid App Switching Handling ─────────────────────────────────────
    @Test
    fun testRapidAppSwitchingTriggersDistraction() {
        sessionManager.startSession("Research", 40, restrictedApps)

        val handled = bridge.onRapidSwitchDetected(
            currentPackage = "com.someother.app",
            totalSwitches = 9,
            timestamp = 10_000L
        )
        assertTrue(handled)
        assertEquals(StudyRescueState.INTERVENTION_ONE_PENDING, sessionManager.stateFlow.value)
        assertEquals(1, sessionManager.snapshotFlow.value.distractionCount)

        // Duplicate rapid switch within global cooldown (5s later) is suppressed
        val duplicateRapid = bridge.onRapidSwitchDetected(
            currentPackage = "com.someother.app",
            totalSwitches = 12,
            timestamp = 15_000L
        )
        assertFalse(duplicateRapid)
        assertEquals(1, sessionManager.snapshotFlow.value.distractionCount)
    }
}
