package com.example.myapplication.data.tracking

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicInteger

class BehavioralAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "BehavioralTracking"

        // ── ML Features (StateFlows for UI reactivity) ───────────────────────
        val typingCps            = kotlinx.coroutines.flow.MutableStateFlow(0f)
        val backspaceCount       = kotlinx.coroutines.flow.MutableStateFlow(0)
        val typingHesitationMs   = kotlinx.coroutines.flow.MutableStateFlow(0L)
        val typingPausesCount    = kotlinx.coroutines.flow.MutableStateFlow(0)
        val maxTypingPauseMs     = kotlinx.coroutines.flow.MutableStateFlow(0L)

        // ── AppSwitchTracker integration ─────────────────────────────────────
        // Single source of truth for all app-switch analytics
        private val appSwitchTracker = AppSwitchTracker()

        // Re-expose tracker StateFlows under the original names so existing
        // UI observers need zero changes.
        val switchCount      get() = appSwitchTracker.switchCountFlow
        val switchTimestamps get() = emptyList<Long>() // kept for API compat; logic lives in tracker

        // ── ScrollTracker integration ─────────────────────────────────────────
        private val scrollTracker = ScrollTracker()

        val scrollVelocityAvg  get() = scrollTracker.scrollVelocityAvgFlow
        val scrollErraticness  get() = scrollTracker.scrollErraticnessFlow

        // ── Typing internals ─────────────────────────────────────────────────
        private val characterCount           = AtomicInteger(0)
        private var lastTypingTime           = 0L
        private var totalKeystrokeIntervalMs = 0L
        private var keystrokeCount           = 0
        private var lastAlertTime            = 0L
        private var backspaceWindowStart     = 0L
        private var backspaceWindowCount     = 0

        // ── Screen-time throttle ─────────────────────────────────────────────
        private var lastScreenTimeCheckTime  = 0L
        private var lastScreenTimeAlertDate  = ""
    }

    // ── Event dispatch ────────────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val eventTypeStr = when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "TYPE_VIEW_TEXT_CHANGED"
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> "TYPE_VIEW_SCROLLED"
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "TYPE_WINDOW_STATE_CHANGED"
            else -> "UNKNOWN (${event.eventType})"
        }
        
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Log.d(TAG, "Accessibility Event Captured -> Type: $eventTypeStr | Package: ${event.packageName}")
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED    -> handleTypingEvent(event)
            AccessibilityEvent.TYPE_VIEW_SCROLLED        -> handleScrollEvent(event)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowStateChanged(event)
        }
    }

    // ── Window / App-switch tracking ──────────────────────────────────────────

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        when (val result = appSwitchTracker.onWindowChanged(event)) {

            is AppSwitchTracker.SwitchResult.NoChange -> {
                // Same app or our own package — nothing to do
            }

            is AppSwitchTracker.SwitchResult.Switched -> {
                Log.d(
                    TAG,
                    "App switched to: ${result.to} | " +
                            "recent(5m)=${result.recentSwitchCount}, " +
                            "total=${appSwitchTracker.switchCount}"
                )

                if (appSwitchTracker.switchCount > 40) {
                    NotificationHelper.sendBehavioralAlert(
                        this,
                        "High Distraction",
                        "Frequent app switching detected (>40 today). Try focusing on one task at a time."
                    )
                }

                // Throttled screen-time check (at most once per 15 min)
                val now = System.currentTimeMillis()
                if (now - lastScreenTimeCheckTime > 15 * 60 * 1000L) {
                    lastScreenTimeCheckTime = now
                    checkScreenTimeLimit()
                }
            }

            is AppSwitchTracker.SwitchResult.RapidSwitchDetected -> {
                Log.w(TAG, "Rapid switching detected! Total switches: ${result.totalSwitchCount}")
                NotificationHelper.sendBehavioralAlert(
                    this,
                    "Focus Alert",
                    "Rapid app switching detected! Try focusing on one task at a time."
                )

                // Still honour the screen-time check after a rapid-switch burst
                val now = System.currentTimeMillis()
                if (now - lastScreenTimeCheckTime > 15 * 60 * 1000L) {
                    lastScreenTimeCheckTime = now
                    checkScreenTimeLimit()
                }
            }
        }
    }

    private fun checkScreenTimeLimit() {
        Log.d(TAG, "Checking screen time limit threshold (300 min)...")
        try {
            val monitor = com.example.myapplication.UsageMonitor(this)
            val minutes = monitor.getTodayScreenTimeMinutes()
            Log.d(TAG, "Current screen time today: $minutes min")

            val today = java.text.SimpleDateFormat(
                "yyyy-MM-dd", java.util.Locale.getDefault()
            ).format(java.util.Date())

            if (minutes >= 300 && lastScreenTimeAlertDate != today) {
                lastScreenTimeAlertDate = today
                Log.i(TAG, "CRITICAL: 5-hour limit exceeded ($minutes min). Sending notification.")
                NotificationHelper.sendBehavioralAlert(
                    this,
                    "Overuse Risk",
                    "Screen time is exceptionally high (>5 hours). Consider taking a long break to rest your eyes and mind."
                )
            } else if (minutes < 300) {
                Log.d(TAG, "Screen time below threshold ($minutes / 300).")
            } else {
                Log.d(TAG, "Alert already sent for today ($today).")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking screen time limit: ${e.message}")
        }
    }

    // ── Scroll tracking (delegated to ScrollTracker) ──────────────────────────

    private fun handleScrollEvent(event: AccessibilityEvent) {
        val alertNeeded = scrollTracker.onScrollEvent(event)

        if (scrollVelocityAvg.value > 1200f) {
            NotificationHelper.sendBehavioralAlert(
                this,
                "High Distraction",
                "Rapid scrolling detected (>1200 px/s). Try slowing down to focus better."
            )
        } else if (alertNeeded) {
            Log.w(TAG, "High scroll erraticness detected! Triggering Mindful Moment Alert.")
            NotificationHelper.sendBehavioralAlert(
                this,
                "Mindful Moment",
                "You've been scrolling quite rapidly. Take a 2-minute breathing break?"
            )
        }
    }

    // ── Typing tracking ───────────────────────────────────────────────────────

    private fun handleTypingEvent(event: AccessibilityEvent) {
        val currentTime = System.currentTimeMillis()
        val added   = event.addedCount
        val removed = event.removedCount

        if (added > 0) {
            characterCount.addAndGet(added)

            if (lastTypingTime != 0L) {
                val delta = currentTime - lastTypingTime

                // Track typing pauses (gap > 2 s counts as a pause)
                if (delta > 2000) {
                    typingPausesCount.value++
                    if (delta > maxTypingPauseMs.value) {
                        maxTypingPauseMs.value = delta
                    }
                }

                if (delta in 1..10000) {
                    totalKeystrokeIntervalMs += delta
                    keystrokeCount += added
                    typingHesitationMs.value =
                        totalKeystrokeIntervalMs / keystrokeCount.coerceAtLeast(1)

                    val currentCps = (added.toFloat() / (delta / 1000f)).coerceIn(0.1f, 15f)
                    val alpha = if (typingCps.value == 0f) 0.8f else 0.2f
                    typingCps.value = (typingCps.value * (1f - alpha)) + (currentCps * alpha)

                    Log.d(TAG, "Typing speed: " + String.format("%.2f", typingCps.value) + " CPS")
                }
            } else {
                typingCps.value = 0.5f
            }

            lastTypingTime = currentTime
        }

        if (removed > 0) {
            backspaceCount.value += removed
            handleBackspaceAnomaly(removed)
            Log.d(TAG, "Backspace used. Total count: ${backspaceCount.value}")
        }

        checkTypingAnomaly()
    }

    private fun handleBackspaceAnomaly(removed: Int) {
        val now = System.currentTimeMillis()
        if (now - backspaceWindowStart > 10_000L) {
            backspaceWindowStart = now
            backspaceWindowCount = removed
        } else {
            backspaceWindowCount += removed
        }

        if (backspaceWindowCount >= 15 && now - lastAlertTime > 60_000L) {
            lastAlertTime = now
            NotificationHelper.sendBehavioralAlert(
                this,
                "Frustration Detected?",
                "You're erasing a lot. Take a deep breath — it is okay to take it slow."
            )
            backspaceWindowCount = 0
        }
    }

    private fun checkTypingAnomaly() {
        val now = System.currentTimeMillis()
        if (typingCps.value > 8.5f && now - lastAlertTime > 60_000L) {
            lastAlertTime = now
            NotificationHelper.sendBehavioralAlert(
                this,
                "High Typing Intensity",
                "Your typing speed is very high. Are you feeling rushed? Try to relax your shoulders."
            )
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onInterrupt() {
        Log.e(TAG, "Accessibility Service Interrupted")
        ServiceConnectionManager.setConnected(false)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Behavioral Accessibility Service Connected")
        ServiceConnectionManager.setConnected(true)

        checkScreenTimeLimit()

        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(
                applicationContext,
                "Wellness Wave AI Active \uD83C\uDF0A\nNow monitoring background behavior",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        ServiceConnectionManager.setConnected(false)
        return super.onUnbind(intent)
    }
}