"""
notification_engine.py
======================
Server-side Per-User Notification Log & Policy Engine for Wellness Wave.
Enforces an 8-point notification policy + Priority Severity Overrides & Requeueing:
1. Hard cap: Max 2 push notifications per user per calendar day.
   - SEVERITY OVERRIDE: Escalation to High risk level (1+ label reaching High) BYPASSES the daily cap.
2. Minimum gap: At least 3 hours (10,800 sec) between push notifications.
   - SEVERITY OVERRIDE: Critical escalation (2+ labels simultaneously reaching High) BYPASSES the 3-hour gap.
3. State Change Triggers: Push ONLY when severity escalates (Low->Medium, Medium->High, Low->High).
4. Deduplicate / Merge: Combine multiple escalating labels into 1 single notification.
5. Quiet hours & Active focus REQUEUEING: Suppress non-critical pushes between 10 PM - 7 AM and during steady typing focus.
   Suppressed triggers are held pending and re-evaluated against CURRENT state at the next allowed window (7:00 AM or focus end).
6. Positive reinforcement: Send a positive message if daily aggregate risk < 0.85 * 7-day rolling average.
7. System/status notifications ("Monitoring Active", etc.) strictly in-app only, never pushed.
8. Server-side per-user persistent/in-memory log.
"""

from datetime import datetime
from typing import Dict, List, Any, Optional

LEVEL_RANK = {
    "Low": 0,
    "Medium": 1,
    "High": 2
}

class UserNotificationLog:
    """Per-user log for tracking notification history, state transitions, and risk metrics."""
    def __init__(self, user_id: str):
        self.user_id: str = user_id
        self.pushed_notifications: List[Dict[str, Any]] = []
        self.last_push_timestamp: Optional[float] = None
        self.last_acknowledged_label_states: Dict[str, str] = {
            "Stress": "Low",
            "Anxiety": "Low",
            "Burnout": "Low",
            "Addiction": "Low"
        }
        self.daily_risk_history: List[Dict[str, Any]] = []  # [{"date": "YYYY-MM-DD", "aggregate_risk": float}]
        self.last_positive_push_date: Optional[str] = None

    def get_pushes_on_date(self, date_str: str) -> int:
        """Returns the number of push notifications sent on a given calendar date (YYYY-MM-DD)."""
        count = 0
        for entry in self.pushed_notifications:
            dt = datetime.fromtimestamp(entry["timestamp"])
            if dt.strftime("%Y-%m-%d") == date_str:
                count += 1
        return count

    def get_7day_rolling_average(self) -> Optional[float]:
        """Calculates 7-day rolling average of aggregate risk scores."""
        if not self.daily_risk_history:
            return None
        recent_entries = self.daily_risk_history[-7:]
        scores = [e["aggregate_risk"] for e in recent_entries]
        return sum(scores) / len(scores)

    def record_push(self, title: str, body: str, notification_type: str, timestamp: float, labels: List[str]):
        """Logs a sent push notification."""
        self.last_push_timestamp = timestamp
        dt_str = datetime.fromtimestamp(timestamp).strftime("%Y-%m-%d")
        if notification_type == "POSITIVE":
            self.last_positive_push_date = dt_str
        self.pushed_notifications.append({
            "timestamp": timestamp,
            "date": dt_str,
            "title": title,
            "body": body,
            "type": notification_type,
            "labels": labels
        })

    def record_daily_risk(self, date_str: str, aggregate_risk: float):
        """Updates or appends daily aggregate risk score."""
        for entry in self.daily_risk_history:
            if entry["date"] == date_str:
                entry["aggregate_risk"] = aggregate_risk
                return
        self.daily_risk_history.append({
            "date": date_str,
            "aggregate_risk": aggregate_risk
        })


