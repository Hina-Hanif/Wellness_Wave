package com.example.myapplication.models

data class RealTimeData(
    val user_id: String,
    val screen_time: Long,
    val app_switches: Int,
    val scroll_speed: Float,
    val typing_speed: Float,
    val unlock_count: Int,
    val night_usage: Long,
    val social_app_minutes: Long = 0,
    val productive_app_minutes: Long = 0
)
