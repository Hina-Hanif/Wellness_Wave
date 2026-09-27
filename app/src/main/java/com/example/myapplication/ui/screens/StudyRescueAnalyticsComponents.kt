package com.example.myapplication.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.analysis.AnalyticsTimeRange
import com.example.myapplication.data.analysis.DailyInterventionTrend
import com.example.myapplication.data.analysis.DailySessionTrend
import com.example.myapplication.data.analysis.StudyRescueAnalyticsSummary

/**
 * Reusable Study Rescue Analytics Dashboard displaying aggregated metrics,
 * completion trends, intervention trends, and privacy-preserving category insights.
 */
@Composable
fun StudyRescueAnalyticsDashboard(
    summary: StudyRescueAnalyticsSummary,
    selectedRange: AnalyticsTimeRange,
    onSelectRange: (AnalyticsTimeRange) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Time Range Filter Chips (Responsive scrollable row)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnalyticsTimeRange.values().forEach { range ->
                val isSelected = selectedRange == range
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectRange(range) },
                    label = {
                        Text(
                            text = range.label,
                            maxLines = 1,
                            softWrap = false,
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.Black
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (summary.isEmpty) {
            EmptyAnalyticsCard()
        } else {
            // KPI Summary Cards
            KpiOverviewGrid(summary = summary)

            Spacer(modifier = Modifier.height(24.dp))

            // Study Session Completion Trend
            SessionCompletionTrendCard(trends = summary.sessionCompletionTrend)

            Spacer(modifier = Modifier.height(24.dp))

            // Intervention Trend Over Time
            InterventionTrendCard(trends = summary.interventionTrend)

            Spacer(modifier = Modifier.height(24.dp))

            // Comprehensive Metrics Breakdown Card
            DetailedMetricsBreakdownCard(summary = summary)
        }
    }
}

// ── KPI Overview Grid ─────────────────────────────────────────────────────────

@Composable
private fun KpiOverviewGrid(summary: StudyRescueAnalyticsSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnalyticsMetricCard(
                title = "Total Sessions",
                value = "${summary.totalSessions}",
                subtitle = "${summary.completedSessions} completed • ${summary.cancelledSessions} cancelled",
                icon = Icons.Rounded.School,
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )

            AnalyticsMetricCard(
                title = "Completion Rate",
                value = "${summary.completionRatePercentage}%",
                subtitle = if (summary.completionRatePercentage >= 75) "Optimal focus" else "Room to grow",
                icon = Icons.Rounded.CheckCircle,
                accentColor = if (summary.completionRatePercentage >= 75) Color(0xFF00C853) else Color(0xFFFBC02D),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnalyticsMetricCard(
                title = "Distractions",
                value = "${summary.totalDistractionEvents}",
                subtitle = "${summary.interventionOneCount} gentle • ${summary.interventionTwoCount} escalated",
                icon = Icons.Rounded.Warning,
                accentColor = Color(0xFFFF9100),
                modifier = Modifier.weight(1f)
            )

            val avgDurationFormatted = if (summary.averageFocusRescueDurationSeconds > 0) {
                val mins = summary.averageFocusRescueDurationSeconds / 60
                val secs = summary.averageFocusRescueDurationSeconds % 60
                if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
            } else {
                "--"
            }

            AnalyticsMetricCard(
                title = "Focus Rescue",
                value = "${summary.focusRescueActivationCount}",
                subtitle = "Avg: $avgDurationFormatted • ${summary.focusRescueCompletionCount} kept",
                icon = Icons.Rounded.Shield,
                accentColor = Color(0xFF80D8FF),
                modifier = Modifier.weight(1f)
            )
        }

        // Category & Ignored highlight
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnalyticsMetricCard(
                title = "Top Distraction Type",
                value = summary.mostFrequentlyRestrictedCategory ?: "None",
                subtitle = "Protected category",
                icon = Icons.Rounded.Category,
                accentColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )

            AnalyticsMetricCard(
                title = "Ignored Alerts",
                value = "${summary.ignoredInterventionCount}",
                subtitle = "${summary.focusRescueExitCount} manual exits",
                icon = Icons.Rounded.NotificationsOff,
                accentColor = if (summary.ignoredInterventionCount > 0) Color(0xFFFF5252) else Color(0xFF00C853),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun AnalyticsMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(4.dp))
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp
            )
        }
    }
}

