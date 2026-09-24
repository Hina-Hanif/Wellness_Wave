package com.example.myapplication.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a persistent Study Rescue session.
 */
@Entity(tableName = "study_sessions")
data class StudySessionEntity(
    @PrimaryKey
    val sessionId: String,
    val taskTitle: String,
    val plannedDurationMillis: Long,
    val startTimeMillis: Long,
    val endTimeMillis: Long? = null,
    val currentState: String,
    val selectedRestrictedAppPackages: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val focusRescueStartTime: Long? = null,
    val focusRescueEndTime: Long? = null,
    val focusRescueState: String? = null,
    val focusRescueExitReason: String? = null,
    val focusRescueCompletedAt: Long? = null,
    val cooldownUntil: Long? = null
)
