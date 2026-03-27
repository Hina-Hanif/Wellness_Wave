package com.example.myapplication.data.tracking


import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.sqrt

class ScrollTracker {

    companion object {
        private const val TAG = "ScrollTracker"
        private const val MAX_VELOCITY_SAMPLES = 100
        private const val SCROLL_EVENT_LOG_INTERVAL = 10
        private const val MIN_AVG_VELOCITY_THRESHOLD = 0.1f
        private const val ERRATICNESS_TRIGGER_THRESHOLD = 1.8f
        private const val MIN_SAMPLES_FOR_ERRATICNESS = 20
    }

    // Exposed StateFlows (mirrors existing BehavioralAccessibilityService fields)
    val scrollVelocityAvgFlow = MutableStateFlow(0f)
    val scrollErraticnessFlow = MutableStateFlow(0f)

    // Readable properties for direct access
    val scrollVelocityAvg: Float get() = scrollVelocityAvgFlow.value
    val scrollErraticness: Float get() = scrollErraticnessFlow.value

    private val velocitySamples = mutableListOf<Float>()
    private var lastScrollTimeMs = 0L

    /**
     * Call this from BehavioralAccessibilityService.onAccessibilityEvent()
     * whenever event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED.
     *
     * Returns true if erraticness threshold was exceeded (so the caller
     * can decide whether to fire an alert and reset the tracker).
     */
    fun onScrollEvent(event: AccessibilityEvent): Boolean {
        val now = System.currentTimeMillis()

        val velocity = computeVelocity(event, now)

        if (velocity > 0f) {
            recordVelocity(velocity)
            recalculateStats()
            lastScrollTimeMs = now

            if (velocitySamples.size % SCROLL_EVENT_LOG_INTERVAL == 0) {
                Log.d(TAG, "Scroll velocity avg: $scrollVelocityAvg px/s equivalent")
            }

            if (scrollErraticness > ERRATICNESS_TRIGGER_THRESHOLD
                && velocitySamples.size > MIN_SAMPLES_FOR_ERRATICNESS
            ) {
                Log.w(TAG, "High scroll erraticness detected: $scrollErraticness")
                reset()
                return true   // caller should fire the alert
            }
        }

        return false
    }

    /**
     * Wipe velocity history and reset computed stats.
     * Call after an erraticness alert fires to avoid repeated triggers.
     */
    fun reset() {
        velocitySamples.clear()
        scrollVelocityAvgFlow.value = 0f
        scrollErraticnessFlow.value = 0f
        lastScrollTimeMs = 0L
        Log.d(TAG, "ScrollTracker reset.")
    }

    // ── private helpers ──────────────────────────────────────────────────────

    /**
     * Derive a velocity value from the event.
     *
     * Strategy (same as the original BehavioralAccessibilityService):
     *  1. Use the raw scroll deltas when non-zero.
     *  2. Fall back to a frequency-based proxy (5000 / timeDiff) so that
     *     events with delta == 0 (common on some devices) still contribute.
     */
    private fun computeVelocity(event: AccessibilityEvent, nowMs: Long): Float {
        val rawDelta = (event.scrollX + event.scrollY).toFloat().let {
            if (it < 0f) -it else it          // manual absoluteValue (no stdlib dep)
        }

        return when {
            rawDelta > 0f -> rawDelta
            lastScrollTimeMs != 0L -> {
                val timeDiff = (nowMs - lastScrollTimeMs).coerceAtLeast(1L)
                5_000f / timeDiff             // proxy: pixels/ms equivalent
            }
            else -> 0f
        }
    }

    private fun recordVelocity(velocity: Float) {
        velocitySamples.add(velocity)
        if (velocitySamples.size > MAX_VELOCITY_SAMPLES) {
            velocitySamples.removeAt(0)
        }
    }

    private fun recalculateStats() {
        val avg = velocitySamples.average().toFloat()
        scrollVelocityAvgFlow.value = avg

        val erraticness = if (avg > MIN_AVG_VELOCITY_THRESHOLD) {
            val variance = velocitySamples
                .map { (it - avg) * (it - avg) }
                .average()
                .toFloat()
            sqrt(variance.toDouble()).toFloat() / avg
        } else {
            0f
        }
        scrollErraticnessFlow.value = erraticness
    }
}