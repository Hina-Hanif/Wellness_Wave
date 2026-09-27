package com.example.myapplication.data.tracking

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.myapplication.MainActivity

/**
 * Result of evaluating and enforcing a window event.
 */
sealed class EnforcementResult {
    data class Allowed(val packageName: String, val reason: AllowReason) : EnforcementResult()
    data class Redirected(val targetPackage: String, val redirectCount: Int) : EnforcementResult()
    data class Ignored(val packageName: String?, val reason: IgnoreReason) : EnforcementResult()
    data class LaunchFailed(val targetPackage: String, val error: String) : EnforcementResult()
}

/**
 * Interface abstracting activity launching to ensure JVM unit testability.
 */
fun interface FocusRescueRedirectLauncher {
    fun launchFocusRescue(targetPackage: String): Boolean
}

/**
 * Default Android implementation that launches MainActivity with single-top Focus Rescue destination.
 */
class DefaultFocusRescueRedirectLauncher(
    private val contextProvider: () -> Context?
) : FocusRescueRedirectLauncher {
    override fun launchFocusRescue(targetPackage: String): Boolean {
        val context = contextProvider() ?: return false
        return try {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "FOCUS_RESCUE")
                putExtra("restricted_package", targetPackage)
            }
            context.startActivity(intent)
            true
        } catch (e: Throwable) {
            try {
                Log.e("FocusRescueEnforcer", "Error starting Focus Rescue activity: ${e.message}")
            } catch (_: Throwable) { }
            false
        }
    }
}

/**
 * Dedicated Focus Rescue enforcement engine.
 *
 * Responsibilities:
 * - Detects foreground package from accessibility window state changes.
 * - Queries central [StudyRescuePolicy].
 * - If Focus Rescue is active and package is restricted: triggers safe redirect to Focus Rescue screen.
 * - If package is allowed or Focus Rescue is inactive: allows normally with zero interference.
 * - Prevents loops and rapid re-triggers via policy cooldown and max consecutive redirects.
 * - Completely isolated from behavioral tracking logic.
 */
class FocusRescueEnforcer(
    private val policyProvider: () -> StudyRescuePolicy?,
    private val redirectLauncher: FocusRescueRedirectLauncher
) {
    companion object {
        private const val TAG = "FocusRescueEnforcer"
    }

    /**
     * Evaluates a foreground window event and enforces Focus Rescue if necessary.
     *
     * @param packageName The package name of the foreground window.
     * @param className Optional class name of the window.
     * @param timestamp Current timestamp.
     * @return [EnforcementResult] describing the action taken.
     */
    fun onWindowEvent(
        packageName: String?,
        className: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): EnforcementResult {
        if (packageName.isNullOrBlank()) {
            return EnforcementResult.Ignored(packageName, IgnoreReason.NULL_OR_BLANK_PACKAGE)
        }

        val policy = policyProvider()
            ?: return EnforcementResult.Ignored(packageName, IgnoreReason.NO_ACTIVE_SESSION)

        // Evaluate policy
        val decision = policy.evaluate(packageName, timestamp)

        return when (decision) {
            is PolicyDecision.Allow -> {
                EnforcementResult.Allowed(decision.packageName, decision.reason)
            }
            is PolicyDecision.Ignore -> {
                EnforcementResult.Ignored(decision.packageName, decision.reason)
            }
            is PolicyDecision.Redirect -> {
                safeLogI(TAG, "Restricted app detected during Focus Rescue: ${decision.targetPackage}. Redirecting (attempt #${decision.redirectCount}).")
                val success = redirectLauncher.launchFocusRescue(decision.targetPackage)
                if (success) {
                    policy.recordRedirectSuccess(decision.targetPackage, timestamp)
                    EnforcementResult.Redirected(decision.targetPackage, decision.redirectCount)
                } else {
                    safeLogE(TAG, "Failed to launch Focus Rescue screen for ${decision.targetPackage}.")
                    EnforcementResult.LaunchFailed(decision.targetPackage, "Failed to launch Activity")
                }
            }
        }
    }

    private fun safeLogI(tag: String, message: String) {
        try {
            Log.i(tag, message)
        } catch (_: Throwable) {
            // Unmocked in unit test environment
        }
    }

    private fun safeLogE(tag: String, message: String) {
        try {
            Log.e(tag, message)
        } catch (_: Throwable) {
            // Unmocked in unit test environment
        }
    }
}
