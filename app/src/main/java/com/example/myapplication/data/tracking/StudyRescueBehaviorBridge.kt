package com.example.myapplication.data.tracking

import android.content.Context
import android.util.Log

enum class DistractionReason {
    RESTRICTED_APP_OPENED,
    RAPID_APP_SWITCHING,
    SOCIAL_APP_EXPOSURE
}

/**
 * Adapter bridge integrating existing behavioral tracking signals with Study Rescue.
 *
 * Design constraints:
 * - Zero duplicate accessibility services or usage monitors.
 * - Gated execution: Exits immediately with zero overhead when no session is active or when paused.
 * - Respects user choice: Only restricted apps chosen by the user trigger app-open distractions.
 * - Protected apps (phone, emergency, dialers, settings, system UI) & productive apps are strictly exempted.
 * - Cooldown & debouncing to prevent duplicate events from rapid window state transitions or recompositions.
 * - High testability on the JVM via constructor-injected sessionManagerProvider.
 */
class StudyRescueBehaviorBridge(
    private val sessionManagerProvider: () -> StudySessionManager?
) {
    companion object {
        private const val TAG = "StudyRescueBridge"
        const val COOLDOWN_SAME_PACKAGE_MS = 30_000L   // 30 seconds for same package
        const val COOLDOWN_GLOBAL_MS = 10_000L         // 10 seconds between any distraction events

        @Volatile
        private var INSTANCE: StudyRescueBehaviorBridge? = null

        fun getInstance(context: Context): StudyRescueBehaviorBridge {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StudyRescueBehaviorBridge {
                    StudySessionManager.getInstance(context.applicationContext)
                }.also { INSTANCE = it }
            }
        }
    }

    private var lastDistractionTimeMs = 0L
    private var lastDistractionPackage: String? = null

    /**
     * Evaluates an app switch event from [AppSwitchTracker].
     *
     * @return true if the event was recognized and recorded as an active study distraction.
     */
    fun onAppSwitched(
        fromPackage: String,
        toPackage: String,
        recentSwitchCount: Int,
        timestamp: Long = System.currentTimeMillis()
    ): Boolean {
        val manager = sessionManagerProvider() ?: return false

        // 1. Avoid processing Study Rescue logic when no session is active or when paused
        if (!manager.isSessionActive() || manager.stateFlow.value == StudyRescueState.SESSION_PAUSED) {
            return false
        }

        // 2. Ignore our own app (returning to study context)
        if (toPackage == "com.example.myapplication") {
            return false
        }

        // 3. Ignore protected emergency, phone, and necessary system apps
        if (isProtectedApp(toPackage)) {
            return false
        }

        // 4. Ignore productive and educational applications by default
        if (AppCategoryClassifier.classify(toPackage) == AppCategory.PRODUCTIVE) {
            return false
        }

        val activeSession = manager.activeSessionFlow.value ?: return false
        val restrictedPackages = activeSession.selectedRestrictedAppPackages

        // 5. Check if the newly opened app was restricted by the user for this session
        // (Rule 1: Do not label every app switch as a distraction automatically)
        // (Rule 2: Respect the user's selected restricted applications)
        val isRestricted = restrictedPackages.contains(toPackage)
        if (!isRestricted) {
            return false
        }

        // 6. Cooldown & duplicate prevention
        if (!isCooldownSatisfied(toPackage, timestamp)) {
            safeLogD(TAG, "Suppressed duplicate distraction for $toPackage due to cooldown.")
            return false
        }

        // 7. Record distraction
        lastDistractionTimeMs = timestamp
        lastDistractionPackage = toPackage

        safeLogI(TAG, "Distraction detected: User opened restricted app '$toPackage' during active session.")
        return manager.onDistractionDetected(toPackage, DistractionReason.RESTRICTED_APP_OPENED, timestamp)
    }

    /**
     * Evaluates a rapid app switching burst event from [AppSwitchTracker].
     *
     * @return true if recorded as a restlessness distraction during an active study session.
     */
    fun onRapidSwitchDetected(
        currentPackage: String,
        totalSwitches: Int,
        timestamp: Long = System.currentTimeMillis()
    ): Boolean {
        val manager = sessionManagerProvider() ?: return false

        // 1. Avoid processing when no session is active or when paused
        if (!manager.isSessionActive() || manager.stateFlow.value == StudyRescueState.SESSION_PAUSED) {
            return false
        }

        // 2. Ignore if current package is our own app or protected
        if (currentPackage == "com.example.myapplication" || isProtectedApp(currentPackage)) {
            return false
        }

        // 3. Global cooldown check
        if (lastDistractionTimeMs != 0L && timestamp - lastDistractionTimeMs < COOLDOWN_GLOBAL_MS) {
            return false
        }

        lastDistractionTimeMs = timestamp
        lastDistractionPackage = currentPackage

        safeLogI(TAG, "Distraction detected: Rapid app switching ($totalSwitches switches) during active session.")
        return manager.onDistractionDetected(currentPackage, DistractionReason.RAPID_APP_SWITCHING, timestamp)
    }

    private fun safeLogD(tag: String, message: String) {
        try {
            android.util.Log.d(tag, message)
        } catch (_: Throwable) {
            // Unmocked Log in unit test environment
        }
    }

    private fun safeLogI(tag: String, message: String) {
        try {
            android.util.Log.i(tag, message)
        } catch (_: Throwable) {
            // Unmocked Log in unit test environment
        }
    }

    private fun isCooldownSatisfied(packageName: String, timestamp: Long): Boolean {
        if (lastDistractionTimeMs == 0L) return true
        if (timestamp - lastDistractionTimeMs < COOLDOWN_GLOBAL_MS) return false
        if (packageName == lastDistractionPackage && (timestamp - lastDistractionTimeMs < COOLDOWN_SAME_PACKAGE_MS)) {
            return false
        }
        return true
    }

    private fun isProtectedApp(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower == "com.android.phone" ||
                lower == "com.google.android.dialer" ||
                lower == "com.samsung.android.dialer" ||
                lower == "com.android.server.telecom" ||
                lower == "com.android.systemui" ||
                lower == "android" ||
                lower == "com.android.settings" ||
                lower.contains("emergency") ||
                lower.contains("launcher") ||
                lower.contains("keyboard") ||
                lower.contains("inputmethod")
    }

    /**
     * Explicitly reset cooldowns (useful for test resets).
     */
    fun resetCooldown() {
        lastDistractionTimeMs = 0L
        lastDistractionPackage = null
    }
}
