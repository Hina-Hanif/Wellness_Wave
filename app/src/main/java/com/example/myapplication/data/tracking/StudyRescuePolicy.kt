package com.example.myapplication.data.tracking

import android.content.Context

/**
 * Configuration options governing Study Rescue restriction policy.
 */
data class FocusRescueConfig(
    val restrictedPackages: Set<String> = emptySet(),
    val customAllowedPackages: Set<String> = emptySet(),
    val allowEducationalApps: Boolean = true,
    val maxConsecutiveRedirects: Int = DEFAULT_MAX_CONSECUTIVE_REDIRECTS,
    val redirectCooldownMs: Long = DEFAULT_REDIRECT_COOLDOWN_MS
) {
    companion object {
        const val DEFAULT_MAX_CONSECUTIVE_REDIRECTS = 5
        const val DEFAULT_REDIRECT_COOLDOWN_MS = 1500L
        const val REDIRECT_WINDOW_RESET_MS = 10_000L
    }
}

/**
 * Outcome of evaluating a package switch against the Study Rescue policy.
 */
sealed class PolicyDecision {
    data class Allow(
        val packageName: String,
        val reason: AllowReason
    ) : PolicyDecision()

    data class Redirect(
        val targetPackage: String,
        val reason: String = "Restricted application opened during active Focus Rescue",
        val redirectCount: Int
    ) : PolicyDecision()

    data class Ignore(
        val packageName: String?,
        val reason: IgnoreReason
    ) : PolicyDecision()
}

enum class AllowReason {
    WELLNESS_WAVE_APP,
    FOCUS_RESCUE_SCREEN,
    EMERGENCY_OR_PHONE,
    SYSTEM_SETTINGS,
    SYSTEM_UI,
    SYSTEM_CRITICAL_FUNCTION,
    EDUCATIONAL_OR_PRODUCTIVE_APP,
    CUSTOM_ALLOWED,
    NOT_RESTRICTED
}

enum class IgnoreReason {
    FOCUS_RESCUE_INACTIVE,
    NO_ACTIVE_SESSION,
    NULL_OR_BLANK_PACKAGE,
    SAME_APP_NO_CHANGE,
    REDIRECT_LOOP_PREVENTED,
    COOLDOWN_ACTIVE
}

/**
 * Central contract determining restriction policy, allowed applications,
 * loop-breaker safeguards, and redirection decisions during Focus Rescue.
 */
interface StudyRescuePolicy {
    fun isFocusRescueActive(): Boolean
    fun getRestrictedPackages(): Set<String>
    fun isPackageAllowed(packageName: String?): Boolean
    fun canExitFocusRescue(): Boolean
    fun shouldIgnoreEvent(packageName: String?, timestamp: Long = System.currentTimeMillis()): Boolean
    fun shouldRedirectToFocusRescue(packageName: String?, timestamp: Long = System.currentTimeMillis()): Boolean
    fun evaluate(packageName: String?, timestamp: Long = System.currentTimeMillis()): PolicyDecision
    fun recordRedirectSuccess(packageName: String, timestamp: Long = System.currentTimeMillis())
    fun resetRedirectLoopCounter()
}

/**
 * Thread-safe default implementation of [StudyRescuePolicy].
 * Coordinates with [StudySessionManager] without retaining Activity/View contexts.
 */
