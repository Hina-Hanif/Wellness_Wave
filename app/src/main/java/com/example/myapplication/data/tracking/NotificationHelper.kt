package com.example.myapplication.data.tracking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.myapplication.MainActivity
import com.example.myapplication.models.PredictionResponse

object NotificationHelper {
    private const val TAG = "NotificationHelper"
    private const val CHANNEL_ALERTS = "behavioral_alerts"
    private const val CHANNEL_WELLNESS = "wellness_tips"
    private const val CHANNEL_NAME_ALERTS = "Behavioral Insights"
    private const val CHANNEL_NAME_WELLNESS = "Wellness Tips"

    /**
     * Primary entry point: Handles push notifications governed strictly by the backend 8-point policy engine.
     */
    fun handleServerNotification(context: Context, prediction: PredictionResponse) {
        if (prediction.should_notify_push != true) {
            Log.d(TAG, "Push suppressed by server policy engine. Reason: ${prediction.suppression_reason ?: "Policy limits"}")
            return
        }

        val title = prediction.notification_title ?: "Wellness Wave Insight"
        val body = prediction.notification_body ?: prediction.real_time_feedback
        val type = prediction.notification_type ?: "WARNING"

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm, CHANNEL_WELLNESS, CHANNEL_NAME_WELLNESS, NotificationManager.IMPORTANCE_HIGH)

        val destination = if (type == "POSITIVE") "MINDFULNESS" else "ANALYTICS"
        val pi = buildMainPendingIntent(context, destination)

        val icon = if (type == "POSITIVE") android.R.drawable.ic_dialog_info else android.R.drawable.ic_menu_compass

        val notif = NotificationCompat.Builder(context, CHANNEL_WELLNESS)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setColor(if (type == "POSITIVE") 0xFF00C853.toInt() else 0xFFFF6D00.toInt())
            .build()

        val notificationId = ("server_push_" + System.currentTimeMillis()).hashCode()
        nm.notify(notificationId, notif)
        Log.i(TAG, "Server push notification posted: '$title'")
    }

    @Deprecated("Delegated to backend NotificationPolicyEngine to prevent fatigue")
    fun sendWellnessTip(context: Context, prediction: PredictionResponse) {
        handleServerNotification(context, prediction)
    }

    @Deprecated("Local push alerts disabled per 8-point anti-fatigue policy. Telemetry is sent to server.")
    fun sendBehavioralAlert(context: Context, title: String, message: String) {
        Log.d(TAG, "Local behavioral alert suppressed. Title: '$title' | Message: '$message'")
    }

    @Deprecated("Screen time alerts delegated to backend state change evaluations.")
    fun sendScreenTimeAlert(context: Context, screenTimeMinutes: Long) {
        Log.d(TAG, "Local screen time alert suppressed ($screenTimeMinutes mins). Delegated to server policy.")
    }

    @Deprecated("Hourly push reminders disabled to prevent fatigue. Hourly tips are in-app only.")
    fun sendHourlyReminder(context: Context, message: String) {
        Log.d(TAG, "Hourly push reminder suppressed. Message kept in-app only: '$message'")
    }

    private fun ensureChannel(nm: NotificationManager, id: String, name: String, importance: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(id, name, importance).apply {
                description = "Wellness Wave behavioral insights"
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildMainPendingIntent(context: Context, destination: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (destination != null) {
                putExtra("navigate_to", destination)
            }
        }
        return PendingIntent.getActivity(
            context, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
