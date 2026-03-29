package com.example.myapplication.data.analysis

import android.content.Context

object ReminderRepository {
    private val messages = listOf(
        "Take a moment to breathe, you don’t need your phone right now.",
        "Give your mind a short break—step away from the screen.",
        "You’re doing great. Pause and reset for a minute.",
        "Let your eyes rest. Come back refreshed.",
        "Focus on the world around you for five minutes.",
        "Hydrate and stretch. Your body will thank you.",
        "Close your eyes and take three deep breaths.",
        "Notice one thing you're grateful for right now.",
        "Your peace is more important than the latest scroll.",
        "Step outside for a moment and feel the air.",
        "Listen to the sounds around you. Be present.",
        "Savor this moment without a digital interface.",
        "You've worked hard. Relax your shoulders.",
        "Mindfulness is a journey. You're doing perfectly."
    )

    fun getNextReminder(context: Context): String {
        val prefs = context.getSharedPreferences("wellness_wave_prefs", Context.MODE_PRIVATE)
        val currentIndex = prefs.getInt("last_reminder_index", 0)
        val nextIndex = (currentIndex + 1) % messages.size
        
        prefs.edit().putInt("last_reminder_index", nextIndex).apply()
        return messages[nextIndex]
    }

    fun getCurrentReminder(context: Context): String {
        val prefs = context.getSharedPreferences("wellness_wave_prefs", Context.MODE_PRIVATE)
        val index = prefs.getInt("last_reminder_index", 0)
        return messages[index]
    }
}
