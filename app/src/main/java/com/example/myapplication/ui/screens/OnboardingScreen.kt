package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.utils.PermissionUtils

@Composable
fun OnboardingScreen(innerPadding: PaddingValues, onComplete: () -> Unit) {
    val context = LocalContext.current
    var usageGranted by remember { mutableStateOf(PermissionUtils.hasUsageStatsPermission(context)) }
    var accessibilityGranted by remember { mutableStateOf(PermissionUtils.hasAccessibilityPermission(context)) }
    
    // Refresh permissions when screen is resumed (approximate for demo)
    LaunchedEffect(Unit) {
        while(true) {
            usageGranted = PermissionUtils.hasUsageStatsPermission(context)
            accessibilityGranted = PermissionUtils.hasAccessibilityPermission(context)
            kotlinx.coroutines.delay(2000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF08100D), Color(0xFF0D1F17))
                )
            )
            .padding(innerPadding)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Illustration Area
            Surface(
                modifier = Modifier.size(160.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🌊", fontSize = 80.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                text = "Wellness Wave",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Your journey to digital balance starts with high-granularity behavioral insights.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp
            )
            
            Spacer(modifier = Modifier.height(48.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PermissionItem(
                    title = "Usage Access", 
                    description = "To monitor app usage patterns.",
                    granted = usageGranted,
                    onClick = { PermissionUtils.launchUsageStatsSettings(context) }
                )
                PermissionItem(
                    title = "Accessibility", 
                    description = "To analyze typing & scrolling.",
                    granted = accessibilityGranted,
                    onClick = { PermissionUtils.launchAccessibilitySettings(context) }
                )
                PermissionItem(
                    title = "Notifications", 
                    description = "To measure reaction latency.",
                    granted = true, // Simplified for now
                    onClick = { }
                )
            }
            
            Spacer(modifier = Modifier.height(64.dp))
            
            Button(
                onClick = { 
                    if (usageGranted && accessibilityGranted) {
                        onComplete() 
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (usageGranted && accessibilityGranted) MaterialTheme.colorScheme.primary else Color.Gray
                )
            ) {
                Text(
                    "Get Started",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            TextButton(onClick = onComplete) {
                Text(
                    "Explore in Demo Mode",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PermissionItem(title: String, description: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (granted) Color.Transparent else Color.White.copy(alpha = 0.05f))
            .clickable { if (!granted) onClick() }
            .padding(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(24.dp),
            shape = CircleShape,
            color = if (granted) MaterialTheme.colorScheme.primary else Color.Gray
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (granted) Icons.Default.Check else Icons.Default.Add, 
                    contentDescription = null, 
                    modifier = Modifier.size(16.dp), 
                    tint = Color.Black
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
