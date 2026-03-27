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

    fun getDailyMetrics(moodScore: Int): UsageMetrics {
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        
        // Start from 11 PM yesterday to capture full night window
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis

        // Precision metrics using queryEvents
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        
        var nightUsageMs = 0L
        var totalAppSwitches = 0
        val lastEventTime = mutableMapOf<String, Long>()
        
        val nightStartHour = 23
        val nightEndHour = 5
        
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val eventTime = event.timeStamp
            val hour = Calendar.getInstance().apply { timeInMillis = eventTime }.get(Calendar.HOUR_OF_DAY)
            val isNight = hour >= nightStartHour || hour < nightEndHour
            
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    totalAppSwitches++
                    lastEventTime[event.packageName] = eventTime
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val start = lastEventTime.remove(event.packageName)
                    if (start != null) {
                        val duration = eventTime - start
                        if (isNight) nightUsageMs += duration
                    }
                }
            }
        }

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

        val nightRatio = if (totalScreenTimeMs > 0) nightUsageMs.toFloat() / totalScreenTimeMs else 0f

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

        return UsageMetrics(
            user_id = userId,
            date = dateString,
            screen_time = TimeUnit.MILLISECONDS.toMinutes(totalScreenTimeMs),
            unlock_count = (totalAppSwitches / 5).coerceAtLeast(1), 
            social_time = TimeUnit.MILLISECONDS.toMinutes(socialTimeMs),
            productivity_time = TimeUnit.MILLISECONDS.toMinutes(productivityTimeMs),
            night_usage = TimeUnit.MILLISECONDS.toMinutes(nightUsageMs),
            night_ratio = nightRatio,
            session_count = totalAppSwitches,
            scrolling_speed_avg = BehavioralAccessibilityService.scrollVelocityAvg.value,
            scroll_erraticness = BehavioralAccessibilityService.scrollErraticness.value,
            typing_cps = BehavioralAccessibilityService.typingCps.value,
            typing_hesitation_ms = BehavioralAccessibilityService.typingHesitationMs.value,
            backspace_rate = BehavioralAccessibilityService.backspaceCount.value.toFloat(),
            notification_response_sec = NotificationReactionTracker.averageResponseSec.value,
            app_switch_count_per_hour = totalAppSwitches / 24,
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
