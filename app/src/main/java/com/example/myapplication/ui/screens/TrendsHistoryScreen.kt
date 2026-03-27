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

    // Normalized scores for drawing the monthly trend chart (0.0 to 1.0)
    // We invert it so higher = better wellness (less stress)
    val scorePoints = historyData?.history?.map { 1.0f - it.stress_score } ?: listOf(0.6f, 0.4f, 0.6f, 0.5f, 0.4f, 0.7f, 0.6f, 0.2f, 0.5f, 0.8f)

    // Calculate Dynamic Highlights
    val bestDay = historyData?.history?.minByOrNull { it.stress_score }
    val focusDay = historyData?.history?.maxByOrNull { it.screen_time_hours }

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
            text = "Your journey over time",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Large Trend Card
        AnalyticsCard(
            title = "Wellness Score Trend",
            subtitle = "Past 30 Days",
            value = if (scorePoints.last() > scorePoints.first()) "Improving" else "Maintaining",
            valueColor = MaterialTheme.colorScheme.primary
        ) {
            LineChartCanvas(
                points = scorePoints,
                lineColor = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "HIGHLIGHTS",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Best Day Card
        HighlightCard(
            title = "Best Day: ${formatToDayName(bestDay?.date ?: "Saturday")}",
            subtitle = "Lowest stress detected. Behavioral patterns showed optimal balance and smooth typing.",
            icon = Icons.Rounded.CheckCircle,
            iconColor = MaterialTheme.colorScheme.primary,
            backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Focus Day Card
        HighlightCard(
            title = "Focus Day: ${formatToDayName(focusDay?.date ?: "Wednesday")}",
            subtitle = "Highest screen time recorded (${focusDay?.screen_time_hours ?: "4.2"}h). Risk of burnout detected in scrolling behavior.",
            icon = Icons.Rounded.Warning,
            iconColor = Color(0xFFFF5252),
            backgroundColor = Color(0xFFFF5252).copy(alpha = 0.1f)
        )

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
