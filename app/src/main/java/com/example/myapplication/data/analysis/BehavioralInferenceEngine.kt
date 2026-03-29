package com.example.myapplication.data.analysis

import android.util.Log
import com.example.myapplication.models.UsageMetrics

data class MentalStateReport(
    val stressLevel: String,    // Low, Medium, High
    val stressInsight: String,  // Description of pattern (no numbers)
    val anxietyLevel: String,   // Low, Medium, High
    val anxietyInsight: String, // Description of pattern (no numbers)
    val burnoutLevel: String,   // Low, Medium, High
    val burnoutInsight: String, // Description of pattern (no numbers)
    val addictionLevel: String, // Low, Medium, High
    val addictionInsight: String, // Description of pattern (no numbers)
    val primaryState: String,   // The most critical state currently active
    val overallLevel: String,   // Calculated Low, Medium, High aggregate
    val overallSummary: String
)

object BehavioralInferenceEngine {
    private const val TAG = "BehavioralInference"

    fun analyze(metrics: UsageMetrics): MentalStateReport {
        // --- 1. Stress Scoring (Interaction Intensity & Arousal) ---
        var sScore = 0
        if (metrics.scrolling_speed_avg > 480) sScore += 2
        if (metrics.typing_cps > 9.5f) sScore += 1 // Higher threshold for fast typists
        if (metrics.backspace_rate > 25f) sScore += 1 // Higher correction threshold
        if (metrics.unlock_count > 150) sScore += 1 // 150+ is a more robust trigger
        val sLevel = when {
            sScore >= 3 -> "High"
            sScore >= 1 -> "Medium"
            else -> "Low"
        }

        // --- 2. Anxiety Scoring (Hesitation & Fragmentation) ---
        var aScore = 0
        if (metrics.app_switch_count_per_hour > 45) aScore += 2 // Much higher for modern multitasking
        if (metrics.scroll_erraticness > 1.5f) aScore += 1
        if (metrics.typing_pauses_count > 50) aScore += 1 // Higher hesitation trigger
        if (metrics.typing_hesitation_ms > 1500) aScore += 1
        val aLevel = when {
            aScore >= 3 -> "High"
            aScore >= 1 -> "Medium"
            else -> "Low"
        }

        // --- 3. Burnout Scoring (Usage Duration & Fatigue) ---
        var bScore = 0
        if (metrics.screen_time > 800) bScore += 2 // 13.3 hours (Extreme)
        if (metrics.screen_time > 480) bScore += 1 // 8 hours (High)
        if (metrics.usage_consistency_shift > 1.45f) bScore += 1 // 45% increase from baseline
        if (metrics.typing_cps < 2.0f && metrics.screen_time > 360) bScore += 1 // Deeper Fatigue slowing
        val bLevel = when {
            bScore >= 3 -> "High"
            bScore >= 1 -> "Medium"
            else -> "Low"
        }

        // --- 4. Addiction Scoring (Habitual Loops & Social Fixation) ---
        var adScore = 0
        val totalTime = metrics.screen_time.toFloat().coerceAtLeast(1f)
        val socialRatio = metrics.social_time.toFloat() / totalTime
        if (socialRatio > 0.75f) adScore += 2 // 75%+ social fixation
        if (socialRatio > 0.5f) adScore += 1
        if (metrics.session_count > 250) adScore += 1 // Much higher session churn
        val adLevel = when {
            adScore >= 3 -> "High"
            adScore >= 1 -> "Medium"
            else -> "Low"
        }

        // --- 5. Generate Qualitative Insights (No Raw Numbers!) ---
        val sInsight = when {
            sLevel == "High" -> "Intense, rapid interaction arousal detected"
            sLevel == "Medium" -> "Elevated interaction rhythm and frequency"
            else -> "Stable and controlled physical engagement"
        }

        val aInsight = when {
            aLevel == "High" -> "Highly fragmented focus and hesitation gaps"
            aLevel == "Medium" -> "Frequent app-to-app shifts and search loops"
            else -> "Consistent focus and rhythmic scrolling"
        }

        val bInsight = when {
            bLevel == "High" -> "Prolonged usage fatigue and interaction slowdown"
            bLevel == "Medium" -> "Exceeding daily usage consistency window"
            else -> "Usage within personal baseline consistency"
        }

        val adInsight = when {
            adLevel == "High" -> "Deep social loops and habitual session churn"
            adLevel == "Medium" -> "Rising social-fixation and repeat unlocks"
            else -> "Healthy application-type balance"
        }

        // --- Determine Primary State ---
        // Priority order for summary: Burnout > Stress > Anxiety > Addiction
        val primaryState = when {
            bLevel == "High" -> "Burnout Alert"
            sLevel == "High" -> "High Interaction Stress"
            aLevel == "High" -> "Anxiety Signals"
            adLevel == "High" -> "Addiction Warning"
            bLevel == "Medium" || sLevel == "Medium" || aLevel == "Medium" || adLevel == "Medium" -> "Interaction Shift"
            else -> "Balanced & Calm"
        }

        val summary = when {
            primaryState == "Burnout Alert" -> "Critical digital fatigue detected. Please take a long break."
            primaryState == "High Interaction Stress" -> "High physical arousal in interaction. Consider mindfulness."
            primaryState == "Anxiety Signals" -> "Rapid app jumping and hesitation detected. Try focusing on one task."
            primaryState == "Addiction Warning" -> "Social media loops detected. Time for a digital detox."
            primaryState == "Interaction Shift" -> "Minor fluctuations in wellbeing metrics. Stay mindful."
            else -> "Digital wellbeing metrics are currently optimal."
        }

        // --- Weighted Overall Level Calculation (Point Based) ---
        // High = 3 pts, Medium = 1 pt. 
        // 5+ = High, 2-4 = Medium, <2 = Low
        var severityPoints = 0
        if (sLevel == "High") severityPoints += 3 else if (sLevel == "Medium") severityPoints += 1
        if (aLevel == "High") severityPoints += 3 else if (aLevel == "Medium") severityPoints += 1
        if (bLevel == "High") severityPoints += 3 else if (bLevel == "Medium") severityPoints += 1
        if (adLevel == "High") severityPoints += 3 else if (adLevel == "Medium") severityPoints += 1

        val overallLevel = when {
            severityPoints >= 5 -> "High"
            severityPoints >= 2 -> "Medium"
            else -> "Low"
        }

        val report = MentalStateReport(
            stressLevel = sLevel,
            stressInsight = sInsight,
            anxietyLevel = aLevel,
            anxietyInsight = aInsight,
            burnoutLevel = bLevel,
            burnoutInsight = bInsight,
            addictionLevel = adLevel,
            addictionInsight = adInsight,
            primaryState = primaryState,
            overallLevel = overallLevel,
            overallSummary = summary
        )

        Log.d(TAG, "Analysis v9.1 (Calibrated Behavioral): $report | Typing: ${metrics.typing_cps} CPS")
        return report
    }
}
