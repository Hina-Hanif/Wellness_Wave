package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.data.UsageDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MoodScreen(innerPadding: PaddingValues) {
    var stressLevel by remember { mutableStateOf(5f) }
    var isSubmitting by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val usageDataManager = remember { UsageDataManager(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "How stressed are you tonight?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = stressLevel.roundToInt().toString(),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        Slider(
            value = stressLevel,
            onValueChange = { stressLevel = it },
            valueRange = 1f..10f,
            steps = 8,
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Calm", style = MaterialTheme.typography.labelMedium)
            Text("Stressed", style = MaterialTheme.typography.labelMedium)
        }

        Spacer(modifier = Modifier.height(64.dp))

        Button(
            onClick = {
                isSubmitting = true
                scope.launch {
                    try {
                        val metrics = usageDataManager.getDailyMetrics(stressLevel.roundToInt())
                        val response = withContext(Dispatchers.IO) {
                            RetrofitClient.instance.submitDailyData(metrics)
                        }
                        
                        if (response.isSuccessful) {
                            Toast.makeText(context, "✅ Data synced successfully to Firebase!", Toast.LENGTH_LONG).show()
                        } else {
                            val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                            Toast.makeText(context, "❌ Sync failed (${response.code()}): $errorMsg", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: java.net.ConnectException) {
                        Toast.makeText(context, "🔌 Cannot connect to backend. Is it running on 10.0.2.2:8000?", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "⚠️ Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    } finally {
                        isSubmitting = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isSubmitting,
            shape = RoundedCornerShape(16.dp)
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Submit & Sync", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
