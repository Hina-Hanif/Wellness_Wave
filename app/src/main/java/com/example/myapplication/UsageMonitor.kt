package com.example.myapplication

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

class  UsageMonitor(private val context: Context) {

    fun hasUsagePermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            "android:get_usage_stats",
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun requestUsagePermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    fun getTodayScreenTimeMinutes(): Long {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        return getPreciseUsageMinutes(usageStatsManager, startTime, endTime)
    }

    fun getNightUsageMinutes(): Long {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = java.util.Calendar.getInstance()
        
        // 12:00 AM Today
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        
        // 6:00 AM Today
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 6)
        val endTime = calendar.timeInMillis

        return getPreciseUsageMinutes(usageStatsManager, startTime, endTime)
    }

    /**
     * Computes today's real foreground usage minutes for SOCIAL and PRODUCTIVE app categories.
     * Returns Pair(socialAppMinutes, productiveAppMinutes).
     */
    fun getCategoryUsageMinutes(totalScreenTimeMinutes: Long = 0L): Pair<Long, Long> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis

        val stats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        var socialMs = 0L
        var productiveMs = 0L

        stats.forEach { (packageName, usageStat) ->
            val fgTime = usageStat.totalTimeInForeground
            if (fgTime > 0) {
                when (com.example.myapplication.data.tracking.AppCategoryClassifier.classify(packageName)) {
                    com.example.myapplication.data.tracking.AppCategory.SOCIAL -> socialMs += fgTime
                    com.example.myapplication.data.tracking.AppCategory.PRODUCTIVE -> productiveMs += fgTime
                    else -> {}
                }
            }
        }

        var socialMin = socialMs / 1000 / 60
        var productiveMin = productiveMs / 1000 / 60

        if (totalScreenTimeMinutes > 0) {
            socialMin = socialMin.coerceAtMost(totalScreenTimeMinutes)
            productiveMin = productiveMin.coerceAtMost(totalScreenTimeMinutes)
        }

        return Pair(socialMin, productiveMin)
    }


    fun getTodayUnlockCount(): Int {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = android.app.usage.UsageEvents.Event()
        var unlockCount = 0
        
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            // 18 is UsageEvents.Event.KEYGUARD_HIDDEN
            if (event.eventType == 18) {
                unlockCount++
            }
        }
        return unlockCount
    }

    private fun getPreciseUsageMinutes(usageStatsManager: UsageStatsManager, startTime: Long, endTime: Long): Long {
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = android.app.usage.UsageEvents.Event()
        val lastResumedTime = mutableMapOf<String, Long>()
        var totalUsageMs = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> {
                    lastResumedTime[event.packageName] = event.timeStamp
                }
                android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val start = lastResumedTime.remove(event.packageName)
                    if (start != null && event.timeStamp > start) {
                        val effectiveStart = Math.max(start, startTime)
                        val effectiveEnd = Math.min(event.timeStamp, endTime)
                        if (effectiveEnd > effectiveStart) {
                            totalUsageMs += (effectiveEnd - effectiveStart)
                        }
                    }
                }
            }
        }
        return totalUsageMs / 1000 / 60
    }

    /**
     * Divides the period from today 00:00 to now into [count] intervals and returns usage intensity (0.0 - 1.0).
     */
    fun getTodayUsageIntervals(count: Int): List<Float> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val dayStart = calendar.timeInMillis
        val intervalDuration = 24 * 60 * 60 * 1000L / count
        
        val usageBuckets = LongArray(count) { 0L }
        
        // Fetch all events for the entire day once
        val events = usageStatsManager.queryEvents(dayStart, now)
        val event = android.app.usage.UsageEvents.Event()
        val lastResumedTime = mutableMapOf<String, Long>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> {
                    lastResumedTime[event.packageName] = event.timeStamp
                }
                android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val start = lastResumedTime.remove(event.packageName)
                    if (start != null && event.timeStamp > start) {
                        // Distribute the duration [start, event.timeStamp] into buckets
                        distributeDurationToBuckets(start, event.timeStamp, dayStart, intervalDuration, usageBuckets)
                    }
                }
            }
        }
        
        // Process currently running apps (not yet paused)
        lastResumedTime.forEach { (_, start) ->
            if (now > start) {
                distributeDurationToBuckets(start, now, dayStart, intervalDuration, usageBuckets)
            }
        }

        return usageBuckets.map { ms ->
            (ms.toFloat() / intervalDuration).coerceIn(0f, 1f)
        }
    }

    private fun distributeDurationToBuckets(start: Long, end: Long, dayStart: Long, intervalDuration: Long, buckets: LongArray) {
        val count = buckets.size
        for (i in 0 until count) {
            val bucketStart = dayStart + i * intervalDuration
            val bucketEnd = bucketStart + intervalDuration
            
            val overlapStart = Math.max(start, bucketStart)
            val overlapEnd = Math.min(end, bucketEnd)
            
            if (overlapEnd > overlapStart) {
                buckets[i] += (overlapEnd - overlapStart)
            }
        }
    }
}