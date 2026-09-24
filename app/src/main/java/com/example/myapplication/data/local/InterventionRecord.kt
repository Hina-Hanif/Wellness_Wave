package com.example.myapplication.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing an individual intervention during a study session.
 */
@Entity(
    tableName = "intervention_records",
    foreignKeys = [
        ForeignKey(
            entity = StudySessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["sessionId", "interventionNumber"], unique = true)
    ]
)
data class InterventionRecord(
    @PrimaryKey
    val interventionId: String,
    val sessionId: String,
    val interventionNumber: Int,
    val interventionType: String,
    val triggeredAt: Long,
    val respondedAt: Long? = null,
    val response: String? = null,
    val ignored: Boolean = false,
    val timeoutAt: Long? = null,
    val metadata: String? = null
)
