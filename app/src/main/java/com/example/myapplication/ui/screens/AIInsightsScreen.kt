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
import kotlinx.coroutines.launch

@Composable
fun AIInsightsScreen(innerPadding: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var prediction by remember { mutableStateOf<PredictionResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }

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
            StressMeterCard(prediction = prediction, isLoading = isLoading)

            Spacer(modifier = Modifier.height(32.dp))

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
        val suggestions = generateSuggestions(prediction)
        
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
fun StressMeterCard(prediction: PredictionResponse?, isLoading: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
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
                        text = "Current State",
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
                        text = if (isLoading) "Analyzing..." else (prediction?.stress_level ?: "Balanced"),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Map Stress Level string to a float between 0 and 1
            val targetProgress = when (prediction?.stress_level) {
                "Low" -> 0.2f
                "Medium" -> 0.5f
                "High" -> 0.9f
                else -> 0.2f
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
                text = "Model Feedback",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = prediction?.real_time_feedback ?: "Keep up the good habits. Your digital metrics look stable.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

data class Suggestion(val title: String, val description: String, val icon: ImageVector, val color: Color)

fun generateSuggestions(prediction: PredictionResponse?): List<Suggestion> {
    if (prediction == null) {
        return listOf(
            Suggestion("2-Minute Breathing", "A quick exercise to center your focus.", Icons.Rounded.Spa, Color(0xFF61D0C9)),
            Suggestion("Screen Break", "Step away for 5 minutes.", Icons.Rounded.DirectionsRun, Color(0xFF6888F9))
        )
    }
    
    val list = mutableListOf<Suggestion>()
    
    if (prediction.addiction_detected) {
        list.add(Suggestion("Device Detox", "You've unlocked your phone frequently. Try placing it in another room for 30 minutes.", Icons.Rounded.Warning, Color(0xFFFF5252)))
    }
    
    if (prediction.burnout_detected) {
        list.add(Suggestion("Digital Sunset", "You're showing signs of burnout and late-night usage. Enable Wind Down at 9 PM.", Icons.Rounded.BatteryAlert, Color(0xFFFFB300)))
    }
    
    if (prediction.anxiety_detected) {
        list.add(Suggestion("Mindful Scrolling", "Your interactions are erratic. Pause and take 3 deep breaths before reopening social apps.", Icons.Rounded.SelfImprovement, Color(0xFF6888F9)))
    }
    
    if (prediction.stress_level == "High" && !prediction.burnout_detected) {
        list.add(Suggestion("Short Walk", "Step outside to lower your cortisol levels.", Icons.Rounded.DirectionsRun, Color(0xFF61D0C9)))
    }
    
    // Default positive if no major flags
    if (list.isEmpty()) {
        list.add(Suggestion("Keep it up!", "Your usage is highly balanced. Consider reading a book instead of scrolling tonight.", Icons.Rounded.AutoAwesome, Color(0xFF61D0C9)))
        list.add(Suggestion("Digital Wind Down", "A gentle reminder to put away devices 30 mins before sleep.", Icons.Rounded.Nightlight, Color(0xFF6888F9)))
    }
    
    return list
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
