package com.example.myapplication.ui.screens

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Spa
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
import com.example.myapplication.data.UsageDataManager
import com.example.myapplication.models.UsageMetrics
import com.example.myapplication.data.analysis.BehavioralInferenceEngine
import com.example.myapplication.data.analysis.MentalStateReport
import com.example.myapplication.data.tracking.BehavioralAccessibilityService
import com.example.myapplication.data.tracking.NotificationReactionTracker
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun AIInsightsScreen(innerPadding: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var prediction by remember { mutableStateOf<PredictionResponse?>(null) }
    var localReport by remember { mutableStateOf<MentalStateReport?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var todayMetrics by remember { mutableStateOf<UsageMetrics?>(null) }
    
    val typingCps by BehavioralAccessibilityService.typingCps.collectAsState()
    val scrollVelocity by BehavioralAccessibilityService.scrollVelocityAvg.collectAsState()
    val scrollErraticness by BehavioralAccessibilityService.scrollErraticness.collectAsState()
    val appSwitches by BehavioralAccessibilityService.switchCount.collectAsState()
    
    val manager = remember { UsageDataManager(context) }
    
    LaunchedEffect(typingCps, scrollVelocity, scrollErraticness, appSwitches) {
        // Fetch current daily metrics (Mood was removed as signals take priority)
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

            Text(
                text = "PERSONALIZED SUGGESTIONS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Dynamic Suggestions based on 4-Dimensional AI Output
        val suggestions = generateSuggestions(prediction, localReport)
        
        items(suggestions) { suggestion ->
            SuggestionCard(suggestion = suggestion)
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        item {
            Spacer(modifier = Modifier.height(90.dp))
        }
    }
}

@Composable
fun StressMeterCard(prediction: PredictionResponse?, localReport: MentalStateReport?, isLoading: Boolean) {
    // Priority: Local Behavioral Analysis (v7) > Remote Prediction > Default "Balanced"
    val primaryText = localReport?.primaryState ?: prediction?.stress_level ?: (if (isLoading) "Analyzing..." else "Balanced")
    
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "AI",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Current Overall Mental Health State",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = primaryText,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Determine Overall Level from calibrated engine
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

            // Custom thick progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
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
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = localReport?.overallSummary ?: prediction?.real_time_feedback ?: "Keep up the good habits. Your digital metrics look stable.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
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

data class Suggestion(val title: String, val description: String, val icon: ImageVector, val color: Color)

fun generateSuggestions(prediction: PredictionResponse?, localReport: MentalStateReport?): List<Suggestion> {
    if (prediction == null && localReport == null) {
        return listOf(
            Suggestion("2-Minute Breathing", "A quick exercise to center your focus.", Icons.Rounded.Spa, Color(0xFF61D0C9)),
            Suggestion("Screen Break", "Step away for 5 minutes.", Icons.Rounded.DirectionsRun, Color(0xFF6888F9))
        )
    }
    
    val list = mutableListOf<Suggestion>()
    
    // Check Levels from Local Analysis (High priority)
    val addiction = localReport?.addictionLevel ?: "Low"
    if (addiction != "Low") {
        list.add(Suggestion("Device Placement", "Place your phone in another room to reduce reflexive unlocks.", Icons.Rounded.Warning, if (addiction == "High") Color(0xFFFF5252) else Color(0xFFFFB300)))
    }
    
    val burnout = localReport?.burnoutLevel ?: "Low"
    if (burnout != "Low") {
        list.add(Suggestion("Digital Sunset", "You're showing fatigue. Enable Night Mode and put away screens 1 hour before bed.", Icons.Rounded.BatteryAlert, if (burnout == "High") Color(0xFFFF5252) else Color(0xFFFFB300)))
    }
    
    val anxiety = localReport?.anxietyLevel ?: "Low"
    if (anxiety != "Low") {
        list.add(Suggestion("Interaction Pause", "Frequent pauses detected. Take 3 deep breaths and focus on a single physical object.", Icons.Rounded.SelfImprovement, if (anxiety == "High") Color(0xFFFF5252) else Color(0xFFFFB300)))
    }
    
    val stress = localReport?.stressLevel ?: "Low"
    if (stress != "Low") {
        list.add(Suggestion("Physical Anchor", "High interaction arousal detected. A 5-minute walk will help lower your physiological stress.", Icons.Rounded.DirectionsRun, if (stress == "High") Color(0xFFFF5252) else Color(0xFF61D0C9)))
    }
    
    // Default positive if no major flags
    if (list.size < 2) {
        list.add(Suggestion("Flow State", "Your typing and scrolling rhythm indicate a healthy flow. Stay focused!", Icons.Rounded.AutoAwesome, Color(0xFF61D0C9)))
        list.add(Suggestion("Sleep Hygiene", "Reading a physical book tonight would be a great way to wind down.", Icons.Rounded.Nightlight, Color(0xFF6888F9)))
    }
    
    return list.take(4) // Top 4 most relevant suggestions
}

@Composable
fun SuggestionCard(suggestion: Suggestion) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = suggestion.color.copy(alpha = 0.1f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = suggestion.icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = suggestion.color
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    text = suggestion.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = suggestion.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
