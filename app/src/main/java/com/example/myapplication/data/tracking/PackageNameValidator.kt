package com.example.myapplication.data.tracking

import android.content.Context
import android.content.pm.PackageManager
import java.util.regex.Pattern

/**
 * Validates, categorizes, and sanitizes Android package names.
 * Ensures system critical functions, emergency calling, settings,
 * and Wellness Wave itself can NEVER be restricted under any circumstances.
 */
object PackageNameValidator {

    private const val MAX_PACKAGE_NAME_LENGTH = 255

    // Standard Android package syntax: segment.segment with at least 2 segments
    private val PACKAGE_NAME_PATTERN = Pattern.compile(
        "^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+$"
    )

    /**
     * Strictly verifies whether a string constitutes a syntactically valid Android package name.
     * Prevents wildcards, nulls, injection attempts, and malformed strings.
     */
    fun isValidPackageName(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        val trimmed = pkg.trim()
        if (trimmed.length > MAX_PACKAGE_NAME_LENGTH) return false
        return PACKAGE_NAME_PATTERN.matcher(trimmed).matches()
    }

    /**
     * Determines whether a package belongs to critical system categories
     * that must NEVER be blocked, restricted, or intercepted.
     */
    fun isProtectedPackage(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return true
        val lower = pkg.trim().lowercase()

        // 1. Core Android framework
        if (lower == "android" || lower == "com.android.systemui") {
            return true
        }

        // 2. Wellness Wave application itself & Focus Rescue screen
        if (lower == "com.example.myapplication") {
            return true
        }

        // 3. Emergency calling, Telephony, and Phone dialers
        if (lower == "com.android.phone" ||
            lower == "com.google.android.dialer" ||
            lower == "com.samsung.android.dialer" ||
            lower == "com.android.server.telecom" ||
            lower == "com.android.incallui" ||
            lower.contains("emergency") ||
            lower.contains("telecom") ||
            lower.contains("dialer") ||
            lower.contains("incallui")
        ) {
            return true
        }

        // 4. System Settings (MUST be accessible so user can inspect/revoke accessibility permission)
        if (lower == "com.android.settings" ||
            lower == "com.google.android.settings" ||
            lower.startsWith("com.android.settings.")
        ) {
            return true
        }

        // 5. System Input Methods (Keyboards)
        if (lower.contains("inputmethod") ||
            lower.contains("keyboard") ||
            lower.contains("ime") ||
            lower.contains("gboard") ||
            lower.contains("swiftkey") ||
            lower.contains("honeyboard")
        ) {
            return true
        }

        // 6. Home Launchers (ensures device navigation is always possible)
        if (lower.contains("launcher") ||
            lower.contains("trebuchet") ||
            lower.contains("pixelui") ||
            lower == "com.miui.home" ||
            lower == "com.huawei.android.launcher"
        ) {
            return true
        }

        return false
    }

    /**
     * Sanitizes a collection of user-selected packages.
     * Removes invalid, malformed, and protected packages without altering
     * the legitimate choices made by the user.
     */
    fun sanitizeRestrictedPackages(packages: Collection<String>?): Set<String> {
        if (packages == null) return emptySet()
        return packages
            .map { it.trim() }
            .filter { isValidPackageName(it) && !isProtectedPackage(it) }
            .toSet()
    }

    /**
     * Safely verifies whether a package is installed on the current device.
     * Returns true if Context is null (e.g. JVM tests) or if package is installed.
     * Never throws NameNotFoundException or security exceptions.
     */
    fun safeIsPackageInstalled(context: Context?, packageName: String?): Boolean {
        if (context == null || packageName.isNullOrBlank()) return true
        return try {
            context.packageManager.getPackageInfo(packageName.trim(), 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Throwable) {
            // In case of SecurityException or query visibility restrictions on Android 11+
            true
        }
    }
}
