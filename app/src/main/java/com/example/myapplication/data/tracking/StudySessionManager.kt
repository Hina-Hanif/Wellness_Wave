package com.example.myapplication.data.tracking

import android.content.Context
import com.example.myapplication.data.local.BehaviorDatabase
import com.example.myapplication.data.local.InterventionRecord
import com.example.myapplication.data.local.StudyRescueRepository
import com.example.myapplication.data.local.StudySessionEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Unified controller coordinating the StudyRescueStateMachine,
 * persistent Room storage (StudyRescueRepository), and live ticking state.
 *
 * Ensures a single source of truth, lifecycle resilience, and zero duplicate sessions.
 */
class StudySessionManager internal constructor(
    private val repository: StudyRescueRepository,
    private val notifier: StudyRescueNotifier = NoOpStudyRescueNotifier(),
    private val interventionTimeoutMs: Long = 30_000L
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val sessionLock = Any()

    val stateMachine = StudyRescueStateMachine()

    // ── Public Observable State ─────────────────────────────────────────────

    val stateFlow: StateFlow<StudyRescueState> = stateMachine.stateFlow
    val snapshotFlow: StateFlow<StudySessionSnapshot> = stateMachine.snapshotFlow

    private val _activeSessionFlow = MutableStateFlow<StudySessionEntity?>(null)
    val activeSessionFlow: StateFlow<StudySessionEntity?> = _activeSessionFlow.asStateFlow()

    private val _remainingSecondsFlow = MutableStateFlow(0L)
    val remainingSecondsFlow: StateFlow<Long> = _remainingSecondsFlow.asStateFlow()

    private val _elapsedSecondsFlow = MutableStateFlow(0L)
    val elapsedSecondsFlow: StateFlow<Long> = _elapsedSecondsFlow.asStateFlow()

    private var tickerJob: Job? = null
    private var interventionTimeoutJob: Job? = null

    init {
        restoreActiveSession()
    }

    /**
     * Rehydrates an active, uncompleted session from Room database.
     * Prevents session loss across activity recreation, rotation, or process restarts.
     */
    fun restoreActiveSession() {
        scope.launch {
            val session = repository.getActiveSession() ?: return@launch
            val ignoredStrikes = repository.getIgnoredInterventionsCount(session.sessionId)

            synchronized(sessionLock) {
                _activeSessionFlow.value = session

                val plannedSeconds = session.plannedDurationMillis / 1000L
                val isPaused = session.currentState == "SESSION_PAUSED"

                // If paused, elapsed time is frozen at the moment of pause (updatedAt)
                val elapsedSoFar = if (isPaused) {
                    ((session.updatedAt - session.startTimeMillis) / 1000L).coerceAtLeast(0L)
                } else {
                    ((System.currentTimeMillis() - session.startTimeMillis) / 1000L).coerceAtLeast(0L)
                }

                val remaining = (plannedSeconds - elapsedSoFar).coerceAtLeast(0L)
                _elapsedSecondsFlow.value = elapsedSoFar
                _remainingSecondsFlow.value = remaining

                val config = StudySessionConfig(
                    sessionId = session.sessionId,
                    taskTitle = session.taskTitle,
                    plannedDurationMinutes = (session.plannedDurationMillis / 60000L).toInt(),
                    startTimestamp = session.startTimeMillis,
                    selectedDistractingApps = session.selectedRestrictedAppPackages.toSet()
                )

                val targetState = try {
                    StudyRescueState.valueOf(session.currentState)
                } catch (e: Exception) {
                    StudyRescueState.SESSION_ACTIVE
                }

                // Deterministically rehydrate state machine
                stateMachine.restoreSession(
                    config = config,
                    targetState = targetState,
                    distractions = 0,
                    ignoredInterventions = ignoredStrikes
                )

                if (!isPaused && remaining > 0L) {
                    startTicker()
                } else if (remaining <= 0L && !isPaused) {
                    // Session finished while app was closed
                    completeSession()
                }
            }
        }
    }

    // ── Session Actions ─────────────────────────────────────────────────────

    /**
     * Starts a new study session. Prevents multiple concurrent active sessions.
     */
    fun startSession(
        taskTitle: String,
        durationMinutes: Int,
        selectedApps: Set<String>
    ): Boolean = synchronized(sessionLock) {
        if (isSessionActive()) {
            return false // Prevent duplicate active sessions
        }

        val sanitizedApps = sanitizePackageNames(selectedApps)
        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val plannedMillis = durationMinutes * 60 * 1000L

        val config = StudySessionConfig(
            sessionId = sessionId,
            taskTitle = taskTitle.ifBlank { "Deep Focus" },
            plannedDurationMinutes = durationMinutes,
            startTimestamp = now,
            selectedDistractingApps = sanitizedApps
        )

        val started = stateMachine.startSession(config)
        if (!started) return false

        val entity = StudySessionEntity(
            sessionId = sessionId,
            taskTitle = config.taskTitle,
            plannedDurationMillis = plannedMillis,
            startTimeMillis = now,
            currentState = "SESSION_ACTIVE",
            selectedRestrictedAppPackages = sanitizedApps.toList(),
            createdAt = now,
            updatedAt = now
        )

        _activeSessionFlow.value = entity
        _remainingSecondsFlow.value = durationMinutes * 60L
        _elapsedSecondsFlow.value = 0L

        scope.launch {
            repository.insertSession(entity)
        }

        startTicker()
        return true
    }

    /**
     * Pauses the active session. Halts the ticker and records pause timestamp.
     * Elapsed time does NOT continue while paused.
     */
    fun pauseSession(): Boolean = synchronized(sessionLock) {
        val now = System.currentTimeMillis()
        val paused = stateMachine.pauseSession(now)
        if (!paused) return false

        stopTicker()
        interventionTimeoutJob?.cancel()
        interventionTimeoutJob = null

        val sessionId = _activeSessionFlow.value?.sessionId
        if (sessionId != null) {
            notifier.cancelNotification(sessionId)
        }

        updateSessionStateInDb("SESSION_PAUSED", now)
        return true
    }

    /**
     * Resumes a paused session.
     * Shifts startTimeMillis forward by the paused duration so elapsed time is exact.
     */
    fun resumeSession(): Boolean = synchronized(sessionLock) {
        val now = System.currentTimeMillis()
        val session = _activeSessionFlow.value ?: return false

        val resumed = stateMachine.resumeSession(now)
        if (!resumed) return false

        // Calculate how long session was paused and shift startTimeMillis forward
        val pauseDuration = (now - session.updatedAt).coerceAtLeast(0L)
        val adjustedStartTime = session.startTimeMillis + pauseDuration

        val updated = session.copy(
            startTimeMillis = adjustedStartTime,
            currentState = "SESSION_ACTIVE",
            updatedAt = now
        )
        _activeSessionFlow.value = updated

        scope.launch {
            repository.updateSession(updated)
        }

        startTicker()
        return true
    }

    /**
     * Completes an active or paused session.
     */
    fun completeSession(): Boolean = synchronized(sessionLock) {
        val now = System.currentTimeMillis()
        val completed = stateMachine.completeSession(now)
        if (!completed) return false

        stopTicker()
        interventionTimeoutJob?.cancel()
        interventionTimeoutJob = null

        val sessionId = _activeSessionFlow.value?.sessionId
        _activeSessionFlow.value = null

        if (sessionId != null) {
            notifier.cancelNotification(sessionId)
            scope.launch {
                repository.completeSession(sessionId, now)
            }
        }
        return true
    }

    /**
     * Cancels / abandons an ongoing session.
     */
    fun cancelSession(): Boolean = synchronized(sessionLock) {
        val now = System.currentTimeMillis()
        val cancelled = stateMachine.cancelSession(now)
        if (!cancelled) return false

        stopTicker()
        interventionTimeoutJob?.cancel()
        interventionTimeoutJob = null

        val sessionId = _activeSessionFlow.value?.sessionId
        _activeSessionFlow.value = null

        if (sessionId != null) {
            notifier.cancelNotification(sessionId)
            scope.launch {
                repository.cancelSession(sessionId, now)
            }
        }
        return true
    }

    /**
     * Records a behavioral distraction event (e.g. restricted app opened or rapid app switching).
     * Only processes when a session is currently active and not paused.
     * Escalates to Intervention Stage 1 or Stage 2, saves intervention in Room, and fires notification.
     */
    fun onDistractionDetected(
        packageName: String? = null,
        reason: DistractionReason = DistractionReason.RESTRICTED_APP_OPENED,
        timestamp: Long = System.currentTimeMillis()
    ): Boolean = synchronized(sessionLock) {
        if (!isSessionActive() || stateMachine.state == StudyRescueState.SESSION_PAUSED) {
            return false
        }

        val session = _activeSessionFlow.value ?: return false

        // 1. Delegate distraction event to state machine (handles debouncing & timeout check)
        val detected = stateMachine.onDistractionDetected(packageName, timestamp)
        if (!detected) return false

        // 2. If state machine is now in DISTRACTION_DETECTED, trigger intervention
        if (stateMachine.state == StudyRescueState.DISTRACTION_DETECTED) {
            val triggered = stateMachine.triggerIntervention(timestamp)
            if (triggered) {
                val newState = stateMachine.state
                if (newState == StudyRescueState.INTERVENTION_ONE_PENDING ||
                    newState == StudyRescueState.INTERVENTION_TWO_PENDING
                ) {
                    val interventionNumber = if (newState == StudyRescueState.INTERVENTION_ONE_PENDING) 1 else 2
                    val isEscalated = (interventionNumber == 2)
                    val interventionId = UUID.randomUUID().toString()

                    val intervention = InterventionRecord(
                        interventionId = interventionId,
                        sessionId = session.sessionId,
                        interventionNumber = interventionNumber,
                        interventionType = if (isEscalated) "STAGE_2_ESCALATED" else "STAGE_1_GENTLE",
                        triggeredAt = timestamp,
                        timeoutAt = timestamp + interventionTimeoutMs
                    )

                    val updatedSession = session.copy(
                        currentState = newState.name,
                        updatedAt = timestamp
                    )
                    _activeSessionFlow.value = updatedSession

                    scope.launch {
                        repository.recordInterventionAndAdvanceSession(updatedSession, intervention)
                    }

                    notifier.showInterventionNotification(
                        sessionId = session.sessionId,
                        interventionNumber = interventionNumber,
                        taskTitle = session.taskTitle,
                        isEscalated = isEscalated
                    )

                    startInterventionTimeoutJob(session.sessionId, interventionNumber, timestamp)
                    return true
                } else if (newState == StudyRescueState.FOCUS_RESCUE_READY) {
                    updateSessionStateInDb("FOCUS_RESCUE_READY", timestamp)
                    return true
                }
            }
        }

        // State machine updated (e.g. distractionCount incremented during active intervention)
        updateSessionStateInDb(stateMachine.state.name, timestamp)
        return true
    }

    // ── Intervention Notification Responses ─────────────────────────────────

    fun onInterventionResumed(sessionId: String, interventionNumber: Int): Boolean = synchronized(sessionLock) {
        val currentSession = _activeSessionFlow.value
        if (currentSession?.sessionId != sessionId) return false

        interventionTimeoutJob?.cancel()
        interventionTimeoutJob = null

        val now = System.currentTimeMillis()
        val success = stateMachine.resumeStudying(now)
        if (!success) return false

        val updated = currentSession.copy(
            currentState = stateMachine.state.name,
            updatedAt = now
        )
        _activeSessionFlow.value = updated

        scope.launch {
            repository.recordInterventionResponse(
                sessionId = sessionId,
                interventionNumber = interventionNumber,
                response = "RESUMED",
                ignored = false,
                respondedAt = now,
                newSessionState = stateMachine.state.name
            )
        }

        notifier.cancelNotification(sessionId)
        return true
    }

    fun onInterventionTakeBreak(sessionId: String, interventionNumber: Int): Boolean = synchronized(sessionLock) {
        val currentSession = _activeSessionFlow.value
        if (currentSession?.sessionId != sessionId) return false

        interventionTimeoutJob?.cancel()
        interventionTimeoutJob = null
        stopTicker()

        val now = System.currentTimeMillis()
        val success = stateMachine.takeBreak(now)
        if (!success) return false

        val updated = currentSession.copy(
            currentState = stateMachine.state.name,
            updatedAt = now
        )
        _activeSessionFlow.value = updated

        scope.launch {
            repository.recordInterventionResponse(
                sessionId = sessionId,
                interventionNumber = interventionNumber,
                response = "TAKE_BREAK",
                ignored = false,
                respondedAt = now,
                newSessionState = stateMachine.state.name
            )
        }

        notifier.cancelNotification(sessionId)
        return true
    }

    fun onInterventionDismissed(sessionId: String, interventionNumber: Int): Boolean = synchronized(sessionLock) {
        val currentSession = _activeSessionFlow.value
        if (currentSession?.sessionId != sessionId) return false

        interventionTimeoutJob?.cancel()
        interventionTimeoutJob = null

        val now = System.currentTimeMillis()
        val success = stateMachine.dismissIntervention(now)
        if (!success) return false

        val updated = currentSession.copy(
            currentState = stateMachine.state.name,
            updatedAt = now
        )
        _activeSessionFlow.value = updated

        scope.launch {
            repository.recordInterventionResponse(
                sessionId = sessionId,
                interventionNumber = interventionNumber,
                response = "DISMISSED",
                ignored = true,
                respondedAt = now,
                newSessionState = stateMachine.state.name
            )
        }

        notifier.cancelNotification(sessionId)
        return true
    }

    private fun startInterventionTimeoutJob(
        sessionId: String,
        interventionNumber: Int,
        triggeredAt: Long
    ) {
        interventionTimeoutJob?.cancel()
        interventionTimeoutJob = scope.launch {
            delay(interventionTimeoutMs)
            synchronized(sessionLock) {
                val currentSession = _activeSessionFlow.value
                if (currentSession?.sessionId != sessionId) return@synchronized

                val expectedState = if (interventionNumber == 1) {
                    StudyRescueState.INTERVENTION_ONE_PENDING
                } else {
                    StudyRescueState.INTERVENTION_TWO_PENDING
                }

                if (stateMachine.state == expectedState) {
                    val now = System.currentTimeMillis()
                    stateMachine.checkInterventionTimeout(now)
                    val nextState = stateMachine.state

                    val updatedSession = currentSession.copy(
                        currentState = nextState.name,
                        updatedAt = now
                    )
                    _activeSessionFlow.value = updatedSession

                    scope.launch {
                        repository.recordInterventionResponse(
                            sessionId = sessionId,
                            interventionNumber = interventionNumber,
                            response = "TIMEOUT",
                            ignored = true,
                            respondedAt = now,
                            newSessionState = nextState.name
                        )
                    }

                    notifier.cancelNotification(sessionId)
                }
            }
        }
    }

    // ── Internal Helpers ────────────────────────────────────────────────────

    fun isSessionActive(): Boolean {
        val s = stateMachine.state
        return s != StudyRescueState.IDLE &&
                s != StudyRescueState.SESSION_COMPLETED &&
                s != StudyRescueState.SESSION_CANCELLED
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val session = _activeSessionFlow.value ?: break
                if (stateMachine.state == StudyRescueState.SESSION_PAUSED) continue

                val now = System.currentTimeMillis()
                val totalPlannedSec = session.plannedDurationMillis / 1000L
                val elapsedSec = ((now - session.startTimeMillis) / 1000L).coerceAtLeast(0L)
                val remainingSec = (totalPlannedSec - elapsedSec).coerceAtLeast(0L)

                _elapsedSecondsFlow.value = elapsedSec
                _remainingSecondsFlow.value = remainingSec

                // Auto-complete if planned duration reached
                if (remainingSec <= 0L && totalPlannedSec > 0L) {
                    completeSession()
                    break
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun updateSessionStateInDb(newState: String, timestamp: Long) {
        val session = _activeSessionFlow.value ?: return
        val updated = session.copy(
            currentState = newState,
            updatedAt = timestamp
        )
        _activeSessionFlow.value = updated
        scope.launch {
            repository.updateSession(updated)
        }
    }

    /**
     * Strips invalid, blank, or protected emergency/system packages.
     * Guarantees emergency dialer, settings, and system UI can never be marked restricted.
     */
    private fun sanitizePackageNames(apps: Set<String>): Set<String> {
        return apps
            .map { it.trim() }
            .filter { pkg ->
                pkg.isNotEmpty() &&
                pkg.contains(".") &&
                !isProtectedPackage(pkg)
            }
            .toSet()
    }

    private fun isProtectedPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower == "com.android.phone" ||
                lower == "com.google.android.dialer" ||
                lower == "com.samsung.android.dialer" ||
                lower == "com.android.server.telecom" ||
                lower == "com.android.systemui" ||
                lower == "android" ||
                lower == "com.android.settings" ||
                lower == "com.example.myapplication" ||
                lower.contains("emergency") ||
                lower.contains("launcher") ||
                lower.contains("keyboard") ||
                lower.contains("inputmethod")
    }

    companion object {
        @Volatile
        private var INSTANCE: StudySessionManager? = null

        fun getInstance(context: Context): StudySessionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StudySessionManager(
                    repository = StudyRescueRepository(BehaviorDatabase.getDatabase(context.applicationContext).studyRescueDao()),
                    notifier = AndroidStudyRescueNotifier(context.applicationContext)
                ).also {
                    INSTANCE = it
                }
            }
        }
    }
}
