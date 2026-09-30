package com.example.myapplication.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.tracking.StudyRescueState
import com.example.myapplication.data.tracking.StudySessionManager
import com.example.myapplication.data.revenuecat.RevenueCatManager
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

/**
 * Integrated Jetpack Compose section for Study Rescue session setup,
 * live timer tracking, and state controls.
 */
@Composable
fun StudyRescueSection(
    sessionManager: StudySessionManager,
    onNavigateToFocusRescue: () -> Unit = {},
    onNavigateToPaywall: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by sessionManager.stateFlow.collectAsState()
    val snapshot by sessionManager.snapshotFlow.collectAsState()
    val remainingSeconds by sessionManager.remainingSecondsFlow.collectAsState()
    val elapsedSeconds by sessionManager.elapsedSecondsFlow.collectAsState()

    val isOngoing = sessionManager.isSessionActive()

    // RevenueCat Entitlement Check for "wellness_pro"
    val isProActive by RevenueCatManager.isProActiveFlow.collectAsState()

    fun openPaywall() {
        onNavigateToPaywall()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                } else if (isProActive) {
                    Surface(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable { openPaywall() },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WorkspacePremium,
                                contentDescription = "View Pro",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "PRO",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (!isProActive) {
                // Free User: Show Premium Locked Card
                LockedStudyRescueCard(
                    onUnlockClicked = { openPaywall() }
                )
            } else if (!isOngoing) {
                // Pro User - Setup Mode: Direct access to the complete Study Rescue setup and analytics
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
                    onCancel = { sessionManager.cancelSession() },
                    onStartFocusRescue = {
                        sessionManager.startFocusRescue()
                        onNavigateToFocusRescue()
                    },
                    onOpenFocusRescue = onNavigateToFocusRescue
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

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    ),
                    shape = RoundedCornerShape(16.dp)
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

        Button(
            onClick = {
                onStartSession(taskTitle, selectedDuration, selectedAppPackages)
            },
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

        Spacer(modifier = Modifier.height(16.dp))

        var showAnalytics by remember { mutableStateOf(false) }
        val context = androidx.compose.ui.platform.LocalContext.current
        val db = remember { com.example.myapplication.data.local.BehaviorDatabase.getDatabase(context) }
        val repository = remember { com.example.myapplication.data.local.StudyRescueRepository(db.studyRescueDao()) }
        var range by remember { mutableStateOf(com.example.myapplication.data.analysis.AnalyticsTimeRange.PAST_7_DAYS) }
        val summary by remember(range) {
            repository.getAnalyticsFlow(kotlinx.coroutines.flow.flowOf(range))
        }.collectAsState(initial = com.example.myapplication.data.analysis.StudyRescueAnalyticsSummary())

        OutlinedButton(
            onClick = { showAnalytics = !showAnalytics },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Icon(
                imageVector = if (showAnalytics) Icons.Rounded.ExpandLess else Icons.Rounded.Analytics,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (showAnalytics) "Hide Focus Analytics" else "View Focus Rescue Analytics",
                style = MaterialTheme.typography.labelLarge
            )
        }

        AnimatedVisibility(visible = showAnalytics) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                StudyRescueAnalyticsDashboard(
                    summary = summary,
                    selectedRange = range,
                    onSelectRange = { range = it }
                )
            }
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
    onCancel: () -> Unit,
    onStartFocusRescue: () -> Unit = {},
    onOpenFocusRescue: () -> Unit = {}
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

        AnimatedVisibility(visible = isFocusRescueEligible && state != StudyRescueState.FOCUS_RESCUE_ACTIVE) {
            Column {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = Color(0xFFFF5252).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFF5252).copy(alpha = 0.2f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shield,
                                        contentDescription = null,
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Focus Rescue Ready",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF5252),
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = "2 strikes recorded • Escalation eligible",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Button(
                            onClick = onStartFocusRescue,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(
                                text = "Activate",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = state == StudyRescueState.FOCUS_RESCUE_ACTIVE) {
            Column {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shield,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Focus Rescue Active",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = "Distraction Shield Enabled",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Button(
                            onClick = onOpenFocusRescue,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(
                                text = "View",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Control Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state == StudyRescueState.SESSION_PAUSED) {
                Button(
                    onClick = onResume,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Resume",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onPause,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(
                        Icons.Default.Pause,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pause",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }

            Button(
                onClick = onComplete,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Complete",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        TextButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Cancel Session",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
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
            color = textColor,
            maxLines = 1,
            softWrap = false
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isHighlighted) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Locked preview card displayed to Free Users when Study Rescue is locked.
 * Features responsive pills and single-line button text that will not wrap awkwardly.
 */
@Composable
fun LockedStudyRescueCard(
    onUnlockClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Locked Feature",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Study Rescue is a Pro Feature",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Eliminate phone distractions during deep study blocks with intelligent app redirection and focus analytics.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 6.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Responsive flow of feature pills (prevents awkward character wrapping)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProFeatureBadge(icon = Icons.Rounded.Shield, label = "App Block")
                ProFeatureBadge(icon = Icons.Rounded.Timeline, label = "Analytics")
                ProFeatureBadge(icon = Icons.Rounded.Bolt, label = "AI Rescue")
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onUnlockClicked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.Black
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WorkspacePremium,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Unlock with Wellness Pro",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.Black,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun ProFeatureBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
