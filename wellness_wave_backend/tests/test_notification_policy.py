"""
test_notification_policy.py
============================
Test suite for server-side NotificationPolicyEngine enforcing:
1. 8-point notification policy rules.
2. Severity Override Priority Tier (High escalation bypasses daily cap; 2+ High bypasses cap AND 3h gap).
3. Quiet Hours & Active Focus Requeueing (Re-evaluated against CURRENT state at 7:00 AM / focus end).
"""

import os
import sys
from datetime import datetime, timedelta

# Add backend root to path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

from api.notification_engine import NotificationPolicyEngine, UserNotificationLog

def test_hard_cap_enforcement_and_high_override():
    print("\n--- TEST 1: Hard Cap Enforcement & High-Severity Cap Override ---")
    engine = NotificationPolicyEngine()
    user_id = "test_user_high_override"
    
    t0 = datetime(2026, 9, 22, 10, 0, 0)
    
    # Push 1 (10:00 AM): Low -> Medium for Stress (Minor escalation)
    dec1 = engine.evaluate(
        user_id,
        {"Stress": "Medium", "Anxiety": "Low", "Burnout": "Low", "Addiction": "Low"},
        {"Stress": 0.5, "Anxiety": 0.1, "Burnout": 0.1, "Addiction": 0.1},
        {"typing_speed_wpm": 10.0, "fragmentation_index": 0.5},
        now_dt=t0
    )
    print(f"Push 1 (10:00 AM, Low->Medium Stress): Push = {dec1['should_notify_push']}, Count = {dec1['daily_push_count']}/{dec1['max_daily_push_cap']}")
    assert dec1['should_notify_push'] is True
    assert dec1['daily_push_count'] == 1

    # Push 2 (02:00 PM): Low -> Medium for Anxiety (Minor escalation, gap ok)
    t1 = t0 + timedelta(hours=4)
    dec2 = engine.evaluate(
        user_id,
        {"Stress": "Medium", "Anxiety": "Medium", "Burnout": "Low", "Addiction": "Low"},
        {"Stress": 0.5, "Anxiety": 0.55, "Burnout": 0.1, "Addiction": 0.1},
        {"typing_speed_wpm": 10.0, "fragmentation_index": 0.5},
        now_dt=t1
    )
    print(f"Push 2 (02:00 PM, Low->Medium Anxiety): Push = {dec2['should_notify_push']}, Count = {dec2['daily_push_count']}/{dec2['max_daily_push_cap']}")
    assert dec2['should_notify_push'] is True
    assert dec2['daily_push_count'] == 2

    # Push 3 Candidate (06:00 PM): Low -> Medium for Burnout (Minor escalation, cap reached)
    t2 = t1 + timedelta(hours=4)
    dec3_minor = engine.evaluate(
        user_id,
        {"Stress": "Medium", "Anxiety": "Medium", "Burnout": "Medium", "Addiction": "Low"},
        {"Stress": 0.5, "Anxiety": 0.55, "Burnout": 0.55, "Addiction": 0.1},
        {"typing_speed_wpm": 10.0, "fragmentation_index": 0.5},
        now_dt=t2
    )
    print(f"Push 3 Candidate (06:00 PM, Minor Medium Escalation): Push = {dec3_minor['should_notify_push']}, Reason = '{dec3_minor['suppression_reason']}'")
    assert dec3_minor['should_notify_push'] is False
    assert "Daily hard cap reached" in dec3_minor['suppression_reason']

    # Push 3 Override (06:00 PM): Low -> High for Burnout (HIGH SEVERITY ESCALATION -> BYPASSES CAP)
    dec3_high = engine.evaluate(
        user_id,
        {"Stress": "Medium", "Anxiety": "Medium", "Burnout": "High", "Addiction": "Low"},
        {"Stress": 0.5, "Anxiety": 0.55, "Burnout": 0.85, "Addiction": 0.1},
        {"typing_speed_wpm": 10.0, "fragmentation_index": 0.5},
        now_dt=t2
    )
    print(f"Push 3 Override (06:00 PM, Low->High Burnout): Push = {dec3_high['should_notify_push']}, Title = '{dec3_high['notification_title']}'")
    assert dec3_high['should_notify_push'] is True
    assert "Burnout Escalation" in dec3_high['notification_title'] or "Burnout" in dec3_high['notification_body']
    print("[PASS] High severity escalation successfully bypassed 2/day daily hard cap!")


def test_critical_high_gap_override():
    print("\n--- TEST 2: Critical High (2+ High States) Bypasses 3-Hour Gap ---")
    engine = NotificationPolicyEngine()
    user_id = "test_user_critical_gap"
    t0 = datetime(2026, 9, 22, 10, 0, 0)

    # Initial Push at 10:00 AM
    dec1 = engine.evaluate(
        user_id,
        {"Stress": "Medium", "Anxiety": "Low", "Burnout": "Low", "Addiction": "Low"},
        {"Stress": 0.5, "Anxiety": 0.1, "Burnout": 0.1, "Addiction": 0.1},
        {"typing_speed_wpm": 10.0, "fragmentation_index": 0.5},
        now_dt=t0
    )
    assert dec1['should_notify_push'] is True

    # 1 hour later (11:00 AM, gap active < 3h): 2 labels simultaneously jump to HIGH (Stress & Anxiety)
    t1 = t0 + timedelta(hours=1)
    dec_critical = engine.evaluate(
        user_id,
        {"Stress": "High", "Anxiety": "High", "Burnout": "Low", "Addiction": "Low"},
        {"Stress": 0.85, "Anxiety": 0.90, "Burnout": 0.1, "Addiction": 0.1},
        {"typing_speed_wpm": 10.0, "fragmentation_index": 0.5},
        now_dt=t1
    )
    print(f"1-Hour Gap Critical High Push: Push = {dec_critical['should_notify_push']}, Title = '{dec_critical['notification_title']}'")
    assert dec_critical['should_notify_push'] is True
    assert "CRITICAL HIGH RISK ALERT" in dec_critical['notification_title']
    print("[PASS] 2+ High critical escalation successfully bypassed 3-hour gap!")


