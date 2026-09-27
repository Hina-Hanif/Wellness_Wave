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

    const val CHANNEL_STUDY_RESCUE = "study_rescue_channel"
    const val CHANNEL_NAME_STUDY_RESCUE = "Study Rescue Alerts"
    const val STUDY_RESCUE_NOTIFICATION_ID = 8801

    fun postStudyRescueIntervention(
        context: Context,
        sessionId: String,
        interventionNumber: Int,
        taskTitle: String,
        isEscalated: Boolean
    ): Boolean {
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            ensureChannel(nm, CHANNEL_STUDY_RESCUE, CHANNEL_NAME_STUDY_RESCUE, NotificationManager.IMPORTANCE_HIGH)

            val title = if (isEscalated) {
                "Focus Rescue Warning"
            } else {
                "Stay Focused on ${taskTitle.ifBlank { "Study" }}"
            }

            val body = if (isEscalated) {
                "Second distraction detected during '${taskTitle.ifBlank { "Deep Focus" }}'. Resume studying or take a mindful break to stay on track."
            } else {
                "You appear off-task from '${taskTitle.ifBlank { "your session" }}'. Would you like to resume studying or take a short break?"
            }

            val contentPendingIntent = buildMainPendingIntent(context, "MINDFULNESS")

            val resumeIntent = Intent(context, StudyRescueActionReceiver::class.java).apply {
                action = StudyRescueActionReceiver.ACTION_RESUME_STUDYING
                putExtra(StudyRescueActionReceiver.EXTRA_SESSION_ID, sessionId)
                putExtra(StudyRescueActionReceiver.EXTRA_INTERVENTION_NUMBER, interventionNumber)
            }
            val resumePendingIntent = PendingIntent.getBroadcast(
                context, 101, resumeIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val breakIntent = Intent(context, StudyRescueActionReceiver::class.java).apply {
                action = StudyRescueActionReceiver.ACTION_TAKE_BREAK
                putExtra(StudyRescueActionReceiver.EXTRA_SESSION_ID, sessionId)
                putExtra(StudyRescueActionReceiver.EXTRA_INTERVENTION_NUMBER, interventionNumber)
            }
            val breakPendingIntent = PendingIntent.getBroadcast(
                context, 102, breakIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val dismissIntent = Intent(context, StudyRescueActionReceiver::class.java).apply {
                action = StudyRescueActionReceiver.ACTION_DISMISS_INTERVENTION
                putExtra(StudyRescueActionReceiver.EXTRA_SESSION_ID, sessionId)
                putExtra(StudyRescueActionReceiver.EXTRA_INTERVENTION_NUMBER, interventionNumber)
            }
            val dismissPendingIntent = PendingIntent.getBroadcast(
                context, 103, dismissIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_STUDY_RESCUE)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(contentPendingIntent)
                .setDeleteIntent(dismissPendingIntent)
                .addAction(android.R.drawable.ic_media_play, "Resume", resumePendingIntent)
                .addAction(android.R.drawable.ic_media_pause, "Take Break", breakPendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPendingIntent)
                .setColor(if (isEscalated) 0xFFD32F2F.toInt() else 0xFF1976D2.toInt())

            nm.notify(STUDY_RESCUE_NOTIFICATION_ID, builder.build())
            Log.i(TAG, "Study Rescue notification posted: intervention #$interventionNumber (escalated=$isEscalated)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post Study Rescue notification: ${e.message}", e)
            false
        }
    }

    fun cancelStudyRescueNotification(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(STUDY_RESCUE_NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel Study Rescue notification: ${e.message}")
        }
    }

    private fun ensureChannel(nm: NotificationManager, id: String, name: String, importance: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(id, name, importance).apply {
                description = "Wellness Wave notifications"
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
