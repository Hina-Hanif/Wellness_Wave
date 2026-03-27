package com.example.myapplication.models

data class PredictionHistoryResponse(
    val user_id: String,
    val history: List<HistoricalPrediction>
)

data class HistoricalPrediction(
    val date: String,
    val stress_score: Float, // 0.0 to 1.0 (Low to High)
    val screen_time_hours: Float
)
