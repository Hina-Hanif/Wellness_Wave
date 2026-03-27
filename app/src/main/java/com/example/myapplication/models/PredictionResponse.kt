package com.example.myapplication.models

data class PredictionResponse(
    val user_id: String,
    val date_evaluated: String?,
    val stress_level: String,
    val confidence_score: Float,
    val anxiety_detected: Boolean,
    val burnout_detected: Boolean,
    val addiction_detected: Boolean,
    val real_time_feedback: String
)
