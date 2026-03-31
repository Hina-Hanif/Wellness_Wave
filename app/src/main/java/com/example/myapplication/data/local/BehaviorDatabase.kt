package com.example.myapplication.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [BehaviorRecord::class], version = 2, exportSchema = false)
abstract class BehaviorDatabase : RoomDatabase() {
    abstract fun behaviorDao(): BehaviorDao

    companion object {
        @Volatile
        private var INSTANCE: BehaviorDatabase? = null

        fun getDatabase(context: Context): BehaviorDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BehaviorDatabase::class.java,
                    "behavior_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
