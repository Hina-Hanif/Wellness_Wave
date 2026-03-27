package com.example.myapplication.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.models.PredictionHistoryResponse
import kotlinx.coroutines.launch
import android.content.Context
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(innerPadding: PaddingValues) {
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
                // Ignore for now, fallback to defaults if null
            } finally {
                isLoading = false
            }
        }
    }

    // Extract dynamic data or use defaults if loading/failed
    val stressPoints = historyData?.history?.map { it.stress_score } ?: listOf(0.4f, 0.6f, 0.4f, 0.5f, 0.6f, 0.3f, 0.4f, 0.8f)
    
    // Normalize screen time (assume 10 hours is 1.0f max for the chart)
    val screenTimeBars = historyData?.history?.map { (it.screen_time_hours / 10f).coerceIn(0f, 1f) } ?: listOf(0.4f, 0.3f, 0.6f, 0.2f, 0.5f, 0.7f, 0.5f)
    
    // Averages
    val avgScreenTime = historyData?.history?.map { it.screen_time_hours }?.average() ?: 5.3
    val avgScreenTimeString = String.format("%.1fh", avgScreenTime)

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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = "Weekly Overview",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Stress Trend Card
            TrendCard(
                title = "STRESS TREND",
                value = if (isLoading) "..." else "Active",
                changeBadge = null,
                isIncrease = false
            ) {
                StressLineChart(points = stressPoints)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Screen Time Card
            TrendCard(
                title = "SCREEN TIME AVG",
                value = if (isLoading) "..." else avgScreenTimeString,
                changeBadge = null,
                isIncrease = false
            ) {
                ScreenTimeBarChart(bars = screenTimeBars)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Unlock Frequency Card
            TrendCard(
                title = "UNLOCK FREQUENCY",
                value = "42 /day",
                statusText = "+28% increase",
                statusColor = Color.Red
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Usage Insights Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = BoxDefaults.cardBorder()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("💡", fontSize = 16.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Usage Insights",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "You unlocked your phone 28% more this week compared to last. High screen time is correlating with your peak stress hours.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Suggestion: Try scheduling short offline moments to recharge and reduce cognitive load.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(16.dp),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    var showTimePicker by remember { mutableStateOf(false) }
                    var selectedTime by remember { mutableStateOf("09:00 AM") }
                    val timePickerState = rememberTimePickerState(initialHour = 9, initialMinute = 0)

                    Text(
                        text = "Current Reminder: $selectedTime",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Set Reminders", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    if (showTimePicker) {
                        TimePickerDialog(
                            onDismissRequest = { showTimePicker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    selectedTime = String.format("%02d:%02d", timePickerState.hour, timePickerState.minute)
                                    showTimePicker = false
                                }) { Text("OK") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                            }
                        ) {
                            TimePicker(state = timePickerState)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        text = { content() },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun TrendCard(
    title: String,
    value: String,
    changeBadge: String? = null,
    isIncrease: Boolean = false,
    statusText: String? = null,
    statusColor: Color = Color.Gray,
    content: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                changeBadge?.let {
                    Surface(
                        color = if (isIncrease) Color(0xFFFF5252).copy(alpha = 0.1f) else Color(0xFF00E37C).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = it,
                            color = if (isIncrease) Color(0xFFFF5252) else Color(0xFF00E37C),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            statusText?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).background(statusColor, CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = it, style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }

            content?.let {
                Spacer(modifier = Modifier.height(24.dp))
                it()
            }
        }
    }
}

@Composable
fun StressLineChart(points: List<Float> = listOf(0.4f, 0.6f, 0.4f, 0.5f, 0.6f, 0.3f, 0.4f, 0.8f)) {
    Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
        val width = size.width
        val height = size.height
        val spacing = width / (points.size - 1)

        val path = Path().apply {
            moveTo(0f, height * (1 - points[0]))
            for (i in 1 until points.size) {
                val x = i * spacing
                val y = height * (1 - points[i])
                // Simple Bézier for smooth lines
                val prevX = (i - 1) * spacing
                val prevY = height * (1 - points[i - 1])
                cubicTo(
                    prevX + spacing / 2f, prevY,
                    x - spacing / 2f, y,
                    x, y
                )
            }
        }

        drawPath(
            path = path,
            color = Color(0xFF00E37C),
            style = Stroke(width = 3.dp.toPx())
        )

        // Draw fill gradient
        val fillPath = Path().apply {
            addPath(path)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF00E37C).copy(alpha = 0.2f), Color.Transparent)
            )
        )
    }
    
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
fun ScreenTimeBarChart(bars: List<Float> = listOf(0.4f, 0.3f, 0.6f, 0.2f, 0.5f, 0.7f, 0.5f)) {
    Row(
        modifier = Modifier.fillMaxWidth().height(100.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        bars.forEach { heightFactor ->
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .fillMaxHeight(heightFactor)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF00E37C), Color(0xFF00E37C).copy(alpha = 0.3f))
                        ),
                        RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                    )
            )
        }
    }
}

object BoxDefaults {
    @Composable
    fun cardBorder() = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
}
