package com.example.myapplication.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

class StudyRescuePersistenceTest {

    private lateinit var fakeDao: FakeStudyRescueDao
    private lateinit var repository: StudyRescueRepository

    @Before
    fun setUp() {
        fakeDao = FakeStudyRescueDao()
        repository = StudyRescueRepository(fakeDao)
    }

    // ── 1. Session Insertion Test ───────────────────────────────────────────
    @Test
    fun testSessionInsertion() = runBlocking {
        val session = StudySessionEntity(
            sessionId = "session_101",
            taskTitle = "Kotlin Coroutines Deep Dive",
            plannedDurationMillis = 25 * 60 * 1000L,
            startTimeMillis = 1_000_000L,
            currentState = "SESSION_ACTIVE",
            selectedRestrictedAppPackages = listOf("com.instagram.android", "com.twitter.android")
        )

        repository.insertSession(session)

        val retrieved = repository.getSessionById("session_101")
        assertNotNull(retrieved)
        assertEquals("session_101", retrieved?.sessionId)
        assertEquals("Kotlin Coroutines Deep Dive", retrieved?.taskTitle)
        assertEquals(2, retrieved?.selectedRestrictedAppPackages?.size)
        assertEquals("SESSION_ACTIVE", retrieved?.currentState)
    }

    // ── 2. Session Recovery Test (App Restart Simulation) ───────────────────
    @Test
    fun testSessionRecovery() = runBlocking {
        // Completed session from yesterday
        val oldSession = StudySessionEntity(
            sessionId = "session_old",
            taskTitle = "Old Task",
            plannedDurationMillis = 30 * 60 * 1000L,
            startTimeMillis = 500_000L,
            endTimeMillis = 530_000L,
            currentState = "SESSION_COMPLETED",
            updatedAt = 530_000L
        )
        repository.insertSession(oldSession)

        // Active session before simulated crash / restart
        val activeSession = StudySessionEntity(
            sessionId = "session_active_now",
            taskTitle = "Active Exam Prep",
            plannedDurationMillis = 45 * 60 * 1000L,
            startTimeMillis = 1_000_000L,
            currentState = "SESSION_ACTIVE",
            updatedAt = 1_010_000L
        )
        repository.insertSession(activeSession)

        // Simulate app restart: query active uncompleted session
        val recovered = repository.getActiveSession()
        assertNotNull(recovered)
        assertEquals("session_active_now", recovered?.sessionId)
        assertEquals("Active Exam Prep", recovered?.taskTitle)
        assertEquals("SESSION_ACTIVE", recovered?.currentState)
    }

    // ── 3. Intervention Insertion Test ──────────────────────────────────────
    @Test
    fun testInterventionInsertion() = runBlocking {
        val intervention = InterventionRecord(
            interventionId = "int_1",
            sessionId = "session_101",
            interventionNumber = 1,
            interventionType = "INTERVENTION_ONE",
            triggeredAt = 1_005_000L,
            timeoutAt = 1_035_000L
        )

        val rowId = repository.insertIntervention(intervention)
        assertEquals(1L, rowId)

        val interventions = repository.getInterventionsForSession("session_101")
        assertEquals(1, interventions.size)
        assertEquals(1, interventions[0].interventionNumber)
        assertFalse(interventions[0].ignored)
    }

    // ── 4. Duplicate Intervention Prevention Test ───────────────────────────
    @Test
    fun testDuplicateInterventionPrevention() = runBlocking {
        val session = StudySessionEntity(
            sessionId = "session_dedup",
            taskTitle = "Dedup Test",
            plannedDurationMillis = 30 * 60 * 1000L,
            startTimeMillis = 1_000_000L,
            currentState = "SESSION_ACTIVE"
        )
        repository.insertSession(session)

        val intervention1 = InterventionRecord(
            interventionId = "int_first",
            sessionId = "session_dedup",
            interventionNumber = 1,
            interventionType = "INTERVENTION_ONE",
            triggeredAt = 1_005_000L
        )

        // First insertion succeeds
        val firstResult = repository.recordInterventionAndAdvanceSession(session, intervention1)
        assertTrue(firstResult)

        // Duplicate event with same sessionId and interventionNumber (e.g. rapid accessibility burst)
        val duplicateIntervention = InterventionRecord(
            interventionId = "int_duplicate",
            sessionId = "session_dedup",
            interventionNumber = 1,
            interventionType = "INTERVENTION_ONE",
            triggeredAt = 1_005_100L
        )
        val duplicateResult = repository.recordInterventionAndAdvanceSession(session, duplicateIntervention)

        // Must be rejected
        assertFalse(duplicateResult)

        // Verify only 1 record stored
        val stored = repository.getInterventionsForSession("session_dedup")
        assertEquals(1, stored.size)
        assertEquals("int_first", stored[0].interventionId)
    }

