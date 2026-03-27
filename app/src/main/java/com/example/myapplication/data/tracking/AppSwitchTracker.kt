package com.example.myapplication.data.tracking

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow

class AppSwitchTracker(
    private val ownPackageName: String = "com.example.myapplication"
) {

    companion object {
        private const val TAG = "AppSwitchTracker"
        private const val SWITCH_WINDOW_MS = 5 * 60 * 1000L   // 5-minute rolling window
        private const val RAPID_SWITCH_THRESHOLD = 8           // switches before alert fires
        
        // Hardcoded list of widely-known system packages
        private val IGNORED_PACKAGES = setOf(
            "com.android.systemui",
            "android"
        )
    }

    // ── Public StateFlows ────────────────────────────────────────────────────

    /** Total app switches recorded since the tracker was created. */
    val switchCountFlow = MutableStateFlow(0)

    /** Number of switches inside the current 5-minute rolling window. */
    val recentSwitchCountFlow = MutableStateFlow(0)

    // ── Readable properties for non-flow access ──────────────────────────────

    val switchCount: Int         get() = switchCountFlow.value
    val recentSwitchCount: Int   get() = recentSwitchCountFlow.value

    // ── Internal state ───────────────────────────────────────────────────────

    private var lastPackageName = ""
    private var lastSwitchTimeMs = 0L
    private val switchTimestamps = mutableListOf<Long>()

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Call this from [BehavioralAccessibilityService.onAccessibilityEvent]
     * whenever [AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED] fires.
     *
     * @return [SwitchResult] describing what happened so the caller can
     *         decide whether to show an alert or take further action.
     */
    fun onWindowChanged(event: AccessibilityEvent): SwitchResult {
        val newPackage = event.packageName?.toString()
            ?: return SwitchResult.NoChange

        // 1. Ignore our own app
        if (newPackage == ownPackageName) {
            return SwitchResult.NoChange
        }

        // 2. Ignore identical subsequent packages (internal app screen changes)
        if (newPackage == lastPackageName) {
            return SwitchResult.NoChange
        }

        // 3. Ignore explicit System UI, keyboards, and launchers
        val isIgnored = IGNORED_PACKAGES.contains(newPackage) ||
                newPackage.contains("launcher", ignoreCase = true) ||
                newPackage.contains("nexuslauncher", ignoreCase = true) ||
                newPackage.contains("trebuchet", ignoreCase = true) ||
                newPackage.contains("sec.android.app", ignoreCase = true) || // Samsung home/system apps
                newPackage.contains("inputmethod", ignoreCase = true) ||
                newPackage.contains("keyboard", ignoreCase = true) ||
                newPackage.contains("honeyboard", ignoreCase = true) // Samsung keyboard

        if (isIgnored) {
            return SwitchResult.NoChange
        }

        val now = System.currentTimeMillis()

        // 4. Ignore extremely fast switches (< 800 ms) to avoid false positives
        if (now - lastSwitchTimeMs < 800L) {
            return SwitchResult.NoChange
        }

        val previousPackage = lastPackageName
        lastPackageName = newPackage
        lastSwitchTimeMs = now

        // Increment total switch counter
        switchCountFlow.value++

        // Update rolling 5-minute window
        switchTimestamps.add(now)
        switchTimestamps.removeAll { it < now - SWITCH_WINDOW_MS }
        recentSwitchCountFlow.value = switchTimestamps.size

        Log.d(TAG, "Real App switched: $previousPackage → $newPackage | " +
                "total=${switchCount}, recent(5m)=${recentSwitchCount}")

        // Detect rapid switching
        return if (switchTimestamps.size >= RAPID_SWITCH_THRESHOLD) {
            switchTimestamps.clear()
            recentSwitchCountFlow.value = 0
            Log.w(TAG, "Rapid app switching threshold reached. Alert triggered.")
            SwitchResult.RapidSwitchDetected(newPackage, switchCount)
        } else {
            SwitchResult.Switched(previousPackage, newPackage, recentSwitchCount)
        }
    }

    /** Reset all counters and history. */
    fun reset() {
        switchCountFlow.value = 0
        recentSwitchCountFlow.value = 0
        switchTimestamps.clear()
        lastPackageName = ""
        lastSwitchTimeMs = 0L
        Log.d(TAG, "AppSwitchTracker reset.")
    }

    // ── Result sealed class ──────────────────────────────────────────────────

    sealed class SwitchResult {
        /** Package did not change or was ignored — nothing to act on. */
        object NoChange : SwitchResult()

        /** A normal app switch occurred. */
        data class Switched(
            val from: String,
            val to: String,
            val recentSwitchCount: Int
        ) : SwitchResult()

        /** Rapid switching threshold breached — caller should show an alert. */
        data class RapidSwitchDetected(
            val currentPackage: String,
            val totalSwitchCount: Int
        ) : SwitchResult()
    }
}