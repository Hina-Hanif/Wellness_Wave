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
    private var lastScrollX = -1
    private var lastScrollY = -1

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
     * Wipe velocity history window but maintain current average state.
     * Call after an erraticness alert fires to avoid repeated triggers.
     */
    fun reset() {
        velocitySamples.clear()
        scrollErraticnessFlow.value = 0f
        lastScrollTimeMs = 0L
        lastScrollX = -1
        lastScrollY = -1
        Log.d(TAG, "ScrollTracker samples reset (avg retained at ${scrollVelocityAvgFlow.value}).")
    }

    // ── private helpers ──────────────────────────────────────────────────────

    /**
     * Derive a true velocity value (px/sec) from the scroll event.
     *
     * Fixes:
     *  1. Calculates real spatial delta (dx, dy) instead of raw absolute scroll position.
     *  2. Requires a minimum time delta (10ms) to prevent division-by-near-zero spikes.
     *  3. Clamps computed speed to a realistic human touchscreen limit (MAX_PLAUSIBLE_SPEED = 4000 px/s).
     */
    private fun computeVelocity(event: AccessibilityEvent, nowMs: Long): Float {
        val currentX = event.scrollX
        val currentY = event.scrollY

        // Try using API 28+ scrollDelta if available
        var deltaPx = 0f
        val deltaX = event.scrollDeltaX
        val deltaY = event.scrollDeltaY

        if (deltaX != -1 || deltaY != -1) {
            val dx = if (deltaX != -1) deltaX.toFloat() else 0f
            val dy = if (deltaY != -1) deltaY.toFloat() else 0f
            deltaPx = sqrt(dx * dx + dy * dy)
        } else if (lastScrollX != -1 && lastScrollY != -1 && (currentX != -1 || currentY != -1)) {
            val dx = (currentX - lastScrollX).toFloat()
            val dy = (currentY - lastScrollY).toFloat()
            deltaPx = sqrt(dx * dx + dy * dy)
        }

        // Update position history
        if (currentX != -1) lastScrollX = currentX
        if (currentY != -1) lastScrollY = currentY

        if (deltaPx <= 0f) {
            return 0f
        }

        if (lastScrollTimeMs == 0L) {
            return 0f
        }

        val timeDiffMs = nowMs - lastScrollTimeMs
        // Ignore rapid burst events (< 10 ms) to avoid near-zero time division artifacts
        if (timeDiffMs < 10L) {
            return 0f
        }

        // Calculate velocity in pixels per second
        val rawVelocity = (deltaPx / (timeDiffMs / 1000f))

        // Sanity Clamping: typical human touch flick/fling on high DPI screen is 500 - 3500 px/s
        val MAX_PLAUSIBLE_SPEED = 4000f
        return rawVelocity.coerceIn(0f, MAX_PLAUSIBLE_SPEED)
    }

    private fun recordVelocity(velocity: Float) {
        velocitySamples.add(velocity)
        if (velocitySamples.size > MAX_VELOCITY_SAMPLES) {
            velocitySamples.removeAt(0)
        }
    }

    private fun recalculateStats() {
        if (velocitySamples.isEmpty()) return
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