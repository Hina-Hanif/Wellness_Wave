package com.example.myapplication.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.tracking.StudyRescueState
import com.example.myapplication.data.tracking.StudySessionManager
import java.util.Locale

/**
 * Integrated Jetpack Compose section for Study Rescue session setup,
 * live timer tracking, and state controls.
 */
@Composable
fun StudyRescueSection(
    sessionManager: StudySessionManager,
    modifier: Modifier = Modifier
) {
    val state by sessionManager.stateFlow.collectAsState()
    val snapshot by sessionManager.snapshotFlow.collectAsState()
    val remainingSeconds by sessionManager.remainingSecondsFlow.collectAsState()
    val elapsedSeconds by sessionManager.elapsedSecondsFlow.collectAsState()

    val isOngoing = sessionManager.isSessionActive()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.School,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Study Rescue",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isOngoing) "Active Session in Progress" else "Structured Deep Focus Mode",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isOngoing) {
                    StatusPill(state = state)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (!isOngoing) {
                // Setup Mode
                StudySessionSetupContent(
                    onStartSession = { title, duration, apps ->
                        sessionManager.startSession(title, duration, apps)
                    }
                )
            } else {
                // Active Session Mode
                ActiveSessionContent(
                    taskTitle = snapshot.taskTitle,
                    plannedMinutes = snapshot.plannedDurationMinutes,
                    remainingSeconds = remainingSeconds,
                    elapsedSeconds = elapsedSeconds,
                    state = state,
                    distractionCount = snapshot.distractionCount,
                    ignoredInterventions = snapshot.ignoredInterventionsCount,
                    isFocusRescueEligible = snapshot.isFocusRescueEligible,
                    onPause = { sessionManager.pauseSession() },
                    onResume = { sessionManager.resumeSession() },
                    onComplete = { sessionManager.completeSession() },
                    onCancel = { sessionManager.cancelSession() }
                )
            }
        }
    }
}

// ── Setup Mode ──────────────────────────────────────────────────────────────

@Composable
private fun StudySessionSetupContent(
    onStartSession: (String, Int, Set<String>) -> Unit
) {
    var taskTitle by rememberSaveable { mutableStateOf("") }
    var selectedDuration by rememberSaveable { mutableIntStateOf(25) }

    // Multi-select distracting apps with sensible defaults
    val defaultApps = remember {
        listOf(
            "Instagram" to "com.instagram.android",
            "YouTube" to "com.google.android.youtube",
            "TikTok" to "com.zhiliaoapp.musically",
            "X / Twitter" to "com.twitter.android",
            "Reddit" to "com.reddit.frontpage"
        )
    }

    var selectedAppPackages by rememberSaveable {
        mutableStateOf(setOf("com.instagram.android", "com.google.android.youtube"))
    }

    Column {
        OutlinedTextField(
            value = taskTitle,
            onValueChange = { taskTitle = it },
            label = { Text("Study Goal / Task Title") },
            placeholder = { Text("e.g. Math Exam Prep, Reading Paper") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "PLANNED DURATION",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(15, 25, 45, 60).forEach { mins ->
                val isSelected = selectedDuration == mins
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedDuration = mins },
                    label = { Text("${mins}m") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.Black
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "RESTRICTED DISTRACTING APPS",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            defaultApps.forEach { (appName, pkg) ->
                val isSelected = selectedAppPackages.contains(pkg)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedAppPackages = if (isSelected) {
                            selectedAppPackages - pkg
                        } else {
                            selectedAppPackages + pkg
                        }
                    },
                    label = { Text(appName) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        var isStarting by remember { mutableStateOf(false) }

        Button(
            onClick = {
                if (!isStarting) {
                    isStarting = true
                    onStartSession(taskTitle, selectedDuration, selectedAppPackages)
                }
            },
            enabled = !isStarting,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Start Study Rescue",
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                fontSize = 15.sp
            )
        }
    }
}

// ── Active Session Mode ─────────────────────────────────────────────────────

@Composable
private fun ActiveSessionContent(
    taskTitle: String,
    plannedMinutes: Int,
    remainingSeconds: Long,
    elapsedSeconds: Long,
    state: StudyRescueState,
    distractionCount: Int,
    ignoredInterventions: Int,
    isFocusRescueEligible: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    val totalSeconds = (plannedMinutes * 60).coerceAtLeast(1)
    val progress = (elapsedSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)

    val remainingFormatted = String.format(Locale.getDefault(), "%02d:%02d", remainingSeconds / 60, remainingSeconds % 60)
    val elapsedFormatted = String.format(Locale.getDefault(), "%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)

    Column {
        Text(
            text = taskTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Large Timer Display
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = remainingFormatted,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "REMAINING TIME",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = elapsedFormatted,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "ELAPSED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Progress Indicator
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Telemetry & Strike Metrics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatPill(
                label = "Target",
                value = "${plannedMinutes}m",
                modifier = Modifier.weight(1f)
            )
            StatPill(
                label = "Distractions",
                value = "$distractionCount",
                modifier = Modifier.weight(1f)
            )
            StatPill(
                label = "Strikes",
                value = "$ignoredInterventions / 2",
                modifier = Modifier.weight(1f),
                isHighlighted = ignoredInterventions >= 2
            )
        }

        AnimatedVisibility(visible = isFocusRescueEligible) {
            Column {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = Color(0xFFFF5252).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Focus Rescue Ready — 2 strikes recorded",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF5252)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Control Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state == StudyRescueState.SESSION_PAUSED) {
                Button(
                    onClick = onResume,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Resume", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = onPause,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Pause, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pause", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = onComplete,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Complete", color = Color.White, fontWeight = FontWeight.Bold)
            }

            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    .size(44.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Cancel Session", tint = Color.White)
            }
        }
    }
}

// ── Shared UI Helpers ───────────────────────────────────────────────────────

@Composable
private fun StatusPill(state: StudyRescueState) {
    val (label, bgColor, textColor) = when (state) {
        StudyRescueState.SESSION_ACTIVE -> Triple("Active", Color(0xFF00E676).copy(alpha = 0.15f), Color(0xFF00E676))
        StudyRescueState.SESSION_PAUSED -> Triple("Paused", Color(0xFFFFB74D).copy(alpha = 0.15f), Color(0xFFFFB74D))
        StudyRescueState.FOCUS_RESCUE_ACTIVE -> Triple("Focus Rescue", Color(0xFFFF5252).copy(alpha = 0.15f), Color(0xFFFF5252))
        StudyRescueState.FOCUS_RESCUE_READY -> Triple("Escalation Ready", Color(0xFFFF9100).copy(alpha = 0.15f), Color(0xFFFF9100))
        StudyRescueState.INTERVENTION_ONE_PENDING,
        StudyRescueState.INTERVENTION_TWO_PENDING -> Triple("Intervention", Color(0xFFFFD54F).copy(alpha = 0.15f), Color(0xFFFFD54F))
        else -> Triple("Idle", Color.White.copy(alpha = 0.1f), Color.White)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (isHighlighted) Color(0xFFFF5252).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.06f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isHighlighted) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
