package com.example.myapplication.data.tracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ServiceConnectionManager {
    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected

    fun setConnected(connected: Boolean) {
        _isServiceConnected.value = connected
    }
}
