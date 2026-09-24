package com.example.myapplication.data.tracking

import android.content.Context
import androidx.core.app.NotificationManagerCompat

/**
 * Contract decoupling notification delivery from domain logic for 100% testability.
 */
interface StudyRescueNotifier {
    fun showInterventionNotification(
        sessionId: String,
        interventionNumber: Int,
        taskTitle: String,
        isEscalated: Boolean
    ): Boolean

    fun cancelNotification(sessionId: String)

    fun hasNotificationPermission(): Boolean
}

class AndroidStudyRescueNotifier(private val context: Context) : StudyRescueNotifier {
    override fun showInterventionNotification(
        sessionId: String,
        interventionNumber: Int,
        taskTitle: String,
        isEscalated: Boolean
    ): Boolean {
        if (!hasNotificationPermission()) return false
        return NotificationHelper.postStudyRescueIntervention(
            context,
            sessionId,
            interventionNumber,
            taskTitle,
            isEscalated
        )
    }

    override fun cancelNotification(sessionId: String) {
        NotificationHelper.cancelStudyRescueNotification(context)
    }

    override fun hasNotificationPermission(): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}

class NoOpStudyRescueNotifier : StudyRescueNotifier {
    override fun showInterventionNotification(
        sessionId: String,
        interventionNumber: Int,
        taskTitle: String,
        isEscalated: Boolean
    ): Boolean = false

    override fun cancelNotification(sessionId: String) {}

    override fun hasNotificationPermission(): Boolean = true
}
