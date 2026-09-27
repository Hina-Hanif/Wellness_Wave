package com.example.myapplication.data.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver handling interactive user responses from Study Rescue notification actions.
 * Updates the state machine and Room persistent records accordingly.
 */
class StudyRescueActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "StudyRescueReceiver"
        const val ACTION_RESUME_STUDYING = "com.example.myapplication.action.RESUME_STUDYING"
        const val ACTION_TAKE_BREAK = "com.example.myapplication.action.TAKE_BREAK"
        const val ACTION_DISMISS_INTERVENTION = "com.example.myapplication.action.DISMISS_INTERVENTION"

        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_INTERVENTION_NUMBER = "extra_intervention_number"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return
        val interventionNumber = intent.getIntExtra(EXTRA_INTERVENTION_NUMBER, 1)

        Log.d(TAG, "Received notification action: $action | session: $sessionId | intervention: #$interventionNumber")

        val manager = StudySessionManager.getInstance(context)

        when (action) {
            ACTION_RESUME_STUDYING -> {
                manager.onInterventionResumed(sessionId, interventionNumber)
            }
            ACTION_TAKE_BREAK -> {
                manager.onInterventionTakeBreak(sessionId, interventionNumber)
            }
            ACTION_DISMISS_INTERVENTION -> {
                manager.onInterventionDismissed(sessionId, interventionNumber)
            }
        }

        // Dismiss notification from status bar
        NotificationHelper.cancelStudyRescueNotification(context)
    }
}