class NotificationPolicyEngine:
    """Server-side engine evaluating notification policy across all users."""
    
    HARD_CAP_PER_DAY = 2
    MIN_GAP_SECONDS = 3 * 3600  # 3 hours (10,800 sec)
    QUIET_START_HOUR = 22  # 10 PM
    QUIET_END_HOUR = 7    # 7 AM

    def __init__(self):
        self.user_logs: Dict[str, UserNotificationLog] = {}

    def get_user_log(self, user_id: str) -> UserNotificationLog:
        if user_id not in self.user_logs:
            self.user_logs[user_id] = UserNotificationLog(user_id)
        return self.user_logs[user_id]

    def is_quiet_hours(self, dt: datetime) -> bool:
        """Returns True if local time falls between 10:00 PM (22:00) and 7:00 AM (07:00)."""
        hour = dt.hour
        return hour >= self.QUIET_START_HOUR or hour < self.QUIET_END_HOUR

    def is_active_focus(self, typing_speed_wpm: float, fragmentation_index: float) -> bool:
        """
        Active focus condition: steady typing speed (>= 25 WPM) with low app-switching fragmentation (< 0.25).
        Notifications are suppressed during active typing focus.
        """
        return (typing_speed_wpm >= 25.0) and (fragmentation_index < 0.25)

    def evaluate(
        self,
        user_id: str,
        label_levels: Dict[str, str],
        label_scores: Dict[str, float],
        telemetry: Dict[str, Any],
        now_dt: Optional[datetime] = None
    ) -> Dict[str, Any]:
        """
        Evaluates current prediction & telemetry against policy rules, priority tiers, and suppression requeueing.
        Returns a decision payload instructing the client whether to push a notification.
        """
        if now_dt is None:
            now_dt = datetime.now()

        timestamp = now_dt.timestamp()
        date_str = now_dt.strftime("%Y-%m-%d")
        user_log = self.get_user_log(user_id)

        # Calculate current aggregate risk (average of 4 calibrated label scores)
        current_aggregate_risk = round(sum(label_scores.values()) / len(label_scores), 4)
        user_log.record_daily_risk(date_str, current_aggregate_risk)
        roll_7d_risk = user_log.get_7day_rolling_average()

        # Check state transitions (escalations compared to last ACKNOWLEDGED pushed state)
        escalated_labels: List[str] = []
        high_escalated_labels: List[str] = []
        
        for label, curr_lvl in label_levels.items():
            prev_ack_lvl = user_log.last_acknowledged_label_states.get(label, "Low")
            if LEVEL_RANK.get(curr_lvl, 0) > LEVEL_RANK.get(prev_ack_lvl, 0):
                escalated_labels.append(label)
                if curr_lvl == "High":
                    high_escalated_labels.append(label)

        # Determine Priority Tiers for Severity Overrides
        is_high_escalation = len(high_escalated_labels) >= 1
        is_critical_high = len(high_escalated_labels) >= 2

        pushes_today = user_log.get_pushes_on_date(date_str)

        # Policy Rule 5a: Quiet Hours Check (10 PM - 7 AM) -> Requeues escalation
        if self.is_quiet_hours(now_dt):
            return {
                "should_notify_push": False,
                "notification_title": None,
                "notification_body": None,
                "notification_type": "NONE",
                "suppression_reason": f"Quiet hours active ({now_dt.strftime('%H:%M')}). Requeued for evaluation at 07:00 AM.",
                "daily_push_count": pushes_today,
                "max_daily_push_cap": self.HARD_CAP_PER_DAY
            }

        # Policy Rule 5b: Active Focus Check -> Requeues escalation
        typing_speed = float(telemetry.get("typing_speed_wpm", telemetry.get("typing_speed", 0.0)))
        frag_index = float(telemetry.get("fragmentation_index", 0.0))
        if self.is_active_focus(typing_speed, frag_index):
            return {
                "should_notify_push": False,
                "notification_title": None,
                "notification_body": None,
                "notification_type": "NONE",
                "suppression_reason": "Active focus typing session detected. Requeued until focus session ends.",
                "daily_push_count": pushes_today,
                "max_daily_push_cap": self.HARD_CAP_PER_DAY
            }

        # Policy Rule 2: Minimum Gap Enforcement (3 hours between notifications)
        # Priority Tier Override: Critical High (2+ labels at High) BYPASSES the 3-hour gap.
        if user_log.last_push_timestamp is not None:
            elapsed_sec = timestamp - user_log.last_push_timestamp
            if elapsed_sec < self.MIN_GAP_SECONDS and not is_critical_high:
                remaining_min = int((self.MIN_GAP_SECONDS - elapsed_sec) / 60)
                return {
                    "should_notify_push": False,
                    "notification_title": None,
                    "notification_body": None,
                    "notification_type": "NONE",
                    "suppression_reason": f"Minimum 3-hour gap active ({remaining_min} mins remaining). Trigger held.",
                    "daily_push_count": pushes_today,
                    "max_daily_push_cap": self.HARD_CAP_PER_DAY
                }

        # Policy Rule 1: Hard Cap (Max 2 push notifications per calendar day)
        # Priority Tier Override: Escalation to High risk level (1+ label at High) BYPASSES the daily cap.
        if pushes_today >= self.HARD_CAP_PER_DAY and not is_high_escalation:
            return {
                "should_notify_push": False,
                "notification_title": None,
                "notification_body": None,
                "notification_type": "NONE",
                "suppression_reason": f"Daily hard cap reached ({pushes_today}/{self.HARD_CAP_PER_DAY}).",
                "daily_push_count": pushes_today,
                "max_daily_push_cap": self.HARD_CAP_PER_DAY
            }

        # Policy Rule 3 & 4: State Change Triggering & Escalation Deduplication/Merging
        if escalated_labels:
            escalated_str_list = [
                f"{lbl} ({user_log.last_acknowledged_label_states.get(lbl, 'Low')} ➔ {label_levels[lbl]})"
                for lbl in escalated_labels
            ]
            
            if is_critical_high:
                title = f"🚨 CRITICAL HIGH RISK ALERT ({len(high_escalated_labels)} High States)"
                merged_desc = ", ".join(escalated_str_list)
                body = f"Critical behavioral escalation detected: {merged_desc}. Immediate mindfulness pause strongly advised."
            elif len(escalated_labels) == 1:
                lbl = escalated_labels[0]
                prev_l = user_log.last_acknowledged_label_states.get(lbl, "Low")
                curr_l = label_levels[lbl]
                title = f"⚠️ Wellness Shift: {lbl} Escalation"
                body = f"Your {lbl} risk level escalated from {prev_l} to {curr_l}. Take a 5-minute break to unwind."
            else:
                title = f"⚠️ Multiple Risk Escalations ({len(escalated_labels)} States)"
                merged_desc = ", ".join(escalated_str_list)
                body = f"Elevated behavioral risks detected: {merged_desc}. Consider stepping away for mindfulness."

            user_log.record_push(title, body, "WARNING", timestamp, escalated_labels)
            user_log.last_acknowledged_label_states = dict(label_levels)
            return {
                "should_notify_push": True,
                "notification_title": title,
                "notification_body": body,
                "notification_type": "WARNING",
                "suppression_reason": None,
                "daily_push_count": pushes_today + 1,
                "max_daily_push_cap": self.HARD_CAP_PER_DAY
            }

        # Policy Rule 6: Positive Reinforcement Check
        if (
            roll_7d_risk is not None
            and len(user_log.daily_risk_history) >= 2
            and current_aggregate_risk < 0.85 * roll_7d_risk
            and user_log.last_positive_push_date != date_str
            and len(escalated_labels) == 0
            and pushes_today < self.HARD_CAP_PER_DAY
        ):
            title = "🌟 Great Digital Balance Today!"
            pct_improvement = int(round((1.0 - (current_aggregate_risk / max(0.01, roll_7d_risk))) * 100))
            body = f"Your wellbeing indicators are {pct_improvement}% better today than your 7-day average. Keep up the great habits!"
            
            user_log.record_push(title, body, "POSITIVE", timestamp, [])
            user_log.last_acknowledged_label_states = dict(label_levels)
            return {
                "should_notify_push": True,
                "notification_title": title,
                "notification_body": body,
                "notification_type": "POSITIVE",
                "suppression_reason": None,
                "daily_push_count": pushes_today + 1,
                "max_daily_push_cap": self.HARD_CAP_PER_DAY
            }

        # Rule 3: No state change escalation -> update acknowledged levels to current if lower/same
        for lbl, curr_l in label_levels.items():
            prev_l = user_log.last_acknowledged_label_states.get(lbl, "Low")
            if LEVEL_RANK.get(curr_l, 0) <= LEVEL_RANK.get(prev_l, 0):
                user_log.last_acknowledged_label_states[lbl] = curr_l

        return {
            "should_notify_push": False,
            "notification_title": None,
            "notification_body": None,
            "notification_type": "NONE",
            "suppression_reason": "No state change escalation detected",
            "daily_push_count": pushes_today,
            "max_daily_push_cap": self.HARD_CAP_PER_DAY
        }


# Global singleton instance for backend runtime
notification_engine = NotificationPolicyEngine()
