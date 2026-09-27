package com.example.myapplication.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repository mediating access to persistent Study Rescue data.
 */
class StudyRescueRepository(private val studyRescueDao: StudyRescueDao) {

    suspend fun insertSession(session: StudySessionEntity) = withContext(Dispatchers.IO) {
        studyRescueDao.insertSession(session)
    }

    suspend fun updateSession(session: StudySessionEntity) = withContext(Dispatchers.IO) {
        studyRescueDao.updateSession(session)
    }

    suspend fun getSessionById(sessionId: String): StudySessionEntity? = withContext(Dispatchers.IO) {
        studyRescueDao.getSessionById(sessionId)
    }

    suspend fun getActiveSession(): StudySessionEntity? = withContext(Dispatchers.IO) {
        studyRescueDao.getActiveSession()
    }

    fun getAllSessionsFlow(): Flow<List<StudySessionEntity>> {
        return studyRescueDao.getAllSessionsFlow()
    }

    suspend fun getRecentSessions(limit: Int): List<StudySessionEntity> = withContext(Dispatchers.IO) {
        studyRescueDao.getRecentSessions(limit)
    }

    suspend fun getSessionsBetween(startMillis: Long, endMillis: Long): List<StudySessionEntity> = withContext(Dispatchers.IO) {
        studyRescueDao.getSessionsBetween(startMillis, endMillis)
    }

    suspend fun getAllSessions(): List<StudySessionEntity> = withContext(Dispatchers.IO) {
        studyRescueDao.getAllSessions()
    }

    suspend fun insertIntervention(intervention: InterventionRecord): Long = withContext(Dispatchers.IO) {
        studyRescueDao.insertIntervention(intervention)
    }

    suspend fun getInterventionsForSession(sessionId: String): List<InterventionRecord> = withContext(Dispatchers.IO) {
        studyRescueDao.getInterventionsForSession(sessionId)
    }

    suspend fun getInterventionByNumber(sessionId: String, number: Int): InterventionRecord? = withContext(Dispatchers.IO) {
        studyRescueDao.getInterventionByNumber(sessionId, number)
    }

    suspend fun getIgnoredInterventionsCount(sessionId: String): Int = withContext(Dispatchers.IO) {
        studyRescueDao.getIgnoredInterventionsCount(sessionId)
    }

    suspend fun getInterventionsBetween(startMillis: Long, endMillis: Long): List<InterventionRecord> = withContext(Dispatchers.IO) {
        studyRescueDao.getInterventionsBetween(startMillis, endMillis)
    }

    suspend fun getAllInterventions(): List<InterventionRecord> = withContext(Dispatchers.IO) {
        studyRescueDao.getAllInterventions()
    }

    fun getAllInterventionsFlow(): Flow<List<InterventionRecord>> {
        return studyRescueDao.getAllInterventionsFlow()
    }

    fun getAnalyticsFlow(timeRangeFlow: Flow<com.example.myapplication.data.analysis.AnalyticsTimeRange>): Flow<com.example.myapplication.data.analysis.StudyRescueAnalyticsSummary> {
        return kotlinx.coroutines.flow.combine(
            getAllSessionsFlow(),
            getAllInterventionsFlow(),
            timeRangeFlow
        ) { sessions, interventions, range ->
            com.example.myapplication.data.analysis.StudyRescueAnalyticsEngine.calculateSummary(
                allSessions = sessions,
                allInterventions = interventions,
                timeRange = range
            )
        }
    }

    suspend fun recordInterventionAndAdvanceSession(
        session: StudySessionEntity,
        intervention: InterventionRecord
    ): Boolean = withContext(Dispatchers.IO) {
        studyRescueDao.recordInterventionAndAdvanceSession(session, intervention)
    }

    suspend fun recordInterventionResponse(
        sessionId: String,
        interventionNumber: Int,
        response: String,
        ignored: Boolean,
        respondedAt: Long,
        newSessionState: String
    ): Boolean = withContext(Dispatchers.IO) {
        studyRescueDao.recordInterventionResponse(
            sessionId,
            interventionNumber,
            response,
            ignored,
            respondedAt,
            newSessionState
        )
    }

    suspend fun escalateToFocusRescue(
        sessionId: String,
        startTime: Long,
        endTime: Long? = null
    ): Boolean = withContext(Dispatchers.IO) {
        studyRescueDao.escalateToFocusRescue(sessionId, startTime, endTime)
    }

    suspend fun exitFocusRescue(
        sessionId: String,
        endTime: Long,
        reason: String,
        cooldownUntil: Long? = null
    ): Boolean = withContext(Dispatchers.IO) {
        studyRescueDao.exitFocusRescue(sessionId, endTime, reason, cooldownUntil)
    }

    suspend fun completeFocusRescue(
        sessionId: String,
        completedAt: Long,
        cooldownUntil: Long? = null
    ): Boolean = withContext(Dispatchers.IO) {
        studyRescueDao.completeFocusRescue(sessionId, completedAt, cooldownUntil)
    }

    suspend fun completeSession(sessionId: String, endTime: Long): Boolean = withContext(Dispatchers.IO) {
        studyRescueDao.completeSession(sessionId, endTime)
    }

    suspend fun cancelSession(sessionId: String, cancelTime: Long): Boolean = withContext(Dispatchers.IO) {
        studyRescueDao.cancelSession(sessionId, cancelTime)
    }
}
