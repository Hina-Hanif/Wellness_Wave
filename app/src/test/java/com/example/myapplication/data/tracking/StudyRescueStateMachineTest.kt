package com.example.myapplication.data.tracking

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StudyRescueStateMachineTest {

    private lateinit var stateMachine: StudyRescueStateMachine
    private val defaultConfig = StudySessionConfig(
        sessionId = "test_session_1",
        taskTitle = "Math Final Exam Prep",
        plannedDurationMinutes = 45,
        startTimestamp = 1_000_000L,
        selectedDistractingApps = setOf("com.instagram.android", "com.zhiliaoapp.musically"),
        interventionTimeoutMs = 30_000L
    )

    @Before
    fun setUp() {
        stateMachine = StudyRescueStateMachine(debounceWindowMs = 300L)
    }

    @Test
    fun testStartingSession() {
        assertEquals(StudyRescueState.IDLE, stateMachine.state)

        val success = stateMachine.startSession(defaultConfig)
        assertTrue(success)
        assertEquals(StudyRescueState.SESSION_ACTIVE, stateMachine.state)

        val snapshot = stateMachine.snapshot
        assertEquals("test_session_1", snapshot.sessionId)
        assertEquals("Math Final Exam Prep", snapshot.taskTitle)
        assertEquals(45, snapshot.plannedDurationMinutes)
        assertEquals(0, snapshot.distractionCount)
        assertEquals(0, snapshot.ignoredInterventionsCount)
        assertFalse(snapshot.isFocusRescueEligible)
    }

    @Test
    fun testDetectingDistractionDoesNotCountAsIgnored() {
        stateMachine.startSession(defaultConfig)

        // Distraction occurs
        val success = stateMachine.onDistractionDetected("com.instagram.android", timestamp = 1_005_000L)
        assertTrue(success)
        assertEquals(StudyRescueState.DISTRACTION_DETECTED, stateMachine.state)

        val snapshot = stateMachine.snapshot
        assertEquals(1, snapshot.distractionCount)
        // Rule 2: Distraction event must NOT automatically count as an ignored intervention
        assertEquals(0, snapshot.ignoredInterventionsCount)
        assertFalse(snapshot.isFocusRescueEligible)
    }

    @Test
    fun testFirstIgnoredInterventionViaExplicitDismiss() {
        stateMachine.startSession(defaultConfig)

        stateMachine.onDistractionDetected("com.instagram.android", timestamp = 1_005_000L)
        stateMachine.triggerIntervention(timestamp = 1_005_100L)
        assertEquals(StudyRescueState.INTERVENTION_ONE_PENDING, stateMachine.state)

        // Explicit dismiss -> ignored count becomes 1, returns to SESSION_ACTIVE
        val dismissed = stateMachine.dismissIntervention(timestamp = 1_006_000L)
        assertTrue(dismissed)
        assertEquals(StudyRescueState.SESSION_ACTIVE, stateMachine.state)
        assertEquals(1, stateMachine.snapshot.ignoredInterventionsCount)
        assertFalse(stateMachine.snapshot.isFocusRescueEligible)
    }

    @Test
    fun testFirstIgnoredInterventionViaTimeout() {
        stateMachine.startSession(defaultConfig)

        stateMachine.onDistractionDetected("com.instagram.android", timestamp = 1_000_000L)
        stateMachine.triggerIntervention(timestamp = 1_000_000L)
        assertEquals(StudyRescueState.INTERVENTION_ONE_PENDING, stateMachine.state)

        // Check before timeout (20s later, timeout is 30s)
        assertFalse(stateMachine.checkInterventionTimeout(timestamp = 1_020_000L))
        assertEquals(StudyRescueState.INTERVENTION_ONE_PENDING, stateMachine.state)

        // Check after timeout (31s later)
        assertTrue(stateMachine.checkInterventionTimeout(timestamp = 1_031_000L))
        assertEquals(StudyRescueState.SESSION_ACTIVE, stateMachine.state)
        assertEquals(1, stateMachine.snapshot.ignoredInterventionsCount)
        assertFalse(stateMachine.snapshot.isFocusRescueEligible)
    }

    @Test
    fun testSecondIgnoredInterventionAndFocusRescueEligibility() {
        stateMachine.startSession(defaultConfig)

        // First ignored intervention
        stateMachine.onDistractionDetected("com.instagram.android", timestamp = 1_005_000L)
        stateMachine.triggerIntervention(timestamp = 1_005_100L)
        stateMachine.dismissIntervention(timestamp = 1_006_000L)
        assertEquals(1, stateMachine.snapshot.ignoredInterventionsCount)

        // Second distraction
        stateMachine.onDistractionDetected("com.zhiliaoapp.musically", timestamp = 1_010_000L)
        stateMachine.triggerIntervention(timestamp = 1_010_100L)
        assertEquals(StudyRescueState.INTERVENTION_TWO_PENDING, stateMachine.state)

        // Second ignored intervention
        val ignoredSecond = stateMachine.ignoreIntervention(timestamp = 1_011_000L)
        assertTrue(ignoredSecond)

        // Must now be FOCUS_RESCUE_READY!
        assertEquals(StudyRescueState.FOCUS_RESCUE_READY, stateMachine.state)
        assertEquals(2, stateMachine.snapshot.ignoredInterventionsCount)
        assertTrue(stateMachine.snapshot.isFocusRescueEligible)

        // User can now activate Focus Rescue
        val startedFocus = stateMachine.startFocusRescue(timestamp = 1_012_000L)
        assertTrue(startedFocus)
        assertEquals(StudyRescueState.FOCUS_RESCUE_ACTIVE, stateMachine.state)

        // User can exit Focus Rescue
        val exited = stateMachine.exitFocusRescue(timestamp = 1_015_000L)
        assertTrue(exited)
        assertEquals(StudyRescueState.SESSION_ACTIVE, stateMachine.state)
    }

    @Test
    fun testPositiveInterventionResponse() {
        stateMachine.startSession(defaultConfig)

        stateMachine.onDistractionDetected("com.instagram.android", timestamp = 1_005_000L)
        stateMachine.triggerIntervention(timestamp = 1_005_100L)
        assertEquals(StudyRescueState.INTERVENTION_ONE_PENDING, stateMachine.state)

        // Explicit positive action: acceptIntervention / resumeStudying
        val accepted = stateMachine.acceptIntervention(timestamp = 1_006_000L)
        assertTrue(accepted)
        assertEquals(StudyRescueState.SESSION_ACTIVE, stateMachine.state)

        // Must NOT be counted as ignored
        assertEquals(0, stateMachine.snapshot.ignoredInterventionsCount)
        assertFalse(stateMachine.snapshot.isFocusRescueEligible)
    }

    @Test
    fun testBreakResponse() {
        stateMachine.startSession(defaultConfig)

        stateMachine.onDistractionDetected("com.instagram.android", timestamp = 1_005_000L)
        stateMachine.triggerIntervention(timestamp = 1_005_100L)
        assertEquals(StudyRescueState.INTERVENTION_ONE_PENDING, stateMachine.state)

        // Mindful break response
        val breakTaken = stateMachine.takeBreak(timestamp = 1_006_000L)
        assertTrue(breakTaken)
        assertEquals(StudyRescueState.SESSION_PAUSED, stateMachine.state)

        // Must NOT increment ignored counter
        assertEquals(0, stateMachine.snapshot.ignoredInterventionsCount)

        // Resume session
        val resumed = stateMachine.resumeSession(timestamp = 1_010_000L)
        assertTrue(resumed)
        assertEquals(StudyRescueState.SESSION_ACTIVE, stateMachine.state)
    }

    @Test
    fun testDuplicateEventHandlingAndDebouncing() {
        stateMachine.startSession(defaultConfig)

        // First distraction event
        val first = stateMachine.onDistractionDetected("com.instagram.android", timestamp = 1_000_000L)
        assertTrue(first)
        assertEquals(1, stateMachine.snapshot.distractionCount)

        // Rapid duplicate event (within 300ms debounce window with same package)
        val duplicate = stateMachine.onDistractionDetected("com.instagram.android", timestamp = 1_000_150L)
        assertTrue(duplicate) // Handled gracefully
        // Counter must not have increased
        assertEquals(1, stateMachine.snapshot.distractionCount)

        // Legitimate event after debounce window
        val legitimate = stateMachine.onDistractionDetected("com.instagram.android", timestamp = 1_000_500L)
        assertTrue(legitimate)
        assertEquals(2, stateMachine.snapshot.distractionCount)
    }

    @Test
    fun testSessionCancellation() {
        stateMachine.startSession(defaultConfig)
        assertEquals(StudyRescueState.SESSION_ACTIVE, stateMachine.state)

        val cancelled = stateMachine.cancelSession(timestamp = 1_010_000L)
        assertTrue(cancelled)
        assertEquals(StudyRescueState.SESSION_CANCELLED, stateMachine.state)

        // New events rejected after cancellation
        val distractionAfterCancel = stateMachine.onDistractionDetected("com.instagram.android")
        assertFalse(distractionAfterCancel)
    }

    @Test
    fun testSessionCompletion() {
        stateMachine.startSession(defaultConfig)
        assertEquals(StudyRescueState.SESSION_ACTIVE, stateMachine.state)

        val completed = stateMachine.completeSession(timestamp = 1_045_000L)
        assertTrue(completed)
        assertEquals(StudyRescueState.SESSION_COMPLETED, stateMachine.state)

        // Can start a new session after completion
        val newSessionStarted = stateMachine.startSession(defaultConfig.copy(sessionId = "test_session_2"))
        assertTrue(newSessionStarted)
        assertEquals(StudyRescueState.SESSION_ACTIVE, stateMachine.state)
    }

    @Test
    fun testInvalidStateTransitions() {
        // In IDLE: cannot pause, complete, cancel, or start focus rescue
        assertFalse(stateMachine.pauseSession())
        assertFalse(stateMachine.resumeSession())
        assertFalse(stateMachine.completeSession())
        assertFalse(stateMachine.cancelSession())
        assertFalse(stateMachine.startFocusRescue())
        assertFalse(stateMachine.exitFocusRescue())
        assertFalse(stateMachine.acceptIntervention())
        assertFalse(stateMachine.dismissIntervention())
        assertFalse(stateMachine.onDistractionDetected("com.instagram.android"))

        // Start session
        stateMachine.startSession(defaultConfig)

        // In SESSION_ACTIVE: cannot start focus rescue directly without 2 ignored interventions
        assertFalse(stateMachine.startFocusRescue())
        assertFalse(stateMachine.exitFocusRescue())
        assertFalse(stateMachine.acceptIntervention())

        // Double start session should be rejected
        assertFalse(stateMachine.startSession(defaultConfig))
    }
}
