package com.example.myapplication.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BehaviorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: BehaviorRecord)

    @Query("SELECT * FROM behavior_records ORDER BY timestamp DESC")
    suspend fun getAllRecords(): List<BehaviorRecord>

    @Query("SELECT * FROM behavior_records WHERE timestamp >= :timestamp LIMIT 1000")
    suspend fun getRecordsForLast7Days(timestamp: Long): List<BehaviorRecord>
}
