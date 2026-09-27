package com.example.myapplication.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object for Study Rescue sessions and intervention events.
 */
@Dao
interface StudyRescueDao {

    // ── Session CRUD ────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: StudySessionEntity)

    @Update
    suspend fun updateSession(session: StudySessionEntity)

    @Query("SELECT * FROM study_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): StudySessionEntity?

    @Query("SELECT * FROM study_sessions WHERE currentState NOT IN ('SESSION_COMPLETED', 'SESSION_CANCELLED') ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getActiveSession(): StudySessionEntity?

    @Query("SELECT * FROM study_sessions ORDER BY createdAt DESC")
    fun getAllSessionsFlow(): Flow<List<StudySessionEntity>>

    @Query("SELECT * FROM study_sessions ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentSessions(limit: Int): List<StudySessionEntity>

    @Query("SELECT * FROM study_sessions WHERE createdAt BETWEEN :startMillis AND :endMillis ORDER BY createdAt ASC")
    suspend fun getSessionsBetween(startMillis: Long, endMillis: Long): List<StudySessionEntity>

    @Query("SELECT * FROM study_sessions ORDER BY createdAt ASC")
    suspend fun getAllSessions(): List<StudySessionEntity>

    // ── Intervention CRUD ───────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIntervention(intervention: InterventionRecord): Long

    @Update
    suspend fun updateIntervention(intervention: InterventionRecord)

    @Query("SELECT * FROM intervention_records WHERE sessionId = :sessionId ORDER BY interventionNumber ASC")
    suspend fun getInterventionsForSession(sessionId: String): List<InterventionRecord>

    @Query("SELECT * FROM intervention_records WHERE sessionId = :sessionId AND interventionNumber = :number LIMIT 1")
    suspend fun getInterventionByNumber(sessionId: String, number: Int): InterventionRecord?

    @Query("SELECT COUNT(*) FROM intervention_records WHERE sessionId = :sessionId AND ignored = 1")
    suspend fun getIgnoredInterventionsCount(sessionId: String): Int

    @Query("SELECT * FROM intervention_records WHERE triggeredAt BETWEEN :startMillis AND :endMillis ORDER BY triggeredAt ASC")
    suspend fun getInterventionsBetween(startMillis: Long, endMillis: Long): List<InterventionRecord>

    @Query("SELECT * FROM intervention_records ORDER BY triggeredAt ASC")
    suspend fun getAllInterventions(): List<InterventionRecord>

    @Query("SELECT * FROM intervention_records ORDER BY triggeredAt DESC")
    fun getAllInterventionsFlow(): Flow<List<InterventionRecord>>

    // ── Atomic Transactions ─────────────────────────────────────────────────

    @Transaction
    suspend fun recordInterventionAndAdvanceSession(
        session: StudySessionEntity,
        intervention: InterventionRecord
    ): Boolean {
        // Prevent duplicate intervention records for the same session and interventionNumber
        val existing = getInterventionByNumber(intervention.sessionId, intervention.interventionNumber)
        if (existing != null) {
            return false // Duplicate rejected
        }

        updateSession(session.copy(updatedAt = System.currentTimeMillis()))
        val insertedRow = insertIntervention(intervention)
        return insertedRow != -1L
    }

    @Transaction
    suspend fun recordInterventionResponse(
        sessionId: String,
        interventionNumber: Int,
        response: String,
        ignored: Boolean,
        respondedAt: Long,
        newSessionState: String
    ): Boolean {
        val intervention = getInterventionByNumber(sessionId, interventionNumber) ?: return false
        val session = getSessionById(sessionId) ?: return false

        updateIntervention(
            intervention.copy(
                response = response,
                ignored = ignored,
                respondedAt = respondedAt
            )
        )

        updateSession(
            session.copy(
                currentState = newSessionState,
                updatedAt = respondedAt
            )
        )
        return true
    }

    @Transaction
    suspend fun escalateToFocusRescue(
        sessionId: String,
        startTime: Long,
        endTime: Long? = null
    ): Boolean {
        val session = getSessionById(sessionId) ?: return false
        val ignoredCount = getIgnoredInterventionsCount(sessionId)
        if (ignoredCount < 2) {
            return false // Focus Rescue requires at least 2 ignored interventions
        }

        val calculatedEndTime = endTime ?: (startTime + session.plannedDurationMillis)

        updateSession(
            session.copy(
                currentState = "FOCUS_RESCUE_ACTIVE",
                focusRescueStartTime = startTime,
                focusRescueEndTime = calculatedEndTime,
                focusRescueState = "ACTIVE",
                updatedAt = startTime
            )
        )
        return true
    }

    @Transaction
    suspend fun exitFocusRescue(
        sessionId: String,
        endTime: Long,
        reason: String,
        cooldownUntil: Long? = null
    ): Boolean {
        val session = getSessionById(sessionId) ?: return false
        if (session.currentState != "FOCUS_RESCUE_ACTIVE") {
            return false
        }
        updateSession(
            session.copy(
                currentState = "SESSION_ACTIVE",
                focusRescueEndTime = endTime,
                focusRescueState = "EXITED",
                focusRescueExitReason = reason,
                focusRescueCompletedAt = endTime,
                cooldownUntil = cooldownUntil,
                updatedAt = endTime
            )
        )
        return true
    }

    @Transaction
    suspend fun completeFocusRescue(
        sessionId: String,
        completedAt: Long,
        cooldownUntil: Long? = null
    ): Boolean {
        val session = getSessionById(sessionId) ?: return false
        if (session.currentState != "FOCUS_RESCUE_ACTIVE") {
            return false
        }
        updateSession(
            session.copy(
                currentState = "SESSION_ACTIVE",
                focusRescueEndTime = completedAt,
                focusRescueState = "COMPLETED",
                focusRescueExitReason = "COMPLETED",
                focusRescueCompletedAt = completedAt,
                cooldownUntil = cooldownUntil,
                updatedAt = completedAt
            )
        )
        return true
    }

    @Transaction
    suspend fun completeSession(
        sessionId: String,
        endTime: Long
    ): Boolean {
        val session = getSessionById(sessionId) ?: return false
        val newFocusRescueState = if (session.currentState == "FOCUS_RESCUE_ACTIVE") "COMPLETED" else session.focusRescueState
        updateSession(
            session.copy(
                currentState = "SESSION_COMPLETED",
                endTimeMillis = endTime,
                focusRescueState = newFocusRescueState,
                updatedAt = endTime
            )
        )
        return true
    }

    @Transaction
    suspend fun cancelSession(
        sessionId: String,
        cancelTime: Long
    ): Boolean {
        val session = getSessionById(sessionId) ?: return false
        val newFocusRescueState = if (session.currentState == "FOCUS_RESCUE_ACTIVE") "CANCELLED" else session.focusRescueState
        updateSession(
            session.copy(
                currentState = "SESSION_CANCELLED",
                endTimeMillis = cancelTime,
                focusRescueState = newFocusRescueState,
                updatedAt = cancelTime
            )
        )
        return true
    }
}
