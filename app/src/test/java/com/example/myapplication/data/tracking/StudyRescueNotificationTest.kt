package com.example.myapplication.data.tracking

import com.example.myapplication.data.local.FakeStudyRescueDao
import com.example.myapplication.data.local.StudyRescueRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StudyRescueNotificationTest {

    private lateinit var fakeDao: FakeStudyRescueDao
    private lateinit var repository: StudyRescueRepository
    private lateinit var fakeNotifier: FakeStudyRescueNotifier
    private lateinit var manager: StudySessionManager

    private val testTimeoutMs = 150L // Fast timeout for deterministic unit tests

    @Before
    fun setUp() {
        fakeDao = FakeStudyRescueDao()
        repository = StudyRescueRepository(fakeDao)
        fakeNotifier = FakeStudyRescueNotifier()
        manager = StudySessionManager(
            repository = repository,
            notifier = fakeNotifier,
            interventionTimeoutMs = testTimeoutMs
        )
    }

    // ── 1. First Notification Trigger ───────────────────────────────────────
    @Test
    fun testFirstInterventionNotificationTriggerAndPersistence() = runBlocking {
        manager.startSession("Quantum Physics", 45, setOf("com.instagram.android"))
        val activeSession = manager.activeSessionFlow.value
        assertNotNull(activeSession)

        // Distraction detected
        val detected = manager.onDistractionDetected(
            packageName = "com.instagram.android",
            timestamp = 1000L
        )
        assertTrue(detected)

        // Verify state is INTERVENTION_ONE_PENDING
        assertEquals(StudyRescueState.INTERVENTION_ONE_PENDING, manager.stateFlow.value)

        // Verify notification was posted
        assertEquals(1, fakeNotifier.shownInterventions.size)
        val call = fakeNotifier.shownInterventions[0]
        assertEquals(activeSession!!.sessionId, call.sessionId)
        assertEquals(1, call.interventionNumber)
        assertFalse("Stage 1 notification must not be escalated", call.isEscalated)
        assertEquals("Quantum Physics", call.taskTitle)

        // Give coroutines a moment to persist
        delay(50L)

        // Verify Room persistence
        val interventions = repository.getInterventionsForSession(activeSession.sessionId)
        assertEquals(1, interventions.size)
        assertEquals(1, interventions[0].interventionNumber)
        assertEquals("STAGE_1_GENTLE", interventions[0].interventionType)
        assertFalse(interventions[0].ignored)
    }

    // ── 2. Second Notification Escalation ────────────────────────────────────
    @Test
    fun testSecondNotificationEscalationAfterIgnoredFirst() = runBlocking {
        manager.startSession("Calculus Review", 30, setOf("com.tiktok.android"))
        val session = manager.activeSessionFlow.value!!

        // Distraction 1: triggers stage 1
        manager.onDistractionDetected(packageName = "com.tiktok.android", timestamp = 1000L)
        assertEquals(StudyRescueState.INTERVENTION_ONE_PENDING, manager.stateFlow.value)
        assertEquals(1, fakeNotifier.shownInterventions.size)

        // User dismisses stage 1 (strike 1)
        manager.onInterventionDismissed(session.sessionId, 1)
        assertEquals(StudyRescueState.SESSION_ACTIVE, manager.stateFlow.value)
        assertEquals(1, manager.stateMachine.snapshot.ignoredInterventionsCount)

        // Distraction 2: should escalate to Stage 2
        val detected2 = manager.onDistractionDetected(packageName = "com.tiktok.android", timestamp = 2000L)
        assertTrue(detected2)

        assertEquals(StudyRescueState.INTERVENTION_TWO_PENDING, manager.stateFlow.value)
        assertEquals(2, fakeNotifier.shownInterventions.size)

        val secondCall = fakeNotifier.shownInterventions[1]
        assertEquals(2, secondCall.interventionNumber)
        assertTrue("Stage 2 notification must be escalated", secondCall.isEscalated)

        delay(50L)
        val records = repository.getInterventionsForSession(session.sessionId)
        assertEquals(2, records.size)
        assertEquals("STAGE_2_ESCALATED", records[1].interventionType)
        assertEquals(2, records[1].interventionNumber)
    }

    // ── 3. Positive Response: Resume Studying ────────────────────────────────
    @Test
    fun testPositiveActionResumeStudyingClearsInterventionWithoutStrike() = runBlocking {
        manager.startSession("History Essay", 60, setOf("com.twitter.android"))
        val session = manager.activeSessionFlow.value!!

        manager.onDistractionDetected(packageName = "com.twitter.android", timestamp = 1000L)
        assertEquals(StudyRescueState.INTERVENTION_ONE_PENDING, manager.stateFlow.value)

        // User clicks "Resume Studying"
        val resumed = manager.onInterventionResumed(session.sessionId, 1)
        assertTrue(resumed)

        // Must return to active session and NOT count as ignored
        assertEquals(StudyRescueState.SESSION_ACTIVE, manager.stateFlow.value)
        assertEquals(0, manager.stateMachine.snapshot.ignoredInterventionsCount)
        assertTrue(fakeNotifier.cancelledSessions.contains(session.sessionId))

        delay(50L)
        val record = repository.getInterventionByNumber(session.sessionId, 1)
        assertNotNull(record)
        assertEquals("RESUMED", record?.response)
        assertFalse(record!!.ignored)
    }

    // ── 4. Positive Response: Take Mindful Break ─────────────────────────────
    @Test
    fun testPositiveActionTakeBreakPausesSessionWithoutStrike() = runBlocking {
        manager.startSession("Chemistry Lab", 40, setOf("com.facebook.katana"))
        val session = manager.activeSessionFlow.value!!

        manager.onDistractionDetected(packageName = "com.facebook.katana", timestamp = 1000L)
        assertEquals(StudyRescueState.INTERVENTION_ONE_PENDING, manager.stateFlow.value)

        // User clicks "Take Break"
        val breakTaken = manager.onInterventionTakeBreak(session.sessionId, 1)
        assertTrue(breakTaken)

        // Session must be paused, ignored count stays 0
        assertEquals(StudyRescueState.SESSION_PAUSED, manager.stateFlow.value)
        assertEquals(0, manager.stateMachine.snapshot.ignoredInterventionsCount)
        assertTrue(fakeNotifier.cancelledSessions.contains(session.sessionId))

        delay(50L)
        val record = repository.getInterventionByNumber(session.sessionId, 1)
        assertNotNull(record)
        assertEquals("TAKE_BREAK", record?.response)
        assertFalse(record!!.ignored)
    }

    // ── 5. Ignored Timeout Behavior ──────────────────────────────────────────
    @Test
    fun testInterventionTimeoutCountsAsIgnoredStrike() = runBlocking {
        manager.startSession("Biology Flashcards", 20, setOf("com.snapchat.android"))
        val session = manager.activeSessionFlow.value!!

        manager.onDistractionDetected(packageName = "com.snapchat.android", timestamp = 1000L)
        assertEquals(StudyRescueState.INTERVENTION_ONE_PENDING, manager.stateFlow.value)

        // Wait for timeout job to trigger
        delay(testTimeoutMs + 100L)

        // State machine timeout handler transitions back to SESSION_ACTIVE with ignored count = 1
        assertEquals(StudyRescueState.SESSION_ACTIVE, manager.stateFlow.value)
        assertEquals(1, manager.stateMachine.snapshot.ignoredInterventionsCount)

        val record = repository.getInterventionByNumber(session.sessionId, 1)
        assertNotNull(record)
        assertEquals("TIMEOUT", record?.response)
        assertTrue(record!!.ignored)
    }

    // ── 6. Focus Rescue Escalation Eligibility ──────────────────────────────
    @Test
    fun testFocusRescueBecomesEligibleOnlyAfterTwoIgnoredInterventions() = runBlocking {
        manager.startSession("Organic Chemistry", 50, setOf("com.netflix.mediaclient"))
        val session = manager.activeSessionFlow.value!!

        // Ignored intervention 1 (via dismiss)
        manager.onDistractionDetected(packageName = "com.netflix.mediaclient", timestamp = 1000L)
        manager.onInterventionDismissed(session.sessionId, 1)
        assertEquals(1, manager.stateMachine.snapshot.ignoredInterventionsCount)
        assertFalse(manager.stateMachine.snapshot.isFocusRescueEligible)

        // Ignored intervention 2 (via dismiss)
        manager.onDistractionDetected(packageName = "com.netflix.mediaclient", timestamp = 2000L)
        manager.onInterventionDismissed(session.sessionId, 2)
        assertEquals(2, manager.stateMachine.snapshot.ignoredInterventionsCount)

        // Now FOCUS_RESCUE_READY!
        assertEquals(StudyRescueState.FOCUS_RESCUE_READY, manager.stateFlow.value)
        assertTrue(manager.stateMachine.snapshot.isFocusRescueEligible)

        // Distraction while FOCUS_RESCUE_READY does not spam intervention 3
        val preCalls = fakeNotifier.shownInterventions.size
        manager.onDistractionDetected(packageName = "com.netflix.mediaclient", timestamp = 3000L)
        assertEquals(preCalls, fakeNotifier.shownInterventions.size)
    }

    // ── 7. Cooldown & Duplicate Prevention ───────────────────────────────────
    @Test
    fun testDuplicateDistractionsWhileInterventionPendingAreIgnored() = runBlocking {
        manager.startSession("Literature Reading", 30, setOf("com.instagram.android"))

        manager.onDistractionDetected(packageName = "com.instagram.android", timestamp = 1000L)
        assertEquals(1, fakeNotifier.shownInterventions.size)

        // Rapid subsequent distractions while intervention 1 is still pending
        manager.onDistractionDetected(packageName = "com.instagram.android", timestamp = 1050L)
        manager.onDistractionDetected(packageName = "com.instagram.android", timestamp = 1100L)

        // Notification count must remain strictly 1 (cooldown prevents duplicate notifications)
        assertEquals(1, fakeNotifier.shownInterventions.size)
        assertEquals(StudyRescueState.INTERVENTION_ONE_PENDING, manager.stateFlow.value)
    }

    // ── 8. Notification Permission Denied ────────────────────────────────────
    @Test
    fun testNotificationPermissionDeniedDoesNotCrashOrBlockState() = runBlocking {
        fakeNotifier.hasPermission = false
        manager.startSession("Math Exercises", 25, setOf("com.youtube.android"))
        val session = manager.activeSessionFlow.value!!

        val detected = manager.onDistractionDetected(packageName = "com.youtube.android", timestamp = 1000L)
        assertTrue(detected)

        // State machine advances despite notification failure
        assertEquals(StudyRescueState.INTERVENTION_ONE_PENDING, manager.stateFlow.value)
        // Notification list is empty because permission was denied
        assertEquals(0, fakeNotifier.shownInterventions.size)

        delay(50L)
        // Intervention record is still safely persisted in Room
        val record = repository.getInterventionByNumber(session.sessionId, 1)
        assertNotNull(record)
    }

    // ── 9. Inactive Session Gating ──────────────────────────────────────────
    @Test
    fun testInactiveAndPausedSessionsDoNotTriggerInterventions() = runBlocking {
        // No session started (IDLE)
        val detectedWhileIdle = manager.onDistractionDetected(packageName = "com.instagram.android", timestamp = 1000L)
        assertFalse(detectedWhileIdle)
        assertEquals(0, fakeNotifier.shownInterventions.size)

        // Start then pause session
        manager.startSession("French Vocab", 15, setOf("com.instagram.android"))
        manager.pauseSession()
        assertEquals(StudyRescueState.SESSION_PAUSED, manager.stateFlow.value)

        val detectedWhilePaused = manager.onDistractionDetected(packageName = "com.instagram.android", timestamp = 2000L)
        assertFalse(detectedWhilePaused)
        assertEquals(0, fakeNotifier.shownInterventions.size)
    }
}

class FakeStudyRescueNotifier : StudyRescueNotifier {
    var hasPermission: Boolean = true
    val shownInterventions = mutableListOf<InterventionCall>()
    val cancelledSessions = mutableListOf<String>()

    data class InterventionCall(
        val sessionId: String,
        val interventionNumber: Int,
        val taskTitle: String,
        val isEscalated: Boolean
    )

    override fun showInterventionNotification(
        sessionId: String,
        interventionNumber: Int,
        taskTitle: String,
        isEscalated: Boolean
    ): Boolean {
        if (!hasPermission) return false
        shownInterventions.add(InterventionCall(sessionId, interventionNumber, taskTitle, isEscalated))
        return true
    }

    override fun cancelNotification(sessionId: String) {
        cancelledSessions.add(sessionId)
    }

    override fun hasNotificationPermission(): Boolean = hasPermission
}