// ── Session Completion Trend Chart ──────────────────────────────────────────

@Composable
private fun SessionCompletionTrendCard(trends: List<DailySessionTrend>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Session Completion Trend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Daily completed vs cancelled study sessions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (trends.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No session trends in this time frame",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                SessionTrendChartCanvas(trends = trends)

                Spacer(modifier = Modifier.height(12.dp))

                // Chart Legend (Responsive flow row)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LegendItem(color = Color(0xFF00C853), label = "Completed")
                    Spacer(modifier = Modifier.width(8.dp))
                    LegendItem(color = Color(0xFFFF5252), label = "Cancelled")
                    Spacer(modifier = Modifier.width(8.dp))
                    LegendItem(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), label = "Incomplete/Active")
                }
            }
        }
    }
}

@Composable
private fun SessionTrendChartCanvas(trends: List<DailySessionTrend>) {
    val maxSessions = trends.maxOfOrNull { it.totalSessions }?.coerceAtLeast(1) ?: 1

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clipToBounds()
    ) {
        val count = trends.size
        val canvasWidth = size.width
        val canvasHeight = size.height

        val rawBarWidth = (canvasWidth / count) * 0.55f
        val barWidth = rawBarWidth.coerceIn(12f, 48f)
        val spacing = canvasWidth / count

        trends.forEachIndexed { i, trend ->
            val totalFraction = (trend.totalSessions.toFloat() / maxSessions).coerceIn(0.08f, 1f)
            val totalBarHeight = canvasHeight * totalFraction
            val completedHeight = if (trend.totalSessions > 0) {
                totalBarHeight * (trend.completedSessions.toFloat() / trend.totalSessions)
            } else 0f
            val cancelledHeight = if (trend.totalSessions > 0) {
                totalBarHeight * (trend.cancelledSessions.toFloat() / trend.totalSessions)
            } else 0f
            val otherHeight = (totalBarHeight - completedHeight - cancelledHeight).coerceAtLeast(0f)

            val x = (i * spacing) + (spacing - barWidth) / 2f
            var currentY = canvasHeight

            // Draw completed portion (green)
            if (completedHeight > 0f) {
                currentY -= completedHeight
                drawRoundRect(
                    color = Color(0xFF00C853),
                    topLeft = Offset(x, currentY),
                    size = Size(barWidth, completedHeight),
                    cornerRadius = CornerRadius(6f, 6f)
                )
            }

            // Draw cancelled portion (red)
            if (cancelledHeight > 0f) {
                currentY -= cancelledHeight
                drawRoundRect(
                    color = Color(0xFFFF5252),
                    topLeft = Offset(x, currentY),
                    size = Size(barWidth, cancelledHeight),
                    cornerRadius = CornerRadius(6f, 6f)
                )
            }

            // Draw remaining active/incomplete portion (neutral)
            if (otherHeight > 0f) {
                currentY -= otherHeight
                drawRoundRect(
                    color = Color.Gray.copy(alpha = 0.5f),
                    topLeft = Offset(x, currentY),
                    size = Size(barWidth, otherHeight),
                    cornerRadius = CornerRadius(6f, 6f)
                )
            }
        }
    }
}

// ── Intervention Trend Card ─────────────────────────────────────────────────

@Composable
private fun InterventionTrendCard(trends: List<DailyInterventionTrend>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Intervention Trend Over Time",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Stage 1 (Gentle) vs Stage 2 (Escalated) vs Ignored",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (trends.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No interventions recorded in this time frame",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                InterventionTrendChartCanvas(trends = trends)

                Spacer(modifier = Modifier.height(12.dp))

                // Chart Legend (Responsive flow row)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LegendItem(color = Color(0xFF00E5FF), label = "Stage 1")
                    Spacer(modifier = Modifier.width(8.dp))
                    LegendItem(color = Color(0xFFFF9100), label = "Stage 2")
                    Spacer(modifier = Modifier.width(8.dp))
                    LegendItem(color = Color(0xFFFF5252), label = "Ignored")
                }
            }
        }
    }
}

