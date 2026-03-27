package com.example.myapplication.models

data class UsageMetrics(
    val user_id: String,
    val date: String,
    val screen_time: Long, // in minutes
    val unlock_count: Int,
    val social_time: Long, // in minutes
    val productivity_time: Long, // in minutes
    val night_usage: Long, // in minutes
    val night_ratio: Float = 0f, // percentage of night usage
    val session_count: Int,
    val scrolling_speed_avg: Float = 0f,
    val scroll_erraticness: Float = 0f,
    val typing_cps: Float = 0f,
    val typing_hesitation_ms: Long = 0L,
    val backspace_rate: Float = 0f,
    val notification_response_sec: Float = 0f,
    val app_switch_count_per_hour: Int = 0,
    val usage_consistency_shift: Float = 0f,
    val typing_pauses_count: Int = 0,
    val max_typing_pause_ms: Long = 0L,
    val mood_score: Int
)
