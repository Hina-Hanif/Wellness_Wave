package com.example.myapplication.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.models.HistoricalPrediction
import com.example.myapplication.models.PredictionHistoryResponse
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TrendsHistoryScreen(innerPadding: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var historyData by remember { mutableStateOf<PredictionHistoryResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val sharedPrefs = context.getSharedPreferences("wellness_wave_prefs", Context.MODE_PRIVATE)
                val userId = sharedPrefs.getString("user_id", "default_user") ?: "default_user"
                
                val response = RetrofitClient.instance.getHistory(userId)
                if (response.isSuccessful) {
                    historyData = response.body()
                }
            } catch (e: Exception) {
                // Ignore for layout previews
            } finally {
                isLoading = false
            }
        }
    }

    // Usage Intensity: Past 7 Days only (Higher = More Activity)
    val weeklyHistory = historyData?.history?.takeLast(7) ?: emptyList()
    
    // Usage points (raw stress_score reflects intensity)
    val usagePoints = if (weeklyHistory.isNotEmpty()) {
        weeklyHistory.map { it.stress_score }
    } else {
        listOf(0.4f, 0.5f, 0.3f, 0.4f, 0.6f, 0.2f, 0.25f) // Realistic 7-day default
    }

    // Dynamic Weekly Status Calculation
    val firstTwoAvg = if (usagePoints.size >= 4) (usagePoints[0] + usagePoints[1]) / 2f else 0.5f
    val lastTwoAvg = if (usagePoints.size >= 2) (usagePoints[usagePoints.size - 1] + usagePoints[usagePoints.size - 2]) / 2f else 0.6f
    
    val improvementStatus = when {
        lastTwoAvg < firstTwoAvg - 0.08f -> "Improving" // Lower usage = Improving
        lastTwoAvg > firstTwoAvg + 0.08f -> "Declining" // Higher usage = Declining
        else -> "Stable"
    }

    // Calculate Smart Weekly Highlights
    val bestDay = weeklyHistory.minByOrNull { it.stress_score }
    val riskDay = weeklyHistory.maxByOrNull { it.stress_score }

    val weeklySummary = when (improvementStatus) {
        "Improving" -> "Your phone usage is improving this week. More mindful sessions detected."
        "Declining" -> "Irregular patterns detected in recent days. Consider more frequent screen breaks."
        else -> "Stable and balanced usage observed throughout the week."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(innerPadding)
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Trends History",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Weekly behavior analysis",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Digital Usage Trend (Bar Chart)
        AnalyticsCard(
            title = "Digital Usage Trend",
            subtitle = "Past 7 Days",
            value = improvementStatus,
            valueColor = when(improvementStatus) {
                "Improving" -> Color(0xFF00C853)
                "Declining" -> Color(0xFFFF5252)
                else -> MaterialTheme.colorScheme.primary
            }
        ) {
            Column {
                BarChartCanvas(
                    points = usagePoints,
                    barColor = if (improvementStatus == "Declining") Color(0xFFFF5252) else MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // X-Axis Day + Date Labels
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (weeklyHistory.isNotEmpty()) {
                        weeklyHistory.forEach { day ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = formatToShortDay(day.date),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f) // Increased visibility
                                )
                                Text(
                                    text = formatToShortDate(day.date),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 7.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) // Increased visibility
                                )
                            }
                        }
                    } else {
                        // Demo day + date labels (accurate relative to today)
                        val calendar = Calendar.getInstance()
                        // Move calendar back 6 days to start from the beginning of the week view
                        calendar.add(Calendar.DAY_OF_YEAR, -6)
                        
                        val demoFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
                        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())

                        (0..6).forEach { _ ->
                            val demoDate = demoFormat.format(calendar.time)
                            val dayName = dayFormat.format(calendar.time)
                            
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = dayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f) // Increased visibility
                                )
                                Text(
                                    text = demoDate,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 7.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) // Increased visibility
                                )
                            }
                            calendar.add(Calendar.DAY_OF_YEAR, 1)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        // Behavioral Summary Text
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = weeklySummary,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "SMART HIGHLIGHTS",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Best Day Card (Numeric-free)
        HighlightCard(
            title = "Best Day: ${formatToDayName(bestDay?.date ?: "Saturday")}${if (bestDay != null) ", " + formatToShortDate(bestDay.date) else ", " + SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date())}",
            subtitle = "Optimal digital balance detected. Your interaction patterns were consistent and relaxed throughout the day.",
            icon = Icons.Rounded.CheckCircle,
            iconColor = Color(0xFF00C853),
            backgroundColor = Color(0xFF00C853).copy(alpha = 0.1f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Risk / Focus Day Card (Numeric-free)
        HighlightCard(
            title = "Focus Day: ${formatToDayName(riskDay?.date ?: "Wednesday")}${if (riskDay != null) ", " + formatToShortDate(riskDay.date) else ", " + SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(System.currentTimeMillis() - 86400000L))}",
            subtitle = "Elevated interaction fatigue detected. High app migration and fragmented sessions indicate a need for more rest.",
            icon = Icons.Rounded.Warning,
            iconColor = Color(0xFFFF5252),
            backgroundColor = Color(0xFFFF5252).copy(alpha = 0.1f)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "DAILY BEHAVIORAL LOGS",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Last 7 Days according to ML model
        if (weeklyHistory.isNotEmpty()) {
            weeklyHistory.reversed().forEach { day ->
                val mlState = getMLStateFromScore(day.stress_score)
                DailyLogItem(
                    dateStr = day.date,
                    state = mlState.label,
                    stateColor = mlState.color,
                    insight = mlState.insight
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        } else {
            // Fallback for demo/new users if database is empty
            val demoDays = listOf(
                HistoricalPrediction(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()), 0.15f, 2f),
                HistoricalPrediction(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(System.currentTimeMillis() - 86400000)), 0.45f, 4f)
            )
            demoDays.forEach { day ->
                val mlState = getMLStateFromScore(day.stress_score)
                DailyLogItem(
                    dateStr = day.date,
                    state = mlState.label,
                    stateColor = mlState.color,
                    insight = mlState.insight
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            Text(
                text = "More history will appear as you continue using the app.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(90.dp))
    }
}

@Composable
fun HighlightCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    backgroundColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = backgroundColor,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = iconColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

fun formatToDayName(dateStr: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = sdf.parse(dateStr)
        val daySdf = SimpleDateFormat("EEEE", Locale.getDefault())
        daySdf.format(date ?: Date())
    } catch (e: Exception) {
        dateStr
    }
}

data class MLState(val label: String, val color: Color, val insight: String)

fun getMLStateFromScore(score: Float): MLState {
    return when {
        score <= 0.20f -> MLState("Balanced", Color(0xFF00C853), "Optimal daily rhythm")
        score <= 0.40f -> MLState("Stress", Color(0xFFFBC02D), "Elevated interaction arousal")
        score <= 0.60f -> MLState("Anxiety", Color(0xFFFF9100), "Fragmented behavioral focus")
        score <= 0.80f -> MLState("Burnout", Color(0xFFFF5252), "Prolonged interaction fatigue")
        else -> MLState("Addiction", Color(0xFFE040FB), "Compulsive social usage")
    }
}

@Composable
fun DailyLogItem(dateStr: String, state: String, stateColor: Color, insight: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatToShortDay(dateStr),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatToShortDate(dateStr),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = insight,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Surface(
                color = stateColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = state,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = stateColor
                )
            }
        }
    }
}

fun formatToShortDate(dateStr: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = sdf.parse(dateStr)
        val shortSdf = SimpleDateFormat("MMM dd", Locale.getDefault())
        shortSdf.format(date ?: Date())
    } catch (e: Exception) {
        dateStr
    }
}

fun formatToShortDay(dateStr: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = sdf.parse(dateStr)
        val daySdf = SimpleDateFormat("EEE", Locale.getDefault())
        daySdf.format(date ?: Date())
    } catch (e: Exception) {
        if (dateStr.length >= 3) dateStr.take(3) else dateStr
    }
}
@Composable
fun BarChartCanvas(points: List<Float>, barColor: Color) {
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(130.dp)) {
        val barCount = points.size
        val canvasWidth = size.width
        val canvasHeight = size.height
        
        val barWidth = (canvasWidth / barCount) * 0.6f
        val spacing = (canvasWidth / barCount)
        
        points.forEachIndexed { i, score ->
            val barHeight = canvasHeight * score.coerceIn(0.05f, 1.0f)
            val x = (i * spacing) + (spacing - barWidth) / 2
            val y = canvasHeight - barHeight
            
            drawRoundRect(
                color = barColor.copy(alpha = if (score > 0.7f) 1.0f else 0.7f),
                topLeft = androidx.compose.ui.geometry.Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
            )
        }
    }
}