@Composable
private fun InterventionTrendChartCanvas(trends: List<DailyInterventionTrend>) {
    val maxInterventions = trends.maxOfOrNull { maxOf(it.totalCount, it.ignoredCount) }?.coerceAtLeast(1) ?: 1

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clipToBounds()
    ) {
        val count = trends.size
        val canvasWidth = size.width
        val canvasHeight = size.height

        val daySlot = canvasWidth / count
        val barWidth = (daySlot * 0.36f).coerceIn(8f, 32f)
        val innerGap = (barWidth * 0.2f).coerceIn(2f, 8f)
        val groupWidth = (barWidth * 2) + innerGap

        trends.forEachIndexed { i, trend ->
            val startX = (i * daySlot) + (daySlot - groupWidth) / 2f
            val x1 = startX
            val x2 = startX + barWidth + innerGap

            // ── Bar 1: Interventions (Stage 1 + Stage 2 Stacked) ──────
            if (trend.totalCount > 0) {
                val totalFraction = (trend.totalCount.toFloat() / maxInterventions).coerceIn(0.08f, 1f)
                val bar1TotalHeight = canvasHeight * totalFraction

                val s1Height = bar1TotalHeight * (trend.stageOneCount.toFloat() / trend.totalCount)
                val s2Height = bar1TotalHeight * (trend.stageTwoCount.toFloat() / trend.totalCount)

                var currentY = canvasHeight

                // Draw Stage 1 (Cyan)
                if (s1Height > 0f) {
                    currentY -= s1Height
                    drawRoundRect(
                        color = Color(0xFF00E5FF),
                        topLeft = Offset(x1, currentY),
                        size = Size(barWidth, s1Height),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }

                // Draw Stage 2 (Orange)
                if (s2Height > 0f) {
                    currentY -= s2Height
                    drawRoundRect(
                        color = Color(0xFFFF9100),
                        topLeft = Offset(x1, currentY),
                        size = Size(barWidth, s2Height),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }
            }

            // ── Bar 2: Ignored Alerts (Red) ───────────────────────────
            if (trend.ignoredCount > 0) {
                val ignFraction = (trend.ignoredCount.toFloat() / maxInterventions).coerceIn(0.08f, 1f)
                val bar2Height = canvasHeight * ignFraction
                val ignY = canvasHeight - bar2Height

                drawRoundRect(
                    color = Color(0xFFFF5252),
                    topLeft = Offset(x2, ignY),
                    size = Size(barWidth, bar2Height),
                    cornerRadius = CornerRadius(4f, 4f)
                )
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

// ── Detailed Metrics Breakdown Card ─────────────────────────────────────────

@Composable
private fun DetailedMetricsBreakdownCard(summary: StudyRescueAnalyticsSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Detailed Metric Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Comprehensive aggregated Study Rescue telemetry",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            MetricRow("Total study sessions", "${summary.totalSessions}")
            MetricRow("Completed study sessions", "${summary.completedSessions}", Color(0xFF00C853))
            MetricRow("Cancelled study sessions", "${summary.cancelledSessions}", Color(0xFFFF5252))
            MetricRow("Total distraction events during study", "${summary.totalDistractionEvents}")
            MetricRow("Intervention 1 (Gentle prompt) count", "${summary.interventionOneCount}")
            MetricRow("Intervention 2 (Escalated prompt) count", "${summary.interventionTwoCount}")
            MetricRow("Ignored intervention count", "${summary.ignoredInterventionCount}")
            MetricRow("Focus Rescue activation count", "${summary.focusRescueActivationCount}")
            MetricRow("Focus Rescue completion count", "${summary.focusRescueCompletionCount}")
            MetricRow("Focus Rescue exit count", "${summary.focusRescueExitCount}")

            val avgDuration = if (summary.averageFocusRescueDurationSeconds > 0) {
                "${summary.averageFocusRescueDurationSeconds}s"
            } else {
                "--"
            }
            MetricRow("Average Focus Rescue duration", avgDuration)
            MetricRow("Top restricted app category", summary.mostFrequentlyRestrictedCategory ?: "None specified")

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Privacy Protected: All metrics are computed locally. No screen contents, notification texts, or private messages are ever stored or sent.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

// ── Empty State Card ────────────────────────────────────────────────────────

@Composable
fun EmptyAnalyticsCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No Study Rescue Records",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Completed study sessions and intervention events will automatically populate your focus analytics and trends. Start your first session in the Mindfulness tab!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}
