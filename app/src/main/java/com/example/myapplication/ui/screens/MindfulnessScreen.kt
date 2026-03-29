package com.example.myapplication.ui.screens

import android.content.Context

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.data.UsageDataManager
import com.example.myapplication.data.analysis.BehavioralInferenceEngine
import com.example.myapplication.data.analysis.MentalStateReport
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MindfulnessScreen(innerPadding: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var localReport by remember { mutableStateOf<MentalStateReport?>(null) }
    
    // Background Analysis Loop
    LaunchedEffect(Unit) {
        val manager = UsageDataManager(context)
        
        while (true) {
            val metrics = manager.getDailyMetrics()
            localReport = BehavioralInferenceEngine.analyze(metrics)
            delay(30000) // Update every 30s
        }
    }

    // Hourly Reminder fetching
    val sharedPrefs = remember { context.getSharedPreferences("wellness_wave_prefs", Context.MODE_PRIVATE) }
    val hourlyReminder = remember { 
        mutableStateOf(sharedPrefs.getString("current_hourly_reminder", "Take a moment to breathe, you don’t need your phone right now.") ?: "") 
    }

    // Refresh reminder when screen is visited
    LaunchedEffect(Unit) {
        hourlyReminder.value = sharedPrefs.getString("current_hourly_reminder", hourlyReminder.value) ?: ""
    }

    val dynamicStatus = when(localReport?.overallLevel) {
        "Low" -> "You’re maintaining a healthy balance"
        "Medium" -> "Great progress, keep it up"
        "High" -> "Your mental health is improving"
        else -> "Wellness check-in active"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(innerPadding)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header (Centered)
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Wellness Wave",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "DAILY MINDFULNESS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Your journey to balance continues.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Gentle Reminder Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BoxDefaults.cardBorder()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🌿", fontSize = 24.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Gentle Reminder",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = dynamicStatus,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = hourlyReminder.value,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 24.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🕒", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Next check-in in 1 hour",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Stats row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MindfulnessStat(label = "Total Breaks", value = "3/5", subValue = "Good pace", progress = 0.6f, modifier = Modifier.weight(1f))
                MindfulnessStat(label = "Focus Time", value = "4.2h", subValue = "+10%", progress = 0.8f, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(24.dp))


        }
    }
}

@Composable
fun MindfulnessStat(label: String, value: String, subValue: String, progress: Float, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BoxDefaults.cardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = subValue, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            )
        }
    }
}