    // ── 5. Two Ignored Interventions Count Test ─────────────────────────────
    @Test
    fun testTwoIgnoredInterventionsCount() = runBlocking {
        val session = StudySessionEntity(
            sessionId = "session_strikes",
            taskTitle = "Strike Escalation Test",
            plannedDurationMillis = 40 * 60 * 1000L,
            startTimeMillis = 1_000_000L,
            currentState = "SESSION_ACTIVE"
        )
        repository.insertSession(session)

        // Strike 1: Ignored
        repository.insertIntervention(
            InterventionRecord(
                interventionId = "int_strike_1",
                sessionId = "session_strikes",
                interventionNumber = 1,
                interventionType = "INTERVENTION_ONE",
                triggeredAt = 1_005_000L,
                respondedAt = 1_035_000L,
                response = "DISMISSED",
                ignored = true
            )
        )

        assertEquals(1, repository.getIgnoredInterventionsCount("session_strikes"))

        // Strike 2: Ignored
        repository.insertIntervention(
            InterventionRecord(
                interventionId = "int_strike_2",
                sessionId = "session_strikes",
                interventionNumber = 2,
                interventionType = "INTERVENTION_TWO",
                triggeredAt = 1_040_000L,
                respondedAt = 1_070_000L,
                response = "IGNORED",
                ignored = true
            )
        )

        assertEquals(2, repository.getIgnoredInterventionsCount("session_strikes"))
    }

    // ── 6. Focus Rescue State Persistence Test ──────────────────────────────
    @Test
    fun testFocusRescueStatePersistence() = runBlocking {
        val session = StudySessionEntity(
            sessionId = "session_fr",
            taskTitle = "Focus Rescue Test",
            plannedDurationMillis = 50 * 60 * 1000L,
            startTimeMillis = 1_000_000L,
            currentState = "FOCUS_RESCUE_READY"
        )
        repository.insertSession(session)

        // Two ignored strikes pre-requisite
        repository.insertIntervention(InterventionRecord("i1", "session_fr", 1, "INT_1", 100L, ignored = true))
        repository.insertIntervention(InterventionRecord("i2", "session_fr", 2, "INT_2", 200L, ignored = true))

        // Escalate to Focus Rescue
        val escalated = repository.escalateToFocusRescue("session_fr", startTime = 1_010_000L)
        assertTrue(escalated)

        var current = repository.getSessionById("session_fr")
        assertEquals("FOCUS_RESCUE_ACTIVE", current?.currentState)
        assertEquals(1_010_000L, current?.focusRescueStartTime)
        assertEquals("ACTIVE", current?.focusRescueState)

        // Exit Focus Rescue
        val exited = repository.exitFocusRescue("session_fr", endTime = 1_030_000L, reason = "USER_REQUESTED")
        assertTrue(exited)

        current = repository.getSessionById("session_fr")
        assertEquals("SESSION_ACTIVE", current?.currentState)
        assertEquals(1_030_000L, current?.focusRescueEndTime)
        assertEquals("EXITED", current?.focusRescueState)
        assertEquals("USER_REQUESTED", current?.focusRescueExitReason)
    }

