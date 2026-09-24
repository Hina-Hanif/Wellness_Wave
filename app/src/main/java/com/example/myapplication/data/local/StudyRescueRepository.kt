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

    suspend fun escalateToFocusRescue(sessionId: String, startTime: Long): Boolean = withContext(Dispatchers.IO) {
        studyRescueDao.escalateToFocusRescue(sessionId, startTime)
    }

    suspend fun exitFocusRescue(sessionId: String, endTime: Long, reason: String): Boolean = withContext(Dispatchers.IO) {
        studyRescueDao.exitFocusRescue(sessionId, endTime, reason)
    }

    suspend fun completeSession(sessionId: String, endTime: Long): Boolean = withContext(Dispatchers.IO) {
        studyRescueDao.completeSession(sessionId, endTime)
    }

    suspend fun cancelSession(sessionId: String, cancelTime: Long): Boolean = withContext(Dispatchers.IO) {
        studyRescueDao.cancelSession(sessionId, cancelTime)
    }
}
