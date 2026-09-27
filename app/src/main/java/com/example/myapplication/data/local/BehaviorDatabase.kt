package com.example.myapplication.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BehaviorRecord::class,
        StudySessionEntity::class,
        InterventionRecord::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(StudyRescueConverters::class)
abstract class BehaviorDatabase : RoomDatabase() {
    abstract fun behaviorDao(): BehaviorDao
    abstract fun studyRescueDao(): StudyRescueDao

    companion object {
        @Volatile
        private var INSTANCE: BehaviorDatabase? = null

        private fun createStudyRescueTables(database: SupportSQLiteDatabase) {
            // Drop any legacy prototype tables to guarantee clean schema migration
            database.execSQL("DROP TABLE IF EXISTS `intervention_records`")
            database.execSQL("DROP TABLE IF EXISTS `study_sessions`")

            // 1. Create study_sessions table with exact Room schema
            database.execSQL("""
                CREATE TABLE `study_sessions` (
                    `sessionId` TEXT NOT NULL,
                    `taskTitle` TEXT NOT NULL,
                    `plannedDurationMillis` INTEGER NOT NULL,
                    `startTimeMillis` INTEGER NOT NULL,
                    `endTimeMillis` INTEGER,
                    `currentState` TEXT NOT NULL,
                    `selectedRestrictedAppPackages` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `focusRescueStartTime` INTEGER,
                    `focusRescueEndTime` INTEGER,
                    `focusRescueState` TEXT,
                    `focusRescueExitReason` TEXT,
                    `focusRescueCompletedAt` INTEGER,
                    `cooldownUntil` INTEGER,
                    PRIMARY KEY(`sessionId`)
                )
            """.trimIndent())

            // 2. Create intervention_records table with Foreign Key to study_sessions
            database.execSQL("""
                CREATE TABLE `intervention_records` (
                    `interventionId` TEXT NOT NULL,
                    `sessionId` TEXT NOT NULL,
                    `interventionNumber` INTEGER NOT NULL,
                    `interventionType` TEXT NOT NULL,
                    `triggeredAt` INTEGER NOT NULL,
                    `respondedAt` INTEGER,
                    `response` TEXT,
                    `ignored` INTEGER NOT NULL,
                    `timeoutAt` INTEGER,
                    `metadata` TEXT,
                    PRIMARY KEY(`interventionId`),
                    FOREIGN KEY(`sessionId`) REFERENCES `study_sessions`(`sessionId`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())

            // 3. Create index on sessionId
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_intervention_records_sessionId` 
                ON `intervention_records` (`sessionId`)
            """.trimIndent())

            // 4. Create unique index on (sessionId, interventionNumber)
            database.execSQL("""
                CREATE UNIQUE INDEX IF NOT EXISTS `index_intervention_records_sessionId_interventionNumber` 
                ON `intervention_records` (`sessionId`, `interventionNumber`)
            """.trimIndent())
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create study_sessions table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `study_sessions` (
                        `sessionId` TEXT NOT NULL,
                        `taskTitle` TEXT NOT NULL,
                        `plannedDurationMillis` INTEGER NOT NULL,
                        `startTimeMillis` INTEGER NOT NULL,
                        `endTimeMillis` INTEGER,
                        `currentState` TEXT NOT NULL,
                        `selectedRestrictedAppPackages` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `focusRescueStartTime` INTEGER,
                        `focusRescueEndTime` INTEGER,
                        `focusRescueState` TEXT,
                        `focusRescueExitReason` TEXT,
                        `focusRescueCompletedAt` INTEGER,
                        `cooldownUntil` INTEGER,
                        PRIMARY KEY(`sessionId`)
                    )
                """.trimIndent())

                // 2. Create intervention_records table with Foreign Key to study_sessions
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `intervention_records` (
                        `interventionId` TEXT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `interventionNumber` INTEGER NOT NULL,
                        `interventionType` TEXT NOT NULL,
                        `triggeredAt` INTEGER NOT NULL,
                        `respondedAt` INTEGER,
                        `response` TEXT,
                        `ignored` INTEGER NOT NULL,
                        `timeoutAt` INTEGER,
                        `metadata` TEXT,
                        PRIMARY KEY(`interventionId`),
                        FOREIGN KEY(`sessionId`) REFERENCES `study_sessions`(`sessionId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())

                // 3. Create index on sessionId
                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS `index_intervention_records_sessionId` 
                    ON `intervention_records` (`sessionId`)
                """.trimIndent())

                // 4. Create unique index on (sessionId, interventionNumber)
                db.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_intervention_records_sessionId_interventionNumber` 
                    ON `intervention_records` (`sessionId`, `interventionNumber`)
                """.trimIndent())
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createStudyRescueTables(db)
            }
        }

        val MIGRATION_2_4 = object : Migration(2, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createStudyRescueTables(db)
            }
        }

        fun getDatabase(context: Context): BehaviorDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BehaviorDatabase::class.java,
                    "behavior_database"
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_2_4)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

