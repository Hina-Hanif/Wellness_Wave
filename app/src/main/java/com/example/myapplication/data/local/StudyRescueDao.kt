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
        startTime: Long
    ): Boolean {
        val session = getSessionById(sessionId) ?: return false
        val ignoredCount = getIgnoredInterventionsCount(sessionId)
        if (ignoredCount < 2) {
            return false // Focus Rescue requires at least 2 ignored interventions
        }

        updateSession(
            session.copy(
                currentState = "FOCUS_RESCUE_ACTIVE",
                focusRescueStartTime = startTime,
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
        reason: String
    ): Boolean {
        val session = getSessionById(sessionId) ?: return false
        updateSession(
            session.copy(
                currentState = "SESSION_ACTIVE",
                focusRescueEndTime = endTime,
                focusRescueState = "EXITED",
                focusRescueExitReason = reason,
                focusRescueCompletedAt = endTime,
                updatedAt = endTime
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
        updateSession(
            session.copy(
                currentState = "SESSION_COMPLETED",
                endTimeMillis = endTime,
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
        updateSession(
            session.copy(
                currentState = "SESSION_CANCELLED",
                endTimeMillis = cancelTime,
                updatedAt = cancelTime
            )
        )
        return true
    }
}