    // ── 7. Migration Behavior Test (SQL Statements Validation) ──────────────
    @Test
    fun testMigrationBehavior() {
        val executedSql = mutableListOf<String>()

        // Create dynamic proxy for SupportSQLiteDatabase to record SQL executions
        val handler = java.lang.reflect.InvocationHandler { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0] as String)
            }
            null
        }

        val mockDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
            handler
        ) as SupportSQLiteDatabase

        // Execute Room Migration from Version 2 to 3
        BehaviorDatabase.MIGRATION_2_3.migrate(mockDb)

        // Verify that study_sessions and intervention_records were created
        assertTrue(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `study_sessions`") })
        assertTrue(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `intervention_records`") })
        assertTrue(executedSql.any { it.contains("index_intervention_records_sessionId") })
        assertTrue(executedSql.any { it.contains("index_intervention_records_sessionId_interventionNumber") })

        // Verify that existing behavior_records table is NEVER dropped or touched
        assertFalse(executedSql.any { it.contains("DROP TABLE", ignoreCase = true) })
        assertFalse(executedSql.any { it.contains("behavior_records", ignoreCase = true) })
    }

    // ── 8. Transaction Behavior Test ────────────────────────────────────────
    @Test
    fun testTransactionBehavior() = runBlocking {
        val session = StudySessionEntity(
            sessionId = "session_tx",
            taskTitle = "Transaction Test",
            plannedDurationMillis = 30 * 60 * 1000L,
            startTimeMillis = 1_000_000L,
            currentState = "INTERVENTION_ONE_PENDING"
        )
        repository.insertSession(session)

        val intervention = InterventionRecord(
            interventionId = "int_tx_1",
            sessionId = "session_tx",
            interventionNumber = 1,
            interventionType = "INTERVENTION_ONE",
            triggeredAt = 1_005_000L
        )
        repository.insertIntervention(intervention)

        // Atomic transaction: user takes a break
        val txSuccess = repository.recordInterventionResponse(
            sessionId = "session_tx",
            interventionNumber = 1,
            response = "TAKE_BREAK",
            ignored = false,
            respondedAt = 1_006_000L,
            newSessionState = "SESSION_PAUSED"
        )
        assertTrue(txSuccess)

        // Both session state and intervention response must reflect the update together
        val updatedSession = repository.getSessionById("session_tx")
        val updatedIntervention = repository.getInterventionByNumber("session_tx", 1)

        assertEquals("SESSION_PAUSED", updatedSession?.currentState)
        assertEquals(1_006_000L, updatedSession?.updatedAt)

        assertEquals("TAKE_BREAK", updatedIntervention?.response)
        assertFalse(updatedIntervention?.ignored ?: true)
        assertEquals(1_006_000L, updatedIntervention?.respondedAt)
    }
}

/**
 * In-memory test implementation of StudyRescueDao implementing exact Room query semantics.
 */
class FakeStudyRescueDao : StudyRescueDao {
    private val sessions = mutableMapOf<String, StudySessionEntity>()
    private val interventions = mutableListOf<InterventionRecord>()

    override suspend fun insertSession(session: StudySessionEntity) {
        sessions[session.sessionId] = session
    }

    override suspend fun updateSession(session: StudySessionEntity) {
        sessions[session.sessionId] = session
    }

    override suspend fun getSessionById(sessionId: String): StudySessionEntity? {
        return sessions[sessionId]
    }

    override suspend fun getActiveSession(): StudySessionEntity? {
        return sessions.values
            .filter { it.currentState != "SESSION_COMPLETED" && it.currentState != "SESSION_CANCELLED" }
            .maxByOrNull { it.updatedAt }
    }

    override fun getAllSessionsFlow(): kotlinx.coroutines.flow.Flow<List<StudySessionEntity>> {
        return kotlinx.coroutines.flow.flowOf(sessions.values.sortedByDescending { it.createdAt })
    }

    override suspend fun getRecentSessions(limit: Int): List<StudySessionEntity> {
        return sessions.values.sortedByDescending { it.createdAt }.take(limit)
    }

    override suspend fun insertIntervention(intervention: InterventionRecord): Long {
        val duplicate = interventions.any {
            it.sessionId == intervention.sessionId && it.interventionNumber == intervention.interventionNumber
        }
        if (duplicate) return -1L
        interventions.add(intervention)
        return 1L
    }

    override suspend fun updateIntervention(intervention: InterventionRecord) {
        val index = interventions.indexOfFirst { it.interventionId == intervention.interventionId }
        if (index != -1) {
            interventions[index] = intervention
        }
    }

    override suspend fun getInterventionsForSession(sessionId: String): List<InterventionRecord> {
        return interventions.filter { it.sessionId == sessionId }.sortedBy { it.interventionNumber }
    }

    override suspend fun getInterventionByNumber(sessionId: String, number: Int): InterventionRecord? {
        return interventions.find { it.sessionId == sessionId && it.interventionNumber == number }
    }

    override suspend fun getIgnoredInterventionsCount(sessionId: String): Int {
        return interventions.count { it.sessionId == sessionId && it.ignored }
    }
}
