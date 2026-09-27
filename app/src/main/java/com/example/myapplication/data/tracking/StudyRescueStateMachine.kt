package com.example.myapplication.data.tracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Domain states for the Study Rescue state machine.
 */
enum class StudyRescueState {
    IDLE,
    SESSION_ACTIVE,
    DISTRACTION_DETECTED,
    INTERVENTION_ONE_PENDING,
    INTERVENTION_TWO_PENDING,
    FOCUS_RESCUE_READY,
    FOCUS_RESCUE_ACTIVE,
    SESSION_COMPLETED,
    SESSION_CANCELLED,
    SESSION_PAUSED
}

/**
 * Configuration supplied when initiating a study session.
 */
data class StudySessionConfig(
    val sessionId: String,
    val taskTitle: String,
    val plannedDurationMinutes: Int,
    val startTimestamp: Long = System.currentTimeMillis(),
    val selectedDistractingApps: Set<String> = emptySet(),
    val interventionTimeoutMs: Long = 30_000L // 30 seconds default intervention timeout
)

/**
 * Immutable snapshot of current session metrics and progression.
 */
data class StudySessionSnapshot(
    val sessionId: String? = null,
    val state: StudyRescueState = StudyRescueState.IDLE,
    val taskTitle: String = "",
    val plannedDurationMinutes: Int = 0,
    val startTimestamp: Long = 0L,
    val selectedDistractingApps: Set<String> = emptySet(),
    val distractionCount: Int = 0,
    val ignoredInterventionsCount: Int = 0,
    val isFocusRescueEligible: Boolean = false,
    val lastInterventionTimestamp: Long? = null,
    val lastActionTimestamp: Long = 0L
)

/**
 * Deterministic, thread-safe, and idempotent domain state machine
 * for Study Rescue and optional Focus Rescue escalation.
 */
