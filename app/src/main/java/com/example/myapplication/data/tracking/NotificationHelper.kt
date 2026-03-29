package com.example.myapplication.data.tracking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.myapplication.MainActivity
import com.example.myapplication.models.PredictionResponse

object NotificationHelper {
    private const val CHANNEL_ALERTS  = "behavioral_alerts"
    private const val CHANNEL_WELLNESS = "wellness_tips"
    private const val CHANNEL_NAME_ALERTS  = "Behavioral Insights"
    private const val CHANNEL_NAME_WELLNESS = "Wellness Tips"

    // ── Global Rate Limiting ──────────────────────────────────────────────────
    private var lastNotificationTime = 0L
    private const val NOTIFICATION_COOLDOWN_MS = 2 * 60 * 60 * 1000L // 2 hours

    private fun canSendNotification(): Boolean {
        val now = System.currentTimeMillis()
        // Allow if no notification has been sent yet, or if 2 hours have passed
        if (lastNotificationTime == 0L || now - lastNotificationTime >= NOTIFICATION_COOLDOWN_MS) {
            lastNotificationTime = now
            return true
        }
        return false
    }

    // ── Existing behavioral alert (app switch / scroll anomaly) ──────────────
    fun sendBehavioralAlert(context: Context, title: String, message: String) {
        if (!canSendNotification()) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm, CHANNEL_ALERTS, CHANNEL_NAME_ALERTS, NotificationManager.IMPORTANCE_HIGH)

        val pi = buildMainPendingIntent(context, "ANALYTICS")
        
        val emoji = when {
            title.contains("Focus") || title.contains("App") -> "🧩"
            title.contains("Frustration") || title.contains("Backspace") -> "☯️"
            title.contains("Typing") || title.contains("Intensity") -> "⚡"
            title.contains("Scroll") || title.contains("Mindful") -> "🌊"
            else -> "✨"
        }

        val notif = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$emoji $title")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setColor(0xFF6200EE.toInt())
            .build()

        nm.notify(("behavior_" + System.currentTimeMillis()).hashCode(), notif)
    }

    // ── NEW: Wellness tip notification based on ML prediction ────────────────
    fun sendWellnessTip(context: Context, prediction: PredictionResponse) {
        // We only send wellness tips if we haven't sent another notification recently
        if (!canSendNotification()) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm, CHANNEL_WELLNESS, CHANNEL_NAME_WELLNESS, NotificationManager.IMPORTANCE_HIGH)

        val (emoji, title, tip) = buildWellnessContent(prediction)

        val pi = buildMainPendingIntent(context, "MINDFULNESS")

        val notif = NotificationCompat.Builder(context, CHANNEL_WELLNESS)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("$emoji $title")
            .setContentText(tip)
            .setStyle(NotificationCompat.BigTextStyle().bigText(tip))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            // Action: open mindfulness
            .addAction(
                android.R.drawable.ic_menu_compass,
                "Open Mindfulness",
                pi
            )
            .build()

        nm.notify(("wellness_${System.currentTimeMillis()}").hashCode(), notif)
    }

    // ── NEW: Quick "phone down" push — sent when screen time is excessive ────
    fun sendScreenTimeAlert(context: Context, screenTimeMinutes: Long) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm, CHANNEL_WELLNESS, CHANNEL_NAME_WELLNESS, NotificationManager.IMPORTANCE_DEFAULT)

        val hours = screenTimeMinutes / 60
        val mins  = screenTimeMinutes % 60
        val timeStr = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

        val pi = buildMainPendingIntent(context, "ANALYTICS")
        val notif = NotificationCompat.Builder(context, CHANNEL_WELLNESS)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("📵 Time to Rest")
            .setContentText("You've spent $timeStr on your phone today. Put it down and recharge.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        nm.notify("screen_time".hashCode(), notif)
    }

    fun sendHourlyReminder(context: Context, message: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm, CHANNEL_WELLNESS, CHANNEL_NAME_WELLNESS, NotificationManager.IMPORTANCE_DEFAULT)

        val pi = buildMainPendingIntent(context, "MINDFULNESS")
        
        val notif = NotificationCompat.Builder(context, CHANNEL_WELLNESS)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("🧘 Gentle Reminder")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setColor(0xFF03DAC5.toInt()) // Teal
            .build()

        nm.notify("hourly_reminder".hashCode(), notif)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private data class WellnessContent(val emoji: String, val title: String, val tip: String)

    private fun buildWellnessContent(p: PredictionResponse): WellnessContent {
        return when {
            p.burnout_detected -> WellnessContent(
                "🛌", "Rest Signals Detected",
                "Take a real break. Your patterns indicate burnout risk."
            )
            p.stress_level.equals("High", ignoreCase = true) -> WellnessContent(
                "🌬️", "Take a Breath",
                "Step away for 5 mins. Your stress levels are elevated."
            )
            p.anxiety_detected -> WellnessContent(
                "🧘", "Calm Mode",
                "Try box breathing now. High-arousal patterns detected."
            )
            p.addiction_detected -> WellnessContent(
                "📵", "Digital Detox",
                "Put the phone down for 30 mins. Unlocking intensity is high."
            )
            p.stress_level.equals("Medium", ignoreCase = true) -> WellnessContent(
                "⚡", "Moderate Stress",
                "Take a 5-minute break. Stay hydrated and mindful."
            )
            else -> WellnessContent(
                "✅", "Balanced State",
                "You're doing great! Keep up the healthy habits."
            )
        }
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
