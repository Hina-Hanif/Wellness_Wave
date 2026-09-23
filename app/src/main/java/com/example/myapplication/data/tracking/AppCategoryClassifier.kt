package com.example.myapplication.data.tracking

enum class AppCategory {
    SOCIAL,
    PRODUCTIVE,
    OTHER
}

object AppCategoryClassifier {

    private val socialPackages = setOf(
        "com.facebook.katana",
        "com.instagram.android",
        "com.twitter.android",
        "com.x.android",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.snapchat.android",
        "com.facebook.orca",
        "org.telegram.messenger",
        "com.reddit.frontpage",
        "com.whatsapp",
        "com.google.android.youtube"
    )

    private val productivePackages = setOf(
        "com.google.android.apps.docs",
        "com.google.android.apps.sheets",
        "com.google.android.apps.slides",
        "com.google.android.apps.drive",
        "com.google.android.gm",
        "com.microsoft.office.word",
        "com.microsoft.office.excel",
        "com.microsoft.office.powerpoint",
        "com.microsoft.office.officehubrow",
        "com.notion.id",
        "com.slack",
        "com.microsoft.teams",
        "us.zoom.videomeetings",
        "com.google.android.apps.meetings",
        "com.evernote",
        "com.todoist"
    )

    fun classify(packageName: String?): AppCategory {
        if (packageName.isNull_or_empty()) return AppCategory.OTHER
        return when {
            socialPackages.contains(packageName) -> AppCategory.SOCIAL
            productivePackages.contains(packageName) -> AppCategory.PRODUCTIVE
            else -> AppCategory.OTHER
        }
    }

    private fun String?.isNull_or_empty(): Boolean = this == null || this.isEmpty()
}
