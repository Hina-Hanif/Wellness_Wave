package com.example.myapplication.models

data class PredictionResponse(
    val user_id: String,
    val date_evaluated: String?,
    val stress_level: String,
    val confidence_score: Float,
    val anxiety_detected: Boolean,
    val burnout_detected: Boolean,
    val addiction_detected: Boolean,
    val real_time_feedback: String,

    // Server-side anti-fatigue notification policy decision fields
    val should_notify_push: Boolean? = false,
    val notification_title: String? = null,
    val notification_body: String? = null,
    val notification_type: String? = null,
    val suppression_reason: String? = null
)
