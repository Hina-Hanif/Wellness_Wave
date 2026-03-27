package com.example.myapplication.data.tracking

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationReactionTracker : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationTracking"
        private val arrivalTimes = mutableMapOf<String, Long>()
        val averageResponseSec = kotlinx.coroutines.flow.MutableStateFlow(0f)
        private var totalResponseTimeMs = 0L
        private var responsesCount = 0
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val key = sbn.key
        arrivalTimes[key] = System.currentTimeMillis()
        Log.d(TAG, "Notification posted: $key")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        val key = sbn.key
        val arrivalTime = arrivalTimes.remove(key)
        
        if (arrivalTime != null) {
            val responseTime = System.currentTimeMillis() - arrivalTime
            // REASON_CLICKED = 1
            if (reason == 1 || reason == 2) { // 1 = CLICK, 2 = CANCEL
                totalResponseTimeMs += responseTime
                responsesCount++
                averageResponseSec.value = (totalResponseTimeMs.toFloat() / responsesCount) / 1000f
                Log.d(TAG, "Notification reacted: $key in ${responseTime}ms. Avg: ${averageResponseSec.value}s")
            }
        }
    }
}
