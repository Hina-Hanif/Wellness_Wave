package com.example.myapplication.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BehaviorRepository(private val behaviorDao: BehaviorDao) {

    suspend fun insertRecord(record: BehaviorRecord) = withContext(Dispatchers.IO) {
        behaviorDao.insertRecord(record)
    }

    suspend fun getAllRecords(): List<BehaviorRecord> = withContext(Dispatchers.IO) {
        behaviorDao.getAllRecords()
    }

    suspend fun getRecordsForLast7Days(): List<BehaviorRecord> = withContext(Dispatchers.IO) {
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        behaviorDao.getRecordsForLast7Days(sevenDaysAgo)
    }
}
