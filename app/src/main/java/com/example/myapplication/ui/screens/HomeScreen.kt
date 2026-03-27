package com.example.myapplication.ui.screens

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.models.PredictionResponse
import com.example.myapplication.data.tracking.BehavioralAccessibilityService
import kotlinx.coroutines.launch
import java.util.Calendar
import android.app.usage.UsageStatsManager

@Composable
fun HomeScreen(innerPadding: PaddingValues, onNavigateSettings: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var prediction by remember { mutableStateOf<PredictionResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var todayScreenTime by remember { mutableStateOf("0m") }
    var screenTimeMinutes by remember { mutableStateOf(0L) }
    var currentTime by remember { mutableStateOf("--:-- AM") }

    // Live Sensors
    val typingCps by BehavioralAccessibilityService.typingCps.collectAsState()
    val typingWpm = (typingCps * 60f).toInt().coerceAtLeast(0)
    val scrollVelocity by BehavioralAccessibilityService.scrollVelocityAvg.collectAsState()
    val appSwitches by BehavioralAccessibilityService.switchCount.collectAsState()

    LaunchedEffect(Unit) {
        while(true) {
            currentTime = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
            kotlinx.coroutines.delay(1000)
        }
    }

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 0..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    // Fetch AI prediction
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val sharedPrefs = context.getSharedPreferences("wellness_wave_prefs", Context.MODE_PRIVATE)
                val userId = sharedPrefs.getString("user_id", "default_user") ?: "default_user"

                val response = RetrofitClient.instance.getPrediction(userId)
                if (response.isSuccessful) {
                    prediction = response.body()
                }
            } catch (e: Exception) {
                // Ignore for now
            } finally {
                isLoading = false
            }
        }
    }

    // Fetch today's screen time periodically
    LaunchedEffect(Unit) {
        val usageMonitor = com.example.myapplication.UsageMonitor(context)
        while (true) {
            val mins = usageMonitor.getTodayScreenTimeMinutes()
            screenTimeMinutes = mins
            val hours = (mins / 60).toInt()
            val minutes = (mins % 60).toInt()
            todayScreenTime = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
            kotlinx.coroutines.delay(60000) // Update every minute
        }
    }

    val isConnected by com.example.myapplication.data.tracking.ServiceConnectionManager.isServiceConnected.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(innerPadding).padding(horizontal = 24.dp).verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Header
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(text = greeting, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(text = remember { java.text.SimpleDateFormat("EEEE, MMM d", java.util.Locale.getDefault()).format(java.util.Date()) }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(text = "Today • $currentTime", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row {
                IconButton(onClick = { 
                    val syncRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.myapplication.data.network.DataSyncWorker>().build()
                    androidx.work.WorkManager.getInstance(context).enqueue(syncRequest)
                }) { Icon(Icons.Default.Sync, contentDescription = "Sync") }
                IconButton(onClick = onNavigateSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Dynamic Behavioral Scoring System
        val wellbeingState = remember(appSwitches, scrollVelocity, screenTimeMinutes, typingWpm) {
            when {
                screenTimeMinutes > 300 -> "Overuse Risk"
                appSwitches > 40 || scrollVelocity > 1200f -> "High Distraction"
                typingWpm < 10 && scrollVelocity < 100f && screenTimeMinutes > 10 -> "Low Activity"
                else -> "Balanced"
            }
        }

        val wellbeingFeedback = remember(wellbeingState) {
            when (wellbeingState) {
                "Overuse Risk" -> "Screen time is exceptionally high. Consider taking a long break to rest your eyes and mind."
                "High Distraction" -> "Frequent app switching and rapid scrolling detected. Try focusing on one task at a time."
                "Low Activity" -> "Activity is unusually low. Taking it easy today, or feeling fatigued?"
                else -> "Your digital habits are looking well-balanced and healthy. Keep it up!"
            }
        }

        // AI Intelligence Card
        val statusColor = when (wellbeingState) {
            "Overuse Risk", "High Distraction" -> Color(0xFFFF5252)
            "Low Activity" -> Color(0xFFFFAB40)
            "Balanced" -> Color(0xFF00E37C)
            else -> MaterialTheme.colorScheme.primary
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = statusColor)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(text = "LIVE DYNAMIC INSIGHT", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))
                Text(text = wellbeingState, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = Color.White)
                
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IndicatorBadge("Distraction", wellbeingState == "High Distraction")
                    IndicatorBadge("Overuse", wellbeingState == "Overuse Risk")
                    IndicatorBadge("Fatigue", wellbeingState == "Low Activity")
                }

                Spacer(modifier = Modifier.height(20.dp))
                Surface(color = Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(text = wellbeingFeedback, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(text = "LIVE SENSORS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(16.dp))

        // Grid Layer 1
        Row(modifier = Modifier.fillMaxWidth()) {
            MetricCard(modifier = Modifier.weight(1f), title = "Screen Time", value = todayScreenTime, icon = Icons.Rounded.PhoneAndroid, trend = "Total Today", trendPositive = false)
            Spacer(modifier = Modifier.width(16.dp))
            MetricCard(modifier = Modifier.weight(1f), title = "Scroll Speed", value = "${scrollVelocity.toInt()} px/s", icon = Icons.Rounded.Height, trend = if (scrollVelocity > 500) "Erratic" else "Smooth", trendPositive = scrollVelocity < 500)
        }
        Spacer(modifier = Modifier.height(16.dp))
        // Grid Layer 2
        Row(modifier = Modifier.fillMaxWidth()) {
            MetricCard(modifier = Modifier.weight(1f), title = "Focus Fragmentation", value = "$appSwitches switches", icon = Icons.Rounded.Apps, trend = if (appSwitches > 50) "High" else "Stable", trendPositive = appSwitches < 50)
            Spacer(modifier = Modifier.width(16.dp))
            MetricCard(modifier = Modifier.weight(1f), title = "Confidence", value = "${((prediction?.confidence_score ?: 0.85f) * 100).toInt()}%", icon = Icons.Rounded.Psychology, trend = "V3 Ensemble", trendPositive = true)
        }

        Spacer(modifier = Modifier.height(90.dp))
    }
}

@Composable
fun IndicatorBadge(label: String, isActive: Boolean) {
    Surface(color = if (isActive) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).background(if (isActive) Color.White else Color.White.copy(alpha = 0.4f), CircleShape))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun MetricCard(modifier: Modifier = Modifier, title: String, value: String, icon: ImageVector, trend: String, trendPositive: Boolean) {
    Card(modifier = modifier.aspectRatio(1f), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.padding(8.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column {
                Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(text = trend, style = MaterialTheme.typography.labelSmall, color = if (trendPositive) MaterialTheme.colorScheme.primary else Color(0xFFFF5252), fontSize = 10.sp)
            }
        }
    }
}