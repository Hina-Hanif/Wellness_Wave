package com.example.myapplication.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.myapplication.data.local.StudySessionEntity
import com.example.myapplication.data.tracking.StudyRescueState
import com.example.myapplication.data.tracking.StudySessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

/**
 * Dedicated full-screen Focus Rescue interface.
 *
 * Provides a distraction sanctuary screen with:
 * - Clear title and explanatory information
 * - Circular countdown timer derived from absolute timestamps
 * - Task title, planned duration, and restricted app summary
 * - Guaranteed exit path, safety and emergency dialer access
 * - Clean completion state transition executed exactly once
 */
@Composable
fun FocusRescueScreen(
    sessionManager: StudySessionManager,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeSession by sessionManager.activeSessionFlow.collectAsState()
    val sessionState by sessionManager.stateFlow.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Absolute timestamp-driven clock. Survived across recomposition, rotation,
    // activity recreation, and app reopening.
    var currentEpochMs by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }

    // Lifecycle-aware ticker: updates currentEpochMs while in STARTED state.
    // Stops completely in background to prevent memory leaks and battery drain.
    LaunchedEffect(lifecycleOwner, activeSession) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                currentEpochMs = System.currentTimeMillis()
                delay(500L)
            }
        }
    }

    // Single-trigger completion flag
    var hasHandledCompletion by rememberSaveable { mutableStateOf(false) }

    FocusRescueContent(
        session = activeSession,
        sessionState = sessionState,
        currentEpochMs = currentEpochMs,
        onExitFocusRescue = {
            sessionManager.exitFocusRescue("USER_EXITED")
            onExit()
        },
        onReturnToStudySession = {
            sessionManager.completeFocusRescue()
            onExit()
        },
        onTimerCompleted = {
            if (!hasHandledCompletion) {
                hasHandledCompletion = true
                // Do not prematurely kill session; state machine and UI display completion
            }
        },
        modifier = modifier
    )
}

/**
 * Stateless presentation composable for Focus Rescue UI.
 * Facilitates straightforward testing, previewing, and lifecycle verification.
 */
