package com.example.myapplication.data.tracking

import com.example.myapplication.data.local.StudyRescueRepository
import com.example.myapplication.data.local.StudySessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Authoritative persisted timestamp model for Focus Rescue.
 *
 * Implements:
 *   endTimeMillis = startTimeMillis + plannedDurationMillis
 *   remainingMillis = max(0, endTimeMillis - currentTimeMillis)
 */
data class FocusRescueTimestamps(
    val startTimeMillis: Long,
    val plannedDurationMillis: Long,
    val endTimeMillis: Long
) {
    /**
     * Calculates remaining time in milliseconds using absolute timestamp math.
     * Guaranteed to never be negative.
     */
    fun calculateRemainingMillis(currentTimeMillis: Long = System.currentTimeMillis()): Long {
        return (endTimeMillis - currentTimeMillis).coerceAtLeast(0L)
    }

    /**
     * Determines whether the planned Focus Rescue duration has elapsed.
     */
    fun isExpired(currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
        return currentTimeMillis >= endTimeMillis
    }
}

/**
 * Result returned when attempting to start Focus Rescue.
 */
sealed class FocusRescueStartResult {
    data class Success(val timestamps: FocusRescueTimestamps) : FocusRescueStartResult()
    object AlreadyActive : FocusRescueStartResult()
    object NoActiveSession : FocusRescueStartResult()
    object NotEligible : FocusRescueStartResult()
    data class CooldownActive(val remainingCooldownMillis: Long) : FocusRescueStartResult()
    object SessionPaused : FocusRescueStartResult()
}

/**
 * Focus Rescue lifecycle states representing the lifecycle of an escalation mode.
 */
enum class FocusRescueLifecycleState {
    INACTIVE,
    ACTIVE,
    COMPLETED,
    EXITED,
    CANCELLED
}

/**
 * Interface defining the Focus Rescue Controller contract.
 */
interface FocusRescueController {
    val isFocusRescueActive: Boolean
    val timestampsFlow: StateFlow<FocusRescueTimestamps?>
    val currentTimestamps: FocusRescueTimestamps?
    val lifecycleStateFlow: StateFlow<FocusRescueLifecycleState>
    val lifecycleState: FocusRescueLifecycleState

    /**
     * Starts Focus Rescue using the active session's planned duration.
     * Persists authoritative start and end timestamps to Room database.
     * Prevents duplicate instances and enforces cooldown.
     */
    fun startFocusRescue(timestamp: Long = System.currentTimeMillis()): FocusRescueStartResult

    /**
     * Rehydrates Focus Rescue state from a persisted session entity.
     * Used after process restart, activity recreation, or app reopen.
     */
    fun restoreFromSession(session: StudySessionEntity, currentTimeMillis: Long = System.currentTimeMillis()): Boolean

    /**
     * Calculates the exact remaining time using absolute timestamps:
     * remainingMillis = max(0, endTimeMillis - currentTimeMillis)
     */
    fun getRemainingMillis(currentTimeMillis: Long = System.currentTimeMillis()): Long

    /**
     * Checks if the timer has expired. If expired, immediately and idempotently completes Focus Rescue.
     */
    fun checkAndHandleExpiration(currentTimeMillis: Long = System.currentTimeMillis()): Boolean

    /**
     * Exits Focus Rescue early upon user request.
     * Persists the exit reason, lifts application restrictions, returns session to standard active study mode,
     * and sets cooldown.
     */
    fun exitFocusRescue(reason: String = "USER_EXITED", timestamp: Long = System.currentTimeMillis()): Boolean

    /**
     * Completes Focus Rescue idempotently when remaining time reaches 0.
     * Does NOT mark the entire study session as completed.
     */
    fun completeFocusRescue(timestamp: Long = System.currentTimeMillis()): Boolean

    /**
     * Checks if Focus Rescue is currently in cooldown.
     */
    fun isInCooldown(currentTimeMillis: Long = System.currentTimeMillis()): Boolean

    /**
     * Returns remaining cooldown in milliseconds, or 0 if cooldown has passed.
     */
    fun getRemainingCooldownMillis(currentTimeMillis: Long = System.currentTimeMillis()): Long

    /**
     * Clears Focus Rescue state and timestamps when session is completed or cancelled.
     */
    fun clearFocusRescue()
}

/**
 * Thread-safe, production implementation of FocusRescueController.
 * Coordinates state machine, Room persistence, and absolute timestamp calculation.
 */
