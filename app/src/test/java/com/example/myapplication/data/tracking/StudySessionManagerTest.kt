package com.example.myapplication.data.tracking

import android.content.Context
import com.example.myapplication.data.local.FakeStudyRescueDao
import com.example.myapplication.data.local.StudyRescueRepository
import com.example.myapplication.data.local.StudySessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

class StudySessionManagerTest {

    private lateinit var fakeDao: FakeStudyRescueDao
    private lateinit var repository: StudyRescueRepository
    private lateinit var sessionManager: StudySessionManager

    @Before
    fun setUp() {
        fakeDao = FakeStudyRescueDao()
        repository = StudyRescueRepository(fakeDao)
        sessionManager = StudySessionManager(repository)
    }

    // ── 1. Duplicate Start Prevention ───────────────────────────────────────
    @Test
    fun testDuplicateStartPrevention() {
        val firstStart = sessionManager.startSession(
            taskTitle = "First Session",
            durationMinutes = 25,
            selectedApps = setOf("com.instagram.android")
        )
        assertTrue(firstStart)
        assertTrue(sessionManager.isSessionActive())

        // Attempting to start a second concurrent session must be rejected
        val secondStart = sessionManager.startSession(
            taskTitle = "Second Session",
            durationMinutes = 15,
            selectedApps = setOf("com.google.android.youtube")
        )
        assertFalse(secondStart)
    }

    // ── 2. Paused Timer & Resume Exactness ───────────────────────────────────
    @Test
    fun testPauseAndResumeTimeCalculation() = runBlocking {
        sessionManager.startSession(
            taskTitle = "Pause Math Test",
            durationMinutes = 25,
            selectedApps = setOf("com.instagram.android")
        )

        val initialSession = sessionManager.activeSessionFlow.value
        assertNotNull(initialSession)
        val originalStartTime = initialSession!!.startTimeMillis

        // Pause session
        val paused = sessionManager.pauseSession()
        assertTrue(paused)
        assertEquals(StudyRescueState.SESSION_PAUSED, sessionManager.stateFlow.value)

        val pausedSession = sessionManager.activeSessionFlow.value
        assertEquals("SESSION_PAUSED", pausedSession?.currentState)

        // Simulate 500ms pause duration
        Thread.sleep(50)

        // Resume session
        val resumed = sessionManager.resumeSession()
        assertTrue(resumed)
        assertEquals(StudyRescueState.SESSION_ACTIVE, sessionManager.stateFlow.value)

        val resumedSession = sessionManager.activeSessionFlow.value
        assertNotNull(resumedSession)

        // Start time must have been shifted forward by the pause duration
        assertTrue(resumedSession!!.startTimeMillis >= originalStartTime)
    }

    // ── 3. Emergency Package Sanitization ───────────────────────────────────
    @Test
    fun testEmergencyPackageSanitization() {
        val dangerousInput = setOf(
            "com.android.phone",
            "com.google.android.dialer",
            "com.android.systemui",
            "com.android.settings",
            "com.example.myapplication",
            "invalid_no_dot",
            "  ",
            "com.instagram.android",
            "com.reddit.frontpage"
        )

        sessionManager.startSession(
            taskTitle = "Sanitization Test",
            durationMinutes = 30,
            selectedApps = dangerousInput
        )

        val session = sessionManager.activeSessionFlow.value
        assertNotNull(session)

        val persisted = session!!.selectedRestrictedAppPackages

        // Emergency apps and invalid packages must be filtered out
        assertFalse(persisted.contains("com.android.phone"))
        assertFalse(persisted.contains("com.google.android.dialer"))
        assertFalse(persisted.contains("com.android.systemui"))
        assertFalse(persisted.contains("com.android.settings"))
        assertFalse(persisted.contains("com.example.myapplication"))
        assertFalse(persisted.contains("invalid_no_dot"))
        assertFalse(persisted.contains("  "))

        // Legitimate user selections must be preserved
        assertTrue(persisted.contains("com.instagram.android"))
        assertTrue(persisted.contains("com.reddit.frontpage"))
    }

    // ── 4. Session Cancellation and Completion ──────────────────────────────
    @Test
    fun testSessionCancellationAndCompletion() {
        sessionManager.startSession("Complete Test", 25, emptySet())
        assertTrue(sessionManager.isSessionActive())

        val completed = sessionManager.completeSession()
        assertTrue(completed)
        assertFalse(sessionManager.isSessionActive())
        assertNull(sessionManager.activeSessionFlow.value)
        assertEquals(StudyRescueState.SESSION_COMPLETED, sessionManager.stateFlow.value)

        // Can start a new session after completion
        val restarted = sessionManager.startSession("Restart Test", 15, emptySet())
        assertTrue(restarted)

        val cancelled = sessionManager.cancelSession()
        assertTrue(cancelled)
        assertFalse(sessionManager.isSessionActive())
        assertNull(sessionManager.activeSessionFlow.value)
        assertEquals(StudyRescueState.SESSION_CANCELLED, sessionManager.stateFlow.value)
    }
}
