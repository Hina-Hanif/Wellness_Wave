package com.example.myapplication.ui.screens

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.models.PredictionResponse
import com.example.myapplication.models.RecoverySummaryResponse
import com.example.myapplication.models.LabelRecoveryTrend
import com.example.myapplication.data.UsageDataManager
import com.example.myapplication.models.UsageMetrics
import com.example.myapplication.data.analysis.BehavioralInferenceEngine
import com.example.myapplication.data.analysis.MentalStateReport
import com.example.myapplication.data.tracking.BehavioralAccessibilityService
import kotlinx.coroutines.launch

@Composable
fun AIInsightsScreen(innerPadding: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var prediction by remember { mutableStateOf<PredictionResponse?>(null) }
    var recoverySummary by remember { mutableStateOf<RecoverySummaryResponse?>(null) }
    var localReport by remember { mutableStateOf<MentalStateReport?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var todayMetrics by remember { mutableStateOf<UsageMetrics?>(null) }
    
    val typingCps by BehavioralAccessibilityService.typingCps.collectAsState()
    val scrollVelocity by BehavioralAccessibilityService.scrollVelocityAvg.collectAsState()
    val scrollErraticness by BehavioralAccessibilityService.scrollErraticness.collectAsState()
    val appSwitches by BehavioralAccessibilityService.switchCount.collectAsState()
    
    val manager = remember { UsageDataManager(context) }
    
    LaunchedEffect(typingCps, scrollVelocity, scrollErraticness, appSwitches) {
        todayMetrics = manager.getDailyMetrics()
        todayMetrics?.let {
            localReport = BehavioralInferenceEngine.analyze(it)
        }
    }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val sharedPrefs = context.getSharedPreferences("wellness_wave_prefs", Context.MODE_PRIVATE)
                val userId = sharedPrefs.getString("user_id", "default_user") ?: "default_user"
                
                val response = RetrofitClient.instance.getPrediction(userId)
                if (response.isSuccessful) {
                    prediction = response.body()
                }

                val recResponse = RetrofitClient.instance.getRecoverySummary(userId)
                if (recResponse.isSuccessful) {
                    recoverySummary = recResponse.body()
                }
            } catch (e: Exception) {
                // Ignore API failure for UI layout tests
            } finally {
                isLoading = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(innerPadding),
        contentPadding = PaddingValues(24.dp)
    ) {
        item {
            Text(
                text = "AI Insights",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Your personalized behavioral analysis",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Current Stress Level Progress Bar Card
            StressMeterCard(prediction = prediction, localReport = localReport, isLoading = isLoading)

            Spacer(modifier = Modifier.height(32.dp))
            
            // Behavioral Analysis Detail Section (Grid of 4 Mental States)
            if (localReport != null) {
                BehavioralAnalysisCard(report = localReport!!)
                Spacer(modifier = Modifier.height(32.dp))
            }

            // Replaced Personalized Suggestions with YOUR RECOVERY TREND
            Text(
                text = "YOUR RECOVERY TREND",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            RecoveryTrendCard(recoverySummary = recoverySummary)

            Spacer(modifier = Modifier.height(90.dp))
        }
    }
}

@Composable
fun StressMeterCard(prediction: PredictionResponse?, localReport: MentalStateReport?, isLoading: Boolean) {
    val primaryText = localReport?.primaryState ?: prediction?.stress_level ?: (if (isLoading) "Analyzing..." else "Balanced")
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "AI",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Current State",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = primaryText,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val currentLevel = localReport?.overallLevel ?: "Low"
            val targetProgress = when (currentLevel) {
                "Low" -> 0.25f
                "Medium" -> 0.55f
                "High" -> 0.95f
                else -> 0.25f
            }

            val animatedProgress by animateFloatAsState(
                targetValue = if (isLoading) 0f else targetProgress,
                animationSpec = tween(1000, easing = FastOutSlowInEasing),
                label = "stressBar"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    if (targetProgress > 0.6f) Color(0xFFFFB300) else MaterialTheme.colorScheme.primary,
                                    if (targetProgress > 0.8f) Color(0xFFFF5252) else MaterialTheme.colorScheme.primary
                                )
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Relaxed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Overloaded", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = localReport?.overallSummary ?: prediction?.real_time_feedback ?: "Keep up the good habits. Your digital metrics look stable.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun BehavioralAnalysisCard(report: MentalStateReport) {
    Column {
        Text(
            text = "BEHAVIORAL MENTAL STATES",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f)) {
                MentalStateGridItem(
                    label = "Stress",
                    level = report.stressLevel,
                    insight = report.stressInsight,
                    icon = Icons.Rounded.AutoAwesome
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Box(modifier = Modifier.weight(1f)) {
                MentalStateGridItem(
                    label = "Anxiety",
                    level = report.anxietyLevel,
                    insight = report.anxietyInsight,
                    icon = Icons.Rounded.SelfImprovement
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f)) {
                MentalStateGridItem(
                    label = "Burnout",
                    level = report.burnoutLevel,
                    insight = report.burnoutInsight,
                    icon = Icons.Rounded.BatteryAlert
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Box(modifier = Modifier.weight(1f)) {
                MentalStateGridItem(
                    label = "Addiction",
                    level = report.addictionLevel,
                    insight = report.addictionInsight,
                    icon = Icons.Rounded.Warning
                )
            }
        }
    }
}

@Composable
private fun MentalStateGridItem(label: String, level: String, insight: String, icon: ImageVector) {
    val levelColor = when(level) {
        "High" -> Color(0xFFFF5252)
        "Medium" -> Color(0xFFFFB300)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = levelColor.copy(alpha = 0.1f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = icon, contentDescription = null, tint = levelColor, modifier = Modifier.size(18.dp))
                    }
                }
                
                Surface(
                    color = levelColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = level, 
                        style = MaterialTheme.typography.labelSmall, 
                        fontWeight = FontWeight.ExtraBold, 
                        color = levelColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = insight,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun RecoveryTrendCard(recoverySummary: RecoverySummaryResponse?) {
    val labels = listOf("Stress", "Anxiety", "Burnout", "Addiction")
    val trends = recoverySummary?.recovery_trends

    if (recoverySummary != null && !recoverySummary.has_sufficient_history) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Text(
                text = "Building your recovery baseline — check back next week.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp)
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            labels.forEach { label ->
                val trend = trends?.get(label)
                val spikesCount = trend?.spikes_this_week ?: 0
                val pctTrend = trend?.recovery_velocity_trend_pct
                val isFaster = trend?.is_faster

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (spikesCount > 0 && pctTrend != null && isFaster != null) {
                                val tintColor = if (isFaster) Color(0xFF4CAF50) else Color(0xFFFF9800)
                                Surface(
                                    color = tintColor.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isFaster) Icons.Rounded.TrendingUp else Icons.Rounded.TrendingDown,
                                            contentDescription = null,
                                            tint = tintColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "$pctTrend% ${if (isFaster) "faster" else "slower"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = tintColor
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (spikesCount == 0) {
                            Text(
                                text = "No $label spikes this week — your baseline has been stable.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "$spikesCount ${if (spikesCount == 1) "spike" else "spikes"} this week",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (trend?.avg_recovery_time_min != null) {
                                    Text(
                                        text = "Avg recovery: ${trend.avg_recovery_time_min} mins",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (!trend?.trigger_summary.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Trigger: ${trend?.trigger_summary}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
