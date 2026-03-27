package com.example.myapplication.ui.screens

import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.data.tracking.BehavioralAccessibilityService
import com.example.myapplication.data.tracking.NotificationReactionTracker
import com.example.myapplication.models.PredictionHistoryResponse
import com.example.myapplication.models.PredictionResponse
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

@Composable
fun BehaviorAnalyticsScreen(innerPadding: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val typingCps by BehavioralAccessibilityService.typingCps.collectAsState()
    val typingPauses by BehavioralAccessibilityService.typingPausesCount.collectAsState()
    val scrollVelocity by BehavioralAccessibilityService.scrollVelocityAvg.collectAsState()
    val scrollErraticness by BehavioralAccessibilityService.scrollErraticness.collectAsState()
    
    // Remote data
    var historyData by remember { mutableStateOf<PredictionHistoryResponse?>(null) }
    var prediction by remember { mutableStateOf<PredictionResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var currentTime by remember { mutableStateOf("--:-- AM") }

    LaunchedEffect(Unit) {
        while(true) {
            currentTime = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
            kotlinx.coroutines.delay(1000)
        }
    }

    // Live computed values from Accessibility Service
    val typingWpm = (typingCps * 60f).toInt().coerceAtLeast(0)
    val hesitation by BehavioralAccessibilityService.typingHesitationMs.collectAsState()
    val avgPauseSec = (hesitation / 1000.0).let { if (it < 0.1) 0.0 else it }
    val appSwitches by BehavioralAccessibilityService.switchCount.collectAsState()
    val notifResponse by NotificationReactionTracker.averageResponseSec.collectAsState()

    // Screen Time from UsageStats
    var screenTimeText by remember { mutableStateOf("--") }
    var nightUsageText by remember { mutableStateOf("--") }
    var screenTimeMinutes by remember { mutableStateOf(0L) }
 
    LaunchedEffect(Unit) {
        try {
            val monitor = com.example.myapplication.UsageMonitor(context)
            val total = monitor.getTodayScreenTimeMinutes()
            val night = monitor.getNightUsageMinutes()
            
            screenTimeMinutes = total
            screenTimeText = if (total >= 60) "${total/60}h ${total%60}m" else "${total}m"
            nightUsageText = if (night >= 60) "${night/60}h ${night%60}m" else "${night}m"
        } catch (_: Exception) { 
            screenTimeText = "N/A"
            nightUsageText = "0m"
        }

        scope.launch {
            try {
                val prefs = context.getSharedPreferences("wellness_wave_prefs", Context.MODE_PRIVATE)
                val userId = prefs.getString("user_id", "default_user") ?: "default_user"

                val predResponse = RetrofitClient.instance.getPrediction(userId)
                if (predResponse.isSuccessful) prediction = predResponse.body()

                val histResponse = RetrofitClient.instance.getHistory(userId)
                if (histResponse.isSuccessful) historyData = histResponse.body()
            } catch (_: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    val screenTimePoints = historyData?.history?.map {
        (it.screen_time_hours / 12f).coerceIn(0.1f, 1f)
    } ?: listOf(0.4f, 0.5f, 0.3f, 0.7f, 0.8f, 0.5f, 0.45f)

    val isConnected by com.example.myapplication.data.tracking.ServiceConnectionManager.isServiceConnected.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(innerPadding)
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Connection Status Banner
        if (!isConnected) {
            Surface(
                color = Color(0xFFFF5252).copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().clickable {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                }
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Rounded.Warning, contentDescription = null, tint = Color(0xFFD50000))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = "Tracking Service Disabled", style = MaterialTheme.typography.labelLarge, color = Color(0xFFD50000), fontWeight = FontWeight.Bold)
                        Text(text = "Tap to enable 'Wellness Wave AI' to start tracking.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFD50000).copy(alpha = 0.8f))
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Header
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Behavioral Tracking", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp)) {
                Text(text = currentTime, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
        Text(text = "Deep dive into your digital habits", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(24.dp))

        // Typing Intelligence (Tri-Bar)
        TypingMetricsChart(wpm = typingWpm, cps = typingCps, pauses = typingPauses)
        
        Spacer(modifier = Modifier.height(24.dp))

        // Mental State Card
        MentalStatePredictionCard(prediction = prediction, isLoading = isLoading, screenTimeMinutes = screenTimeMinutes, nightUsageText = nightUsageText)

        Spacer(modifier = Modifier.height(24.dp))
        SectionLabel(text = "BEHAVIORAL TRACKING")
        Spacer(modifier = Modifier.height(16.dp))

        AnalyticsCard(title = "Screen Time", subtitle = "Today's Usage", value = screenTimeText, valueColor = MaterialTheme.colorScheme.primary) {
            LineChartCanvas(points = screenTimePoints, lineColor = MaterialTheme.colorScheme.primary)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            SmallStatCard(modifier = Modifier.weight(1f), title = "Night Usage", value = nightUsageText, subtitle = if (nightUsageText != "0m") "Late habits" else "11PM–5AM", color = Color(0xFF7986CB), icon = Icons.Rounded.Nightlight)
            Spacer(modifier = Modifier.width(12.dp))
            SmallStatCard(modifier = Modifier.weight(1f), title = "Avg Pause", value = if (avgPauseSec > 0) "${"%.1f".format(avgPauseSec)}s" else "0.0s", subtitle = "Hesitation", color = MaterialTheme.colorScheme.primary, icon = Icons.Rounded.Pause)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            SmallStatCard(modifier = Modifier.weight(1f), title = "Scroll Speed", value = if (scrollVelocity > 0) "${scrollVelocity.toInt()}" else "0", subtitle = if (scrollErraticness > 1.5f) "⚠ Erratic" else "Smooth", color = if (scrollErraticness > 1.5f) Color(0xFFFF8A65) else MaterialTheme.colorScheme.primary, icon = Icons.Rounded.SwipeVertical)
            Spacer(modifier = Modifier.width(12.dp))
            SmallStatCard(modifier = Modifier.weight(1f), title = "Focus Switches", value = "$appSwitches", subtitle = "App fragmentation", color = MaterialTheme.colorScheme.secondary, icon = Icons.Rounded.Apps)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Night Usage + Unlocks ─────────────────────────────────
        SectionLabel(text = "NATIVE USAGE (11 PM – 5 AM)")

        Spacer(modifier = Modifier.height(16.dp))

        NativeUsageRow(screenTimeMinutes = screenTimeMinutes)

        Spacer(modifier = Modifier.height(24.dp))

        // ── Behavioral Indicators ─────────────────────────────────
        SectionLabel(text = "BEHAVIORAL SIGNALS")

        Spacer(modifier = Modifier.height(12.dp))

        BehavioralIndicatorRow(
            label = "Typing Behavior",
            detail = "Speed, frequency, pause duration — content never accessed",
            icon = Icons.Rounded.Keyboard,
            color = MaterialTheme.colorScheme.primary
        )
        BehavioralIndicatorRow(
            label = "Scrolling Behavior",
            detail = "Speed, duration, erratic vs. smooth patterns",
            icon = Icons.Rounded.TouchApp,
            color = MaterialTheme.colorScheme.secondary
        )
        BehavioralIndicatorRow(
            label = "App Switching",
            detail = "Frequency and duration across app categories",
            icon = Icons.Rounded.Apps,
            color = Color(0xFF7986CB)
        )
        BehavioralIndicatorRow(
            label = "Notification Reactions",
            detail = "Response time and dismissal patterns",
            icon = Icons.Rounded.Notifications,
            color = Color(0xFFFF8A65)
        )

        Spacer(modifier = Modifier.height(90.dp))
    }
}

// ── Components ────────────────────────────────────────────────────────────────

@Composable
fun MentalStatePredictionCard(prediction: PredictionResponse?, isLoading: Boolean, screenTimeMinutes: Long, nightUsageText: String) {
    val stressColor = when (prediction?.stress_level) {
        "High", "Burnout", "Addiction" -> Color(0xFFFF5252)
        "Medium", "Anxiety", "Stress" -> Color(0xFFFFAB40)
        "Low", "Balanced" -> Color(0xFF00C853)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(text = "AI INSIGHT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = if (isLoading) "Analyzing..." else (prediction?.stress_level ?: "Balanced"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = stressColor)
                }
                if (prediction != null) {
                    Text(text = "${(prediction.confidence_score * 100).toInt()}% CONF", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = stressColor)
                }
            }
            if (prediction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = prediction.real_time_feedback, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
            }
        }
    }
}

data class TypingEntry(val label: String, val speed: Float, val freq: Float, val rhythm: Float)

@Composable
fun TypingMetricsChart(wpm: Int, cps: Float, pauses: Int) {
    val timeLabels = remember {
        val calendar = Calendar.getInstance()
        val formatter = java.text.SimpleDateFormat("h a", java.util.Locale.getDefault())
        List(4) { i ->
            val tempCal = calendar.clone() as Calendar
            tempCal.add(Calendar.HOUR_OF_DAY, -(9 - (i * 3)))
            formatter.format(tempCal.time)
        }
    }

    val entries = listOf(
        TypingEntry(timeLabels[0], 0.65f, 0.40f, 0.85f),
        TypingEntry(timeLabels[1], 0.45f, 0.35f, 0.70f),
        TypingEntry(timeLabels[2], 0.75f, 0.60f, 0.90f),
        TypingEntry(timeLabels[3], (wpm / 100f).coerceIn(0.1f, 1f), (cps * 10f / 50f).coerceIn(0.1f, 1f), (1f - (pauses * 0.1f)).coerceIn(0.1f, 1f))
    )

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = "Typing Intelligence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = "Speed • Frequency • Rhythm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                entries.forEach { entry ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center, modifier = Modifier.height(110.dp)) {
                            MetricBar(entry.speed, Color(0xFF00E37C))
                            Spacer(modifier = Modifier.width(2.dp))
                            MetricBar(entry.freq, Color(0xFF00BCD4))
                            Spacer(modifier = Modifier.width(2.dp))
                            MetricBar(entry.rhythm, Color(0xFF26A69A))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = entry.label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBar(heightProgress: Float, color: Color) {
    Box(modifier = Modifier.width(6.dp).fillMaxHeight(heightProgress).clip(RoundedCornerShape(3.dp)).background(color))
}

@Composable
fun SectionLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
}

@Composable
fun AnalyticsCard(title: String, subtitle: String, value: String, valueColor: Color, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = valueColor)
            }
            Spacer(modifier = Modifier.height(24.dp))
            content()
        }
    }
}

@Composable
fun SmallStatCard(modifier: Modifier = Modifier, title: String, value: String, subtitle: String = "", color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Card(modifier = modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
            if (subtitle.isNotEmpty()) Text(text = subtitle, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
        }
    }
}

@Composable
fun LineChartCanvas(points: List<Float>, lineColor: Color) {
    Canvas(modifier = Modifier.fillMaxWidth().height(130.dp)) {
        val spacing = size.width / (points.size - 1).coerceAtLeast(1)
        val path = Path().apply {
            moveTo(0f, size.height * (1 - points[0]))
            for (i in 1 until points.size) {
                cubicTo((i-1)*spacing + spacing/2, size.height*(1-points[i-1]), i*spacing - spacing/2, size.height*(1-points[i]), i*spacing, size.height*(1-points[i]))
            }
        }
        drawPath(path, lineColor, style = Stroke(4f))
    }
}

// ── Night Usage Row ────────────────────────────────────────────────────────────
@Composable
fun NativeUsageRow(screenTimeMinutes: Long) {
    val unlockEstimate = (screenTimeMinutes / 8).coerceAtLeast(1)
    val nightEstimateMin = (screenTimeMinutes * 0.12f).toInt()

    Row(modifier = Modifier.fillMaxWidth()) {
        NativeStatCard(
            modifier = Modifier.weight(1f),
            title = "Unlocks",
            value = "$unlockEstimate",
            sub = "Today",
            icon = Icons.Rounded.LockOpen,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        NativeStatCard(
            modifier = Modifier.weight(1f),
            title = "Night Usage",
            value = "${nightEstimateMin}m",
            sub = "11 PM – 5 AM",
            icon = Icons.Rounded.Nightlight,
            color = Color(0xFF7986CB)
        )
    }
}

@Composable
fun NativeStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    sub: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = color.copy(alpha = 0.12f), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
                Text(text = sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
            }
        }
    }
}

// ── Behavioral Indicator Row ─────────────────────────────────────────────────
@Composable
fun BehavioralIndicatorRow(label: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = CircleShape, color = color.copy(alpha = 0.1f), modifier = Modifier.size(36.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(text = detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 14.sp)
        }
    }
}