def test_quiet_hours_requeue_and_morning_delivery():
    print("\n--- TEST 3 & 4: Quiet Hours Requeueing & 07:00 AM Re-evaluation ---")
    engine = NotificationPolicyEngine()
    user_id_persistent = "test_user_requeue_persistent"
    user_id_subsided = "test_user_requeue_subsided"

    # Case A: Persistent Elevated State (11:45 PM -> 07:00 AM)
    t_night = datetime(2026, 9, 22, 23, 45, 0)
    dec_night = engine.evaluate(
        user_id_persistent,
        {"Stress": "High", "Anxiety": "Low", "Burnout": "Low", "Addiction": "Low"},
        {"Stress": 0.85, "Anxiety": 0.1, "Burnout": 0.1, "Addiction": 0.1},
        {"typing_speed_wpm": 10.0, "fragmentation_index": 0.5},
        now_dt=t_night
    )
    print(f"11:45 PM Trigger: Push = {dec_night['should_notify_push']}, Reason = '{dec_night['suppression_reason']}'")
    assert dec_night['should_notify_push'] is False
    assert "Quiet hours active" in dec_night['suppression_reason']
    assert "Requeued for evaluation at 07:00 AM" in dec_night['suppression_reason']

    # Next Morning at 07:00 AM (Quiet hours ended, High state persists)
    t_morning = datetime(2026, 9, 23, 7, 0, 0)
    dec_morning = engine.evaluate(
        user_id_persistent,
        {"Stress": "High", "Anxiety": "Low", "Burnout": "Low", "Addiction": "Low"},
        {"Stress": 0.85, "Anxiety": 0.1, "Burnout": 0.1, "Addiction": 0.1},
        {"typing_speed_wpm": 10.0, "fragmentation_index": 0.5},
        now_dt=t_morning
    )
    print(f"07:00 AM Re-evaluation (Persistent State): Push = {dec_morning['should_notify_push']}, Title = '{dec_morning['notification_title']}'")
    assert dec_morning['should_notify_push'] is True
    assert "Stress" in dec_morning['notification_title'] or "Stress" in dec_morning['notification_body']

    # Case B: Subsided State (11:45 PM -> 07:00 AM)
    # 11:45 PM: Low -> Medium Anxiety escalation during quiet hours
    engine.evaluate(
        user_id_subsided,
        {"Stress": "Low", "Anxiety": "Medium", "Burnout": "Low", "Addiction": "Low"},
        {"Stress": 0.25, "Anxiety": 0.5, "Burnout": 0.25, "Addiction": 0.25},
        {"typing_speed_wpm": 10.0, "fragmentation_index": 0.5},
        now_dt=t_night
    )
    # Next Morning at 07:00 AM: User anxiety naturally returned to Low overnight
    dec_subsided = engine.evaluate(
        user_id_subsided,
        {"Stress": "Low", "Anxiety": "Low", "Burnout": "Low", "Addiction": "Low"},
        {"Stress": 0.25, "Anxiety": 0.25, "Burnout": 0.25, "Addiction": 0.25},
        {"typing_speed_wpm": 10.0, "fragmentation_index": 0.5},
        now_dt=t_morning
    )
    print(f"07:00 AM Re-evaluation (Subsided State): Push = {dec_subsided['should_notify_push']}, Reason = '{dec_subsided['suppression_reason']}'")
    assert dec_subsided['should_notify_push'] is False
    assert "No state change escalation detected" in dec_subsided['suppression_reason']

    print("[PASS] Quiet hours requeueing and 07:00 AM re-evaluation verified for both persistent and subsided states!")


def test_positive_reinforcement():
    print("\n--- TEST 5: Positive Reinforcement Notification ---")
    engine = NotificationPolicyEngine()
    user_id = "test_user_pos"

    user_log = engine.get_user_log(user_id)
    user_log.record_daily_risk("2026-09-19", 0.70)
    user_log.record_daily_risk("2026-09-20", 0.68)
    user_log.record_daily_risk("2026-09-21", 0.72)

    t_today = datetime(2026, 9, 22, 15, 0, 0)
    dec_pos = engine.evaluate(
        user_id,
        {"Stress": "Low", "Anxiety": "Low", "Burnout": "Low", "Addiction": "Low"},
        {"Stress": 0.2, "Anxiety": 0.2, "Burnout": 0.2, "Addiction": 0.2},
        {"typing_speed_wpm": 10.0, "fragmentation_index": 0.5},
        now_dt=t_today
    )
    print(f"Positive Push: {dec_pos['should_notify_push']}, Type = {dec_pos['notification_type']}")
    print(f"Title: {dec_pos['notification_title']}")
    print(f"Body:  {dec_pos['notification_body']}")
    assert dec_pos['should_notify_push'] is True
    assert dec_pos['notification_type'] == "POSITIVE"
    assert "Great Digital Balance" in dec_pos['notification_title']
    print("[PASS] Positive reinforcement notification verified!")


if __name__ == "__main__":
    test_hard_cap_enforcement_and_high_override()
    test_critical_high_gap_override()
    test_quiet_hours_requeue_and_morning_delivery()
    test_positive_reinforcement()
    print("\nALL UPDATED NOTIFICATION POLICY TESTS PASSED SUCCESSFULLY!")
