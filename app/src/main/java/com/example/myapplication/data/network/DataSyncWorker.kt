package com.example.myapplication.data.network

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.data.UsageDataManager
import com.example.myapplication.data.tracking.NotificationHelper
import java.util.concurrent.TimeUnit

class DataSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val usageDataManager = UsageDataManager(applicationContext)
        val api = RetrofitClient.instance

        return try {
            val metrics = usageDataManager.getDailyMetrics(moodScore = 0)

            Log.d("DataSyncWorker", "============================================")
            Log.d("DataSyncWorker", "  WELLNESS WAVE - SYNCING DATA TO FIREBASE  ")
            Log.d("DataSyncWorker", "============================================")
            Log.d("DataSyncWorker", "  User ID        : ${metrics.user_id}")
            Log.d("DataSyncWorker", "  Date           : ${metrics.date}")
            Log.d("DataSyncWorker", "  Screen Time    : ${metrics.screen_time} min")
            Log.d("DataSyncWorker", "  Typing CPS     : ${metrics.typing_cps}")
            Log.d("DataSyncWorker", "  Typing Pauses  : ${metrics.typing_pauses_count}")
            Log.d("DataSyncWorker", "  Scroll Speed   : ${metrics.scrolling_speed_avg}")
            Log.d("DataSyncWorker", "  Erraticness    : ${metrics.scroll_erraticness}")
            Log.d("DataSyncWorker", "  App Switches   : ${metrics.session_count}")
            Log.d("DataSyncWorker", "  Night Usage    : ${metrics.night_usage} min")
            Log.d("DataSyncWorker", "  Social Time    : ${metrics.social_time} min")
            Log.d("DataSyncWorker", "--------------------------------------------")

            // Send metrics to backend
            val submitResponse = api.submitDailyData(metrics)

            if (submitResponse.isSuccessful) {
                Log.d("DataSyncWorker", "✅ SUCCESS — Data sent to Firebase via FastAPI!")

                // Fetch the latest ML prediction for this user
                val predictionResponse = try {
                    api.getPrediction(metrics.user_id)
                } catch (e: Exception) {
                    Log.e("DataSyncWorker", "⚠ Could not fetch prediction: ${e.message}")
                    null
                }

                if (predictionResponse?.isSuccessful == true) {
                    val prediction = predictionResponse.body()
                    if (prediction != null) {
                        Log.d("DataSyncWorker", "🧠 Prediction: Stress=${prediction.stress_level} | Anxiety=${prediction.anxiety_detected} | Burnout=${prediction.burnout_detected}")

                        // Send contextual wellness tip notification
                        NotificationHelper.sendWellnessTip(applicationContext, prediction)

                        // Separately alert if screen time is very high (> 5 hours)
                        val screenTimeHours = TimeUnit.MINUTES.toHours(metrics.screen_time)
                        if (screenTimeHours >= 5) {
                            NotificationHelper.sendScreenTimeAlert(applicationContext, metrics.screen_time)
                        }
                    }
                } else {
                    Log.w("DataSyncWorker", "⚠ Prediction not available yet — no notification sent")
                }

                Log.d("DataSyncWorker", "============================================")
                Result.success()

            } else {
                Log.e("DataSyncWorker", "❌ FAILED — Backend responded: ${submitResponse.code()} ${submitResponse.errorBody()?.string()}")
                Log.d("DataSyncWorker", "============================================")
                Result.retry()
            }

        } catch (e: Exception) {
            Log.e("DataSyncWorker", "❌ ERROR — Could not reach backend: ${e.message}")
            Log.d("DataSyncWorker", "  (Is the backend running? Check your API base URL)")
            Log.d("DataSyncWorker", "============================================")
            Result.retry()
        }
    }
}