class StudyRescueStateMachine(
    private val debounceWindowMs: Long = 400L
) {
    private val lock = Any()

    private var currentState: StudyRescueState = StudyRescueState.IDLE
    private var currentConfig: StudySessionConfig? = null

    private var distractionCount: Int = 0
    private var ignoredInterventionsCount: Int = 0
    private var lastInterventionTimestamp: Long? = null
    private var lastActionTimestamp: Long = 0L
    private var wasFocusRescueActiveBeforePause: Boolean = false

    // Debounce tracking for rapid duplicate inputs/events
    private var lastEventTimestamp: Long = 0L
    private var lastDistractionPackage: String? = null

    private val _stateFlow = MutableStateFlow(StudyRescueState.IDLE)
    val stateFlow: StateFlow<StudyRescueState> = _stateFlow.asStateFlow()

    private val _snapshotFlow = MutableStateFlow(StudySessionSnapshot())
    val snapshotFlow: StateFlow<StudySessionSnapshot> = _snapshotFlow.asStateFlow()

    val state: StudyRescueState
        get() = synchronized(lock) { currentState }

    val snapshot: StudySessionSnapshot
        get() = synchronized(lock) { buildSnapshotLocked() }

    // ── Session Lifecycle ───────────────────────────────────────────────────

    /**
     * Starts a new study session with the given configuration.
     * Allowed from IDLE, SESSION_COMPLETED, or SESSION_CANCELLED.
     */
    fun startSession(config: StudySessionConfig): Boolean = synchronized(lock) {
        if (currentState != StudyRescueState.IDLE &&
            currentState != StudyRescueState.SESSION_COMPLETED &&
            currentState != StudyRescueState.SESSION_CANCELLED
        ) {
            return false // Session already running or paused
        }

        currentConfig = config
        distractionCount = 0
        ignoredInterventionsCount = 0
        lastInterventionTimestamp = null
        lastActionTimestamp = config.startTimestamp
        wasFocusRescueActiveBeforePause = false
        lastEventTimestamp = 0L
        lastDistractionPackage = null

        transitionToLocked(StudyRescueState.SESSION_ACTIVE)
        return true
    }

    /**
     * Directly rehydrates session state from persistent storage after process restart.
     */
    fun restoreSession(
        config: StudySessionConfig,
        targetState: StudyRescueState,
        distractions: Int = 0,
        ignoredInterventions: Int = 0
    ): Boolean = synchronized(lock) {
        currentConfig = config
        distractionCount = distractions
        ignoredInterventionsCount = ignoredInterventions
        lastInterventionTimestamp = null
        lastActionTimestamp = config.startTimestamp
        wasFocusRescueActiveBeforePause = (targetState == StudyRescueState.FOCUS_RESCUE_ACTIVE)
        lastEventTimestamp = 0L
        lastDistractionPackage = null

        transitionToLocked(targetState)
        return true
    }

    /**
     * Pauses an ongoing session. Idempotent if already paused.
     */
    fun pauseSession(timestamp: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        if (currentState == StudyRescueState.SESSION_PAUSED) {
            return true // Idempotent
        }

        if (currentState != StudyRescueState.SESSION_ACTIVE &&
            currentState != StudyRescueState.DISTRACTION_DETECTED &&
            currentState != StudyRescueState.INTERVENTION_ONE_PENDING &&
            currentState != StudyRescueState.INTERVENTION_TWO_PENDING &&
            currentState != StudyRescueState.FOCUS_RESCUE_READY &&
            currentState != StudyRescueState.FOCUS_RESCUE_ACTIVE
        ) {
            return false
        }

        wasFocusRescueActiveBeforePause = (currentState == StudyRescueState.FOCUS_RESCUE_ACTIVE)
        lastActionTimestamp = timestamp
        transitionToLocked(StudyRescueState.SESSION_PAUSED)
        return true
    }

    /**
     * Resumes a paused session.
     */
    fun resumeSession(timestamp: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        if (currentState != StudyRescueState.SESSION_PAUSED) {
            return false
        }

        lastActionTimestamp = timestamp
        val targetState = if (wasFocusRescueActiveBeforePause) {
            StudyRescueState.FOCUS_RESCUE_ACTIVE
        } else {
            StudyRescueState.SESSION_ACTIVE
        }
        transitionToLocked(targetState)
        return true
    }

    /**
     * Completes an active or paused session.
     */
    fun completeSession(timestamp: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        if (!isSessionOngoingLocked()) {
            return false
        }

        lastActionTimestamp = timestamp
        transitionToLocked(StudyRescueState.SESSION_COMPLETED)
        return true
    }

    /**
     * Cancels / abandons an active or paused session.
     */
    fun cancelSession(timestamp: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        if (!isSessionOngoingLocked()) {
            return false
        }

        lastActionTimestamp = timestamp
        transitionToLocked(StudyRescueState.SESSION_CANCELLED)
        return true
    }

    // ── Distraction & Intervention Handling ─────────────────────────────────

    /**
     * Records a distraction event during a session.
     * Does NOT automatically count as an ignored intervention (Rule 2).
     *
     * Transitions SESSION_ACTIVE -> DISTRACTION_DETECTED.
     */
    fun onDistractionDetected(
        packageName: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): Boolean = synchronized(lock) {
        if (!isSessionOngoingLocked() || currentState == StudyRescueState.SESSION_PAUSED) {
            return false
        }

        // Debounce rapid duplicate accessibility / sensor events
        if (timestamp - lastEventTimestamp < debounceWindowMs && packageName == lastDistractionPackage) {
            return true // Successfully deduplicated
        }

        lastEventTimestamp = timestamp
        lastDistractionPackage = packageName

        // If an intervention is already pending, check if it timed out first
        if (currentState == StudyRescueState.INTERVENTION_ONE_PENDING ||
            currentState == StudyRescueState.INTERVENTION_TWO_PENDING
        ) {
            checkInterventionTimeoutLocked(timestamp)
        }

        // If state is still pending intervention or already at focus rescue, record distraction counter only
        distractionCount++
        lastActionTimestamp = timestamp

        if (currentState == StudyRescueState.SESSION_ACTIVE) {
            transitionToLocked(StudyRescueState.DISTRACTION_DETECTED)
        } else {
            _snapshotFlow.value = buildSnapshotLocked()
        }

        return true
    }

    /**
     * Triggers the appropriate pending intervention.
     * DISTRACTION_DETECTED -> INTERVENTION_ONE_PENDING (if 0 ignored)
     *                      -> INTERVENTION_TWO_PENDING (if 1 ignored)
     * If 2+ already ignored, transitions to FOCUS_RESCUE_READY.
     */
    fun triggerIntervention(timestamp: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        if (currentState != StudyRescueState.DISTRACTION_DETECTED &&
            currentState != StudyRescueState.SESSION_ACTIVE
        ) {
            return false
        }

        lastInterventionTimestamp = timestamp
        lastActionTimestamp = timestamp

        when {
            ignoredInterventionsCount == 0 -> {
                transitionToLocked(StudyRescueState.INTERVENTION_ONE_PENDING)
            }
            ignoredInterventionsCount == 1 -> {
                transitionToLocked(StudyRescueState.INTERVENTION_TWO_PENDING)
            }
            else -> {
                transitionToLocked(StudyRescueState.FOCUS_RESCUE_READY)
            }
        }
        return true
    }

    /**
     * User explicitly accepts or acknowledges intervention.
     * Counts as positive action -> Returns to SESSION_ACTIVE without incrementing ignored counter.
     */
    fun acceptIntervention(timestamp: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        if (currentState != StudyRescueState.INTERVENTION_ONE_PENDING &&
            currentState != StudyRescueState.INTERVENTION_TWO_PENDING &&
            currentState != StudyRescueState.DISTRACTION_DETECTED
        ) {
            return false
        }

        lastActionTimestamp = timestamp
        lastInterventionTimestamp = null
        transitionToLocked(StudyRescueState.SESSION_ACTIVE)
        return true
    }

    /**
     * User resumes studying.
     * Counts as positive action -> Returns to SESSION_ACTIVE without incrementing ignored counter.
     */
    fun resumeStudying(timestamp: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        return acceptIntervention(timestamp)
    }

    /**
     * User takes a mindful break in response to distraction.
     * Counts as positive action -> Pauses session without incrementing ignored counter.
     */
    fun takeBreak(timestamp: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        if (currentState != StudyRescueState.INTERVENTION_ONE_PENDING &&
            currentState != StudyRescueState.INTERVENTION_TWO_PENDING &&
            currentState != StudyRescueState.DISTRACTION_DETECTED &&
            currentState != StudyRescueState.SESSION_ACTIVE
        ) {
            return false
        }

        lastActionTimestamp = timestamp
        lastInterventionTimestamp = null
        transitionToLocked(StudyRescueState.SESSION_PAUSED)
        return true
    }

    /**
     * User explicitly dismisses the intervention.
     * Counts as an IGNORED intervention.
     */
    fun dismissIntervention(timestamp: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        return handleIgnoredInterventionLocked(timestamp)
    }

    /**
     * User ignores the intervention.
     * Counts as an IGNORED intervention.
     */
    fun ignoreIntervention(timestamp: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        return handleIgnoredInterventionLocked(timestamp)
    }

    /**
     * Checks whether the current pending intervention has timed out without user response.
     * If timed out, marks the intervention as ignored.
     */
    fun checkInterventionTimeout(timestamp: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        return checkInterventionTimeoutLocked(timestamp)
    }

    // ── Focus Rescue Escalation ─────────────────────────────────────────────

    /**
     * Starts Focus Rescue escalation mode.
     * Allowed only when FOCUS_RESCUE_READY (i.e. after 2 genuinely ignored interventions).
     */
    fun startFocusRescue(timestamp: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        if (currentState != StudyRescueState.FOCUS_RESCUE_READY) {
            return false
        }

        lastActionTimestamp = timestamp
        transitionToLocked(StudyRescueState.FOCUS_RESCUE_ACTIVE)
        return true
    }

    /**
     * Exits Focus Rescue escalation mode back to standard SESSION_ACTIVE.
     */
    fun exitFocusRescue(timestamp: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        if (currentState != StudyRescueState.FOCUS_RESCUE_ACTIVE) {
            return false
        }

        lastActionTimestamp = timestamp
        transitionToLocked(StudyRescueState.SESSION_ACTIVE)
        return true
    }

    // ── Internal Helpers ────────────────────────────────────────────────────

    private fun handleIgnoredInterventionLocked(timestamp: Long): Boolean {
        when (currentState) {
            StudyRescueState.INTERVENTION_ONE_PENDING -> {
                ignoredInterventionsCount = 1
                lastActionTimestamp = timestamp
                lastInterventionTimestamp = null
                transitionToLocked(StudyRescueState.SESSION_ACTIVE)
                return true
            }
            StudyRescueState.INTERVENTION_TWO_PENDING -> {
                ignoredInterventionsCount = 2
                lastActionTimestamp = timestamp
                lastInterventionTimestamp = null
                // 2 ignored interventions -> Focus Rescue Ready!
                transitionToLocked(StudyRescueState.FOCUS_RESCUE_READY)
                return true
            }
            else -> return false
        }
    }

    private fun checkInterventionTimeoutLocked(timestamp: Long): Boolean {
        val interventionStart = lastInterventionTimestamp ?: return false
        val timeoutMs = currentConfig?.interventionTimeoutMs ?: 30_000L

        if (timestamp - interventionStart >= timeoutMs) {
            return handleIgnoredInterventionLocked(timestamp)
        }
        return false
    }

    private fun isSessionOngoingLocked(): Boolean {
        return currentState != StudyRescueState.IDLE &&
                currentState != StudyRescueState.SESSION_COMPLETED &&
                currentState != StudyRescueState.SESSION_CANCELLED
    }

    private fun transitionToLocked(newState: StudyRescueState) {
        currentState = newState
        _stateFlow.value = newState
        _snapshotFlow.value = buildSnapshotLocked()
    }

    private fun buildSnapshotLocked(): StudySessionSnapshot {
        val config = currentConfig
        return StudySessionSnapshot(
            sessionId = config?.sessionId,
            state = currentState,
            taskTitle = config?.taskTitle ?: "",
            plannedDurationMinutes = config?.plannedDurationMinutes ?: 0,
            startTimestamp = config?.startTimestamp ?: 0L,
            selectedDistractingApps = config?.selectedDistractingApps ?: emptySet(),
            distractionCount = distractionCount,
            ignoredInterventionsCount = ignoredInterventionsCount,
            isFocusRescueEligible = (ignoredInterventionsCount >= 2),
            lastInterventionTimestamp = lastInterventionTimestamp,
            lastActionTimestamp = lastActionTimestamp
        )
    }
}