@Composable
fun FocusRescueContent(
    session: StudySessionEntity?,
    sessionState: StudyRescueState,
    currentEpochMs: Long,
    onExitFocusRescue: () -> Unit,
    onReturnToStudySession: () -> Unit,
    onTimerCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showExitConfirmationDialog by rememberSaveable { mutableStateOf(false) }

    // If no active session exists, render fallback safely
    if (session == null) {
        NoSessionFallback(onReturn = onReturnToStudySession)
        return
    }

    // Absolute Timestamp Timer Calculation
    val startTimeMillis = session.focusRescueStartTime ?: session.startTimeMillis
    val plannedDurationMillis = session.plannedDurationMillis
    val endTimeMillis = session.focusRescueEndTime ?: (startTimeMillis + plannedDurationMillis)
    val remainingMillis = (endTimeMillis - currentEpochMs).coerceAtLeast(0L)
    val isTimerFinished = (remainingMillis <= 0L && plannedDurationMillis > 0L)

    val progress = if (plannedDurationMillis > 0L) {
        (remainingMillis.toFloat() / plannedDurationMillis.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    // Trigger completion hook exactly once when remaining reaches 0
    LaunchedEffect(isTimerFinished) {
        if (isTimerFinished) {
            onTimerCompleted()
        }
    }

    // Back button handling: Never traps user. Shows exit dialog or exits cleanly.
    BackHandler {
        if (isTimerFinished) {
            onReturnToStudySession()
        } else {
            showExitConfirmationDialog = true
        }
    }

    // Exit Confirmation Dialog
    if (showExitConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmationDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Exit Focus Rescue?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Distracting app restrictions will be lifted and you will return to your standard study session. You can re-enter whenever you need to refocus.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmationDialog = false
                        onExitFocusRescue()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Exit Focus Rescue", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmationDialog = false }) {
                    Text("Stay Focused")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Top Bar: Title & Safety Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = "Focus Rescue Shield",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Focus Rescue",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = if (isTimerFinished) "Session Completed" else "Distraction Shield Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isTimerFinished) Color(0xFF00E676) else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Quick exit button in top bar
                IconButton(
                    onClick = {
                        if (isTimerFinished) onReturnToStudySession() else showExitConfirmationDialog = true
                    },
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        .size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Exit Focus Rescue",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Explanation Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = "Information",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Distraction Restriction Active",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Selected distracting applications are temporarily restricted. Essential phone tools remain available and you can exit at any time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 3. Current Session Task Title & Planned Duration Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.TaskAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = session.taskTitle.ifBlank { "Deep Study Session" },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. Circular Countdown Timer
            CircularCountdownTimer(
                progress = progress,
                remainingTimeFormatted = formatRemainingTime(remainingMillis),
                totalDurationLabel = "PLANNED: ${plannedDurationMillis / 60000L} MIN",
                isCompleted = isTimerFinished,
                modifier = Modifier.semantics {
                    contentDescription = if (isTimerFinished) {
                        "Focus Rescue completed"
                    } else {
                        "Remaining time: ${formatRemainingTime(remainingMillis)}"
                    }
                }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 5. Completion State or Restricted Apps Summary
            if (isTimerFinished) {
                // Completion State Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF00E676).copy(alpha = 0.12f),
                    border = BorderStroke(1.5.dp, Color(0xFF00E676).copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Success",
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Focus Rescue Completed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E676)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Restrictions have ended. Great job reclaiming your focus!",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onReturnToStudySession,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = null,
                                tint = Color.Black
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Return to the study session",
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
            } else {
                // Restricted Application Summary Card
                RestrictedAppsSummaryCard(
                    restrictedPackages = session.selectedRestrictedAppPackages
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 6. Action Buttons & Emergency Access
            if (!isTimerFinished) {
                // Main Exit Button
                OutlinedButton(
                    onClick = { showExitConfirmationDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Exit Focus Rescue",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Safety / Emergency Escape Hatches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { openPhoneDialer(context) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Phone,
                            contentDescription = "Emergency & Phone",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Phone / Emergency",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    OutlinedButton(
                        onClick = { openSystemSettings(context) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "System Settings",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Custom canvas circular countdown timer with gradient arc and readable status readout.
 */
@Composable
fun CircularCountdownTimer(
    progress: Float,
    remainingTimeFormatted: String,
    totalDurationLabel: String,
    isCompleted: Boolean,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 16.dp,
    trackColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    gradientColors: List<Color> = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary
    )
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(240.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(strokeWidth / 2)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)

            // Background track
            drawCircle(
                color = trackColor,
                style = stroke
            )

            // Dynamic progress sweep arc
            if (!isCompleted && progress > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(gradientColors),
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = stroke
                )
            } else if (isCompleted) {
                drawCircle(
                    color = Color(0xFF00E676),
                    style = stroke
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "COMPLETED",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF00E676)
                )
            } else {
                Text(
                    text = remainingTimeFormatted,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = totalDurationLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

/**
 * Summary card listing user-selected restricted applications.
 */
@Composable
private fun RestrictedAppsSummaryCard(
    restrictedPackages: List<String>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Restricted Applications",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${restrictedPackages.size} Apps",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (restrictedPackages.isEmpty()) {
                Text(
                    text = "No distracting apps selected for this session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    restrictedPackages.take(4).forEach { pkg ->
                        val appLabel = remember(pkg) { resolveFriendlyAppName(context, pkg) }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.06f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                        ) {
                            Text(
                                text = appLabel,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                if (restrictedPackages.size > 4) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "+ ${restrictedPackages.size - 4} more restricted",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Fallback displayed if FocusRescueScreen is visited when no session exists.
 */
@Composable
private fun NoSessionFallback(onReturn: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Active Focus Rescue",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "There is currently no active Focus Rescue session in progress.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onReturn,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Return to Wellness Wave")
            }
        }
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────

/**
 * Formats milliseconds into a readable MM:SS or HH:MM:SS string.
 */
fun formatRemainingTime(remainingMillis: Long): String {
    val totalSeconds = (remainingMillis / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

/**
 * Resolves friendly human-readable app label from package name safely.
 */
fun resolveFriendlyAppName(context: Context, packageName: String): String {
    return try {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, 0)
        }
        pm.getApplicationLabel(info).toString()
    } catch (e: Exception) {
        when (packageName) {
            "com.instagram.android" -> "Instagram"
            "com.google.android.youtube" -> "YouTube"
            "com.zhiliaoapp.musically" -> "TikTok"
            "com.twitter.android" -> "X / Twitter"
            "com.reddit.frontpage" -> "Reddit"
            "com.facebook.katana" -> "Facebook"
            "com.snapchat.android" -> "Snapchat"
            else -> packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }
}

/**
 * Opens phone dialer without placing a call, ensuring emergency dialing is always reachable.
 */
private fun openPhoneDialer(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        android.util.Log.e("FocusRescueScreen", "Failed to open phone dialer", e)
    }
}

/**
 * Opens Android system settings, preventing user lock-out.
 */
private fun openSystemSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        android.util.Log.e("FocusRescueScreen", "Failed to open settings", e)
    }
}
