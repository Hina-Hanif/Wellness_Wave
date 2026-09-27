package com.example.myapplication.data.tracking

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages user preferences for Study Rescue and Focus Rescue selected restricted apps.
 * Thread-safe, avoids storing sensitive data, and never silently mutates user configurations.
 */
class FocusRescuePolicyPreferences(private val context: Context?) {

    companion object {
        private const val PREFS_NAME = "study_rescue_policy_prefs"
        private const val KEY_SELECTED_RESTRICTED_APPS = "key_selected_restricted_apps"
        private const val KEY_ALLOW_EDUCATIONAL_APPS = "key_allow_educational_apps"

        // Default candidate list presented for user selection (NOT enforced unless selected)
        val DEFAULT_CANDIDATE_PACKAGES = setOf(
            "com.instagram.android",
            "com.google.android.youtube",
            "com.zhiliaoapp.musically",
            "com.twitter.android",
            "com.reddit.frontpage",
            "com.facebook.katana"
        )
    }

    private fun getPrefs(): SharedPreferences? {
        val appContext = context?.applicationContext ?: context
        return appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Retrieves the set of restricted app packages explicitly chosen by the user.
     * Sanitizes results to ensure no system-critical packages are stored or returned.
     * Safely handles corrupted preference types by defaulting to emptySet().
     */
    fun getSelectedRestrictedPackages(): Set<String> {
        val prefs = getPrefs() ?: return emptySet()
        return try {
            val raw = prefs.getStringSet(KEY_SELECTED_RESTRICTED_APPS, null) ?: return emptySet()
            PackageNameValidator.sanitizeRestrictedPackages(raw)
        } catch (_: Throwable) {
            emptySet()
        }
    }

    /**
     * Persists the user's selected restricted app packages.
     * Sanitizes before saving to prevent storing protected system packages.
     */
    fun setSelectedRestrictedPackages(packages: Set<String>) {
        val prefs = getPrefs() ?: return
        val sanitized = PackageNameValidator.sanitizeRestrictedPackages(packages)
        prefs.edit()
            .putStringSet(KEY_SELECTED_RESTRICTED_APPS, sanitized)
            .apply()
    }

    /**
     * Whether educational and productivity apps are automatically permitted.
     * Defaults to true if preference is corrupted or missing.
     */
    fun isEducationalAppsAllowed(): Boolean {
        val prefs = getPrefs() ?: return true
        return try {
            prefs.getBoolean(KEY_ALLOW_EDUCATIONAL_APPS, true)
        } catch (_: Throwable) {
            true
        }
    }

    fun setEducationalAppsAllowed(allowed: Boolean) {
        val prefs = getPrefs() ?: return
        prefs.edit()
            .putBoolean(KEY_ALLOW_EDUCATIONAL_APPS, allowed)
            .apply()
    }

    /**
     * Converts persistent preferences into a runtime [FocusRescueConfig].
     */
    fun toConfig(): FocusRescueConfig {
        return FocusRescueConfig(
            restrictedPackages = getSelectedRestrictedPackages(),
            allowEducationalApps = isEducationalAppsAllowed()
        )
    }
}
