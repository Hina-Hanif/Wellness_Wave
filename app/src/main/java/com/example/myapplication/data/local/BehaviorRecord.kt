package com.example.myapplication.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "behavior_records")
data class BehaviorRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val screenTime: Long,
    val unlockCount: Int,
    val nightUsage: Long,
    val appSwitchCount: Int,
    val scrollSpeed: Float
)