class DefaultFocusRescueController(
    private val sessionManagerProvider: () -> StudySessionManager,
    private val repository: StudyRescueRepository,
    private val cooldownDurationMs: Long = DEFAULT_COOLDOWN_MS,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : FocusRescueController {

    private val lock = Any()

    private val _timestampsFlow = MutableStateFlow<FocusRescueTimestamps?>(null)
    override val timestampsFlow: StateFlow<FocusRescueTimestamps?> = _timestampsFlow.asStateFlow()

    private val _lifecycleStateFlow = MutableStateFlow(FocusRescueLifecycleState.INACTIVE)
    override val lifecycleStateFlow: StateFlow<FocusRescueLifecycleState> = _lifecycleStateFlow.asStateFlow()

    override val lifecycleState: FocusRescueLifecycleState
        get() = synchronized(lock) { _lifecycleStateFlow.value }

    override val currentTimestamps: FocusRescueTimestamps?
        get() = synchronized(lock) { _timestampsFlow.value }

    override val isFocusRescueActive: Boolean
        get() = synchronized(lock) {
            val sessionManager = sessionManagerProvider()
            sessionManager.stateMachine.state == StudyRescueState.FOCUS_RESCUE_ACTIVE &&
                    _timestampsFlow.value != null
        }

    override fun clearFocusRescue() = synchronized(lock) {
        _timestampsFlow.value = null
        _lifecycleStateFlow.value = FocusRescueLifecycleState.INACTIVE
    }

    override fun startFocusRescue(timestamp: Long): FocusRescueStartResult = synchronized(lock) {
        val sessionManager = sessionManagerProvider()
        val session = sessionManager.activeSessionFlow.value
            ?: return FocusRescueStartResult.NoActiveSession

        // 1. Prevent duplicate start requests (Rule 9)
        if (isFocusRescueActive || sessionManager.stateMachine.state == StudyRescueState.FOCUS_RESCUE_ACTIVE) {
            return FocusRescueStartResult.AlreadyActive
        }

        // 2. Reject if session is paused
        if (sessionManager.stateMachine.state == StudyRescueState.SESSION_PAUSED) {
            return FocusRescueStartResult.SessionPaused
        }

        // 3. Cooldown check (Rule 11)
        if (isInCooldown(timestamp)) {
            val remainingCooldown = getRemainingCooldownMillis(timestamp)
            return FocusRescueStartResult.CooldownActive(remainingCooldown)
        }

        // 4. Eligibility check: Focus Rescue requires state machine readiness (2 ignored strikes)
        if (sessionManager.stateMachine.state != StudyRescueState.FOCUS_RESCUE_READY) {
            return FocusRescueStartResult.NotEligible
        }

        // 5. Authoritative Timestamp Model (Rules 1 & 2):
        // Duration MUST come from the current study session. Never hardcoded.
        val startTimeMillis = timestamp
        val plannedDurationMillis = session.plannedDurationMillis
        val endTimeMillis = startTimeMillis + plannedDurationMillis
        val timestamps = FocusRescueTimestamps(startTimeMillis, plannedDurationMillis, endTimeMillis)

        // 6. Transition state machine
        val started = sessionManager.stateMachine.startFocusRescue(timestamp)
        if (!started) return FocusRescueStartResult.NotEligible

        // 7. Persist authoritative timestamps to Room (Rule 4)
        val updatedSession = session.copy(
            currentState = "FOCUS_RESCUE_ACTIVE",
            focusRescueStartTime = startTimeMillis,
            focusRescueEndTime = endTimeMillis,
            focusRescueState = "ACTIVE",
            updatedAt = timestamp
        )
        sessionManager.updateActiveSessionDirectly(updatedSession)
        _timestampsFlow.value = timestamps
        _lifecycleStateFlow.value = FocusRescueLifecycleState.ACTIVE

        scope.launch {
            repository.escalateToFocusRescue(session.sessionId, startTimeMillis, endTimeMillis)
        }

        return FocusRescueStartResult.Success(timestamps)
    }

    override fun restoreFromSession(session: StudySessionEntity, currentTimeMillis: Long): Boolean = synchronized(lock) {
        if (session.currentState != "FOCUS_RESCUE_ACTIVE") {
            _timestampsFlow.value = null
            _lifecycleStateFlow.value = when (session.focusRescueState) {
                "COMPLETED" -> FocusRescueLifecycleState.COMPLETED
                "EXITED" -> FocusRescueLifecycleState.EXITED
                "CANCELLED" -> FocusRescueLifecycleState.CANCELLED
                else -> FocusRescueLifecycleState.INACTIVE
            }
            return false
        }

        val startTime = session.focusRescueStartTime ?: session.startTimeMillis
        val plannedDuration = session.plannedDurationMillis
        val endTime = session.focusRescueEndTime ?: (startTime + plannedDuration)

        val timestamps = FocusRescueTimestamps(startTime, plannedDuration, endTime)

        // 8. If the end time has passed during app closure, complete Focus Rescue immediately (Rule 6)
        if (timestamps.isExpired(currentTimeMillis)) {
            _timestampsFlow.value = timestamps
            completeFocusRescue(currentTimeMillis)
            return false
        }

        _timestampsFlow.value = timestamps
        _lifecycleStateFlow.value = FocusRescueLifecycleState.ACTIVE
        return true
    }

    override fun getRemainingMillis(currentTimeMillis: Long): Long = synchronized(lock) {
        val ts = _timestampsFlow.value ?: return 0L
        return ts.calculateRemainingMillis(currentTimeMillis)
    }

    override fun checkAndHandleExpiration(currentTimeMillis: Long): Boolean = synchronized(lock) {
        val ts = _timestampsFlow.value ?: return false
        if (ts.isExpired(currentTimeMillis)) {
            completeFocusRescue(currentTimeMillis)
            return true
        }
        return false
    }

    override fun exitFocusRescue(reason: String, timestamp: Long): Boolean = synchronized(lock) {
        val sessionManager = sessionManagerProvider()
        val session = sessionManager.activeSessionFlow.value ?: return false

        // Check if Focus Rescue was actually active
        if (sessionManager.stateMachine.state != StudyRescueState.FOCUS_RESCUE_ACTIVE) {
            return false
        }

        val cooldownUntil = timestamp + cooldownDurationMs

        // Transition state machine back to standard study session (Rule 7)
        val exited = sessionManager.stateMachine.exitFocusRescue(timestamp)
        if (!exited) return false

        _timestampsFlow.value = null
        _lifecycleStateFlow.value = FocusRescueLifecycleState.EXITED

        val updated = session.copy(
            currentState = "SESSION_ACTIVE",
            focusRescueEndTime = timestamp,
            focusRescueState = "EXITED",
            focusRescueExitReason = reason,
            focusRescueCompletedAt = timestamp,
            cooldownUntil = cooldownUntil,
            updatedAt = timestamp
        )
        sessionManager.updateActiveSessionDirectly(updated)

        scope.launch {
            repository.exitFocusRescue(session.sessionId, timestamp, reason, cooldownUntil)
        }

        return true
    }

    override fun completeFocusRescue(timestamp: Long): Boolean = synchronized(lock) {
        val sessionManager = sessionManagerProvider()
        val session = sessionManager.activeSessionFlow.value

        // Rule 5: Completion must be idempotent
        if (session == null || sessionManager.stateMachine.state != StudyRescueState.FOCUS_RESCUE_ACTIVE) {
            _timestampsFlow.value = null
            _lifecycleStateFlow.value = FocusRescueLifecycleState.COMPLETED
            return true // Idempotent success
        }

        val cooldownUntil = timestamp + cooldownDurationMs

        // Transition state machine: FOCUS_RESCUE_ACTIVE -> SESSION_ACTIVE
        sessionManager.stateMachine.exitFocusRescue(timestamp)
        _timestampsFlow.value = null
        _lifecycleStateFlow.value = FocusRescueLifecycleState.COMPLETED

        // Rule 8: Focus Rescue completion must NOT automatically mark the entire study session as completed.
        // It returns to SESSION_ACTIVE with restrictions ended.
        val updated = session.copy(
            currentState = "SESSION_ACTIVE",
            focusRescueEndTime = timestamp,
            focusRescueState = "COMPLETED",
            focusRescueExitReason = "COMPLETED",
            focusRescueCompletedAt = timestamp,
            cooldownUntil = cooldownUntil,
            updatedAt = timestamp
        )
        sessionManager.updateActiveSessionDirectly(updated)

        scope.launch {
            repository.completeFocusRescue(session.sessionId, timestamp, cooldownUntil)
        }

        return true
    }

    override fun isInCooldown(currentTimeMillis: Long): Boolean = synchronized(lock) {
        val session = sessionManagerProvider().activeSessionFlow.value ?: return false
        val cooldown = session.cooldownUntil ?: return false
        return currentTimeMillis < cooldown
    }

    override fun getRemainingCooldownMillis(currentTimeMillis: Long): Long = synchronized(lock) {
        val session = sessionManagerProvider().activeSessionFlow.value ?: return 0L
        val cooldown = session.cooldownUntil ?: return 0L
        return (cooldown - currentTimeMillis).coerceAtLeast(0L)
    }

    companion object {
        const val DEFAULT_COOLDOWN_MS = 5 * 60 * 1000L // 5 minutes default cooldown
    }
}