class DefaultStudyRescuePolicy(
    private val sessionManagerProvider: () -> StudySessionManager?,
    private val configProvider: () -> FocusRescueConfig = { FocusRescueConfig() }
) : StudyRescuePolicy {

    private val lock = Any()

    private var consecutiveRedirectCount = 0
    private var lastRedirectTimeMs = 0L
    private var lastRedirectPackage: String? = null

    override fun isFocusRescueActive(): Boolean = synchronized(lock) {
        val manager = sessionManagerProvider() ?: return false
        return manager.stateFlow.value == StudyRescueState.FOCUS_RESCUE_ACTIVE
    }

    override fun getRestrictedPackages(): Set<String> = synchronized(lock) {
        val manager = sessionManagerProvider()
        val sessionPackages = manager?.activeSessionFlow?.value?.selectedRestrictedAppPackages?.toSet()
            ?: emptySet()
        val staticConfigPackages = configProvider().restrictedPackages
        val combined = sessionPackages + staticConfigPackages
        return PackageNameValidator.sanitizeRestrictedPackages(combined)
    }

    override fun isPackageAllowed(packageName: String?): Boolean = synchronized(lock) {
        if (packageName.isNullOrBlank()) return true
        val trimmed = packageName.trim()

        // 1. Wellness Wave itself & Focus Rescue screen
        if (trimmed == "com.example.myapplication") return true

        // 2. Critical system packages (emergency, phone, settings, system UI, launchers, keyboards)
        if (PackageNameValidator.isProtectedPackage(trimmed)) return true

        // 3. Explicit user-selected restricted package evaluation
        val restricted = getRestrictedPackages()
        if (restricted.contains(trimmed)) {
            return false
        }

        val config = configProvider()

        // 4. Custom allowed packages
        if (config.customAllowedPackages.contains(trimmed)) return true

        // 5. Educational / productive applications (when not restricted)
        if (config.allowEducationalApps &&
            AppCategoryClassifier.classify(trimmed) == AppCategory.PRODUCTIVE
        ) {
            return true
        }

        // 6. Any other package not in restricted list
        return true
    }

    override fun canExitFocusRescue(): Boolean = synchronized(lock) {
        val manager = sessionManagerProvider() ?: return false
        // User can exit Focus Rescue whenever it is active
        return manager.stateFlow.value == StudyRescueState.FOCUS_RESCUE_ACTIVE
    }

    override fun shouldIgnoreEvent(packageName: String?, timestamp: Long): Boolean = synchronized(lock) {
        val decision = evaluate(packageName, timestamp)
        return decision is PolicyDecision.Ignore
    }

    override fun shouldRedirectToFocusRescue(packageName: String?, timestamp: Long): Boolean = synchronized(lock) {
        val decision = evaluate(packageName, timestamp)
        return decision is PolicyDecision.Redirect
    }

    override fun evaluate(packageName: String?, timestamp: Long): PolicyDecision = synchronized(lock) {
        if (packageName.isNullOrBlank()) {
            return PolicyDecision.Ignore(packageName, IgnoreReason.NULL_OR_BLANK_PACKAGE)
        }
        val trimmed = packageName.trim()

        // Reset loop counter if window expired
        if (timestamp - lastRedirectTimeMs > FocusRescueConfig.REDIRECT_WINDOW_RESET_MS) {
            consecutiveRedirectCount = 0
        }

        // 1. Check if Focus Rescue is currently active
        if (!isFocusRescueActive()) {
            return if (PackageNameValidator.isProtectedPackage(trimmed) || isPackageAllowed(trimmed)) {
                PolicyDecision.Allow(trimmed, determineAllowReason(trimmed))
            } else {
                PolicyDecision.Ignore(trimmed, IgnoreReason.FOCUS_RESCUE_INACTIVE)
            }
        }

        // 2. Wellness Wave application itself is ALWAYS allowed
        if (trimmed == "com.example.myapplication") {
            consecutiveRedirectCount = 0 // User returned to app, reset loop count
            return PolicyDecision.Allow(trimmed, AllowReason.WELLNESS_WAVE_APP)
        }

        // 3. System Settings is ALWAYS allowed (ensures user can disable accessibility service)
        val lower = trimmed.lowercase()
        if (lower == "com.android.settings" ||
            lower == "com.google.android.settings" ||
            lower.startsWith("com.android.settings.")
        ) {
            consecutiveRedirectCount = 0
            return PolicyDecision.Allow(trimmed, AllowReason.SYSTEM_SETTINGS)
        }

        // 4. Emergency & Phone dialers are ALWAYS allowed
        if (lower == "com.android.phone" ||
            lower == "com.google.android.dialer" ||
            lower == "com.samsung.android.dialer" ||
            lower == "com.android.server.telecom" ||
            lower == "com.android.incallui" ||
            lower.contains("emergency") ||
            lower.contains("dialer") ||
            lower.contains("incallui")
        ) {
            consecutiveRedirectCount = 0
            return PolicyDecision.Allow(trimmed, AllowReason.EMERGENCY_OR_PHONE)
        }

        // 5. System UI is ALWAYS allowed
        if (lower == "com.android.systemui" || lower == "android") {
            return PolicyDecision.Allow(trimmed, AllowReason.SYSTEM_UI)
        }

        // 6. Launchers and Keyboards are ALWAYS allowed
        if (PackageNameValidator.isProtectedPackage(trimmed)) {
            return PolicyDecision.Allow(trimmed, AllowReason.SYSTEM_CRITICAL_FUNCTION)
        }

        val config = configProvider()

        // 7. Explicit user-selected restricted package evaluation (takes precedence over generic categorization)
        val restricted = getRestrictedPackages()
        if (restricted.contains(trimmed)) {
            // Loop breaker check (Security Requirement 4)
            if (consecutiveRedirectCount >= config.maxConsecutiveRedirects) {
                return PolicyDecision.Ignore(trimmed, IgnoreReason.REDIRECT_LOOP_PREVENTED)
            }

            // Cooldown check
            if (timestamp - lastRedirectTimeMs < config.redirectCooldownMs &&
                trimmed == lastRedirectPackage
            ) {
                return PolicyDecision.Ignore(trimmed, IgnoreReason.COOLDOWN_ACTIVE)
            }

            return PolicyDecision.Redirect(
                targetPackage = trimmed,
                redirectCount = consecutiveRedirectCount + 1
            )
        }

        // 8. Custom allowed packages
        if (config.customAllowedPackages.contains(trimmed)) {
            return PolicyDecision.Allow(trimmed, AllowReason.CUSTOM_ALLOWED)
        }

        // 9. Educational / productive apps (when not explicitly restricted by user)
        if (config.allowEducationalApps &&
            AppCategoryClassifier.classify(trimmed) == AppCategory.PRODUCTIVE
        ) {
            return PolicyDecision.Allow(trimmed, AllowReason.EDUCATIONAL_OR_PRODUCTIVE_APP)
        }

        // 10. Unrestricted applications
        return PolicyDecision.Allow(trimmed, AllowReason.NOT_RESTRICTED)
    }

    override fun recordRedirectSuccess(packageName: String, timestamp: Long) = synchronized(lock) {
        consecutiveRedirectCount++
        lastRedirectTimeMs = timestamp
        lastRedirectPackage = packageName
    }

    override fun resetRedirectLoopCounter() = synchronized(lock) {
        consecutiveRedirectCount = 0
        lastRedirectTimeMs = 0L
        lastRedirectPackage = null
    }

    private fun determineAllowReason(pkg: String): AllowReason {
        val lower = pkg.lowercase()
        return when {
            lower == "com.example.myapplication" -> AllowReason.WELLNESS_WAVE_APP
            lower == "com.android.settings" || lower == "com.google.android.settings" || lower.startsWith("com.android.settings.") -> AllowReason.SYSTEM_SETTINGS
            lower == "com.android.phone" || lower == "com.google.android.dialer" || lower == "com.samsung.android.dialer" || lower.contains("emergency") || lower.contains("dialer") -> AllowReason.EMERGENCY_OR_PHONE
            lower == "com.android.systemui" || lower == "android" -> AllowReason.SYSTEM_UI
            PackageNameValidator.isProtectedPackage(pkg) -> AllowReason.SYSTEM_CRITICAL_FUNCTION
            AppCategoryClassifier.classify(pkg) == AppCategory.PRODUCTIVE -> AllowReason.EDUCATIONAL_OR_PRODUCTIVE_APP
            else -> AllowReason.NOT_RESTRICTED
        }
    }
}
