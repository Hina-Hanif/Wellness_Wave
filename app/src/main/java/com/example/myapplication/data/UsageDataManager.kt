package com.example.myapplication.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.example.myapplication.data.tracking.BehavioralAccessibilityService
import com.example.myapplication.data.tracking.NotificationReactionTracker
import com.example.myapplication.models.UsageMetrics
import java.util.*
import java.util.concurrent.TimeUnit

class UsageDataManager(private val context: Context) {

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun getDailyMetrics(moodScore: Int = 0): UsageMetrics {
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        
        // Start from 12:00 AM today to standardize night usage window and align with backend/UI logic
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis

        // Precision metrics using queryEvents
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        
        var totalAppSwitches = 0
        val lastEventTime = mutableMapOf<String, Long>()
        
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val eventTime = event.timeStamp
            
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    totalAppSwitches++
                    lastEventTime[event.packageName] = eventTime
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    lastEventTime.remove(event.packageName)
                }
            }
        }
        
        val usageMonitor = com.example.myapplication.UsageMonitor(context)
        val nightUsageMinutes = usageMonitor.getNightUsageMinutes()
        val realUnlockCount = usageMonitor.getTodayUnlockCount()

        // Aggregate stats for category totals
        val stats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        var totalScreenTimeMs = 0L
        var socialTimeMs = 0L
        var productivityTimeMs = 0L
        
        val socialPackages = setOf("com.facebook.katana", "com.instagram.android", "com.twitter.android", "com.zhiliaoapp.musically", "com.whatsapp")
        val productivityPackages = setOf("com.google.android.apps.docs", "com.microsoft.office.word", "com.slack", "com.google.android.gm")

        stats.forEach { (packageName, usageStat) ->
            val timeInForeground = usageStat.totalTimeInForeground
            if (timeInForeground > 0) {
                totalScreenTimeMs += timeInForeground
                if (socialPackages.contains(packageName)) socialTimeMs += timeInForeground
                else if (productivityPackages.contains(packageName)) productivityTimeMs += timeInForeground
            }
        }

        val nightRatio = if (totalScreenTimeMs > 0) (nightUsageMinutes * 60 * 1000).toFloat() / totalScreenTimeMs else 0f

        val sharedPrefs = context.getSharedPreferences("wellness_wave_prefs", Context.MODE_PRIVATE)
        var userId = sharedPrefs.getString("user_id", null) ?: UUID.randomUUID().toString().also {
            sharedPrefs.edit().putString("user_id", it).apply()
        }

        val df = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateString = df.format(Date())

        val avgScreenTime = getHistoricalAverageScreenTime(totalScreenTimeMs)
        val shift = if (avgScreenTime > 0) {
            (totalScreenTimeMs.toFloat() / avgScreenTime.toFloat()).coerceIn(0.5f, 1.5f)
        } else 1.0f

        val activeHours = (TimeUnit.MILLISECONDS.toHours(totalScreenTimeMs)).coerceAtLeast(1L)
        
        return UsageMetrics(
            user_id = userId,
            date = dateString,
            screen_time = TimeUnit.MILLISECONDS.toMinutes(totalScreenTimeMs),
            unlock_count = realUnlockCount, 
            social_time = TimeUnit.MILLISECONDS.toMinutes(socialTimeMs),
            productivity_time = TimeUnit.MILLISECONDS.toMinutes(productivityTimeMs),
            night_usage = nightUsageMinutes,
            night_ratio = nightRatio,
            session_count = totalAppSwitches,
            scrolling_speed_avg = BehavioralAccessibilityService.scrollVelocityAvg.value,
            scroll_erraticness = BehavioralAccessibilityService.scrollErraticness.value,
            typing_cps = BehavioralAccessibilityService.typingCps.value,
            typing_hesitation_ms = BehavioralAccessibilityService.typingHesitationMs.value,
            backspace_rate = BehavioralAccessibilityService.backspaceCount.value.toFloat(),
            notification_response_sec = NotificationReactionTracker.averageResponseSec.value,
            app_switch_count_per_hour = (totalAppSwitches / activeHours).toInt(),
            usage_consistency_shift = shift,
            typing_pauses_count = BehavioralAccessibilityService.typingPausesCount.value,
            max_typing_pause_ms = BehavioralAccessibilityService.maxTypingPauseMs.value,
            mood_score = moodScore
        )
    }

    private fun getHistoricalAverageScreenTime(currentTime: Long): Long {
        val prefs = context.getSharedPreferences("usage_history", Context.MODE_PRIVATE)
        val historyStr = prefs.getString("screen_time_history", "") ?: ""
        val history = if (historyStr.isEmpty()) mutableListOf() else historyStr.split(",").map { it.toLong() }.toMutableList()
        
        // Update history (simplified: store once per day if called frequently)
        val lastUpdate = prefs.getLong("last_history_update", 0L)
        val dayMs = 24 * 60 * 60 * 1000L
        if (System.currentTimeMillis() - lastUpdate > dayMs) {
            history.add(currentTime)
            if (history.size > 7) history.removeAt(0)
            prefs.edit()
                .putString("screen_time_history", history.joinToString(","))
                .putLong("last_history_update", System.currentTimeMillis())
                .apply()
        }
        
        return if (history.isEmpty()) currentTime else history.average().toLong()
    }
}
