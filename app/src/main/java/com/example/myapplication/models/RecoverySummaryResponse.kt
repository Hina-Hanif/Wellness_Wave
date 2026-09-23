package com.example.myapplication.models

data class LabelRecoveryTrend(
    val spikes_this_week: Int,
    val avg_recovery_time_min: Int?,
    val recovery_velocity_trend_pct: Int?,
    val is_faster: Boolean?,
    val trigger_summary: String?
)

data class RecoverySummaryResponse(
    val user_id: String,
    val has_sufficient_history: Boolean,
    val recovery_trends: Map<String, LabelRecoveryTrend>?
)
