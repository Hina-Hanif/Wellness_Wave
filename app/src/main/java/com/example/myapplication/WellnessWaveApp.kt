package com.example.myapplication

import android.app.Application
import android.util.Log
import com.example.myapplication.data.revenuecat.RevenueCatManager

class WellnessWaveApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Initializing Wellness Wave Application...")

        // Initialize RevenueCat Purchases SDK at application startup
        RevenueCatManager.initialize(this, BuildConfig.REVENUECAT_API_KEY)
    }

    companion object {
        private const val TAG = "WellnessWaveApp"
    }
}
