package com.example.myapplication.api

import com.example.myapplication.models.PredictionHistoryResponse
import com.example.myapplication.models.PredictionResponse
import com.example.myapplication.models.UsageMetrics
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface WellnessWaveApi {
    @POST("/daily-data")
    suspend fun submitDailyData(@Body metrics: UsageMetrics): Response<Map<String, Any>>

    @GET("/prediction")
    suspend fun getPrediction(@Query("user_id") userId: String): Response<PredictionResponse>

    @GET("/history")
    suspend fun getHistory(@Query("user_id") userId: String): Response<PredictionHistoryResponse>
}
