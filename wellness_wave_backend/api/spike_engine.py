"""
spike_engine.py
===============
Server-side SpikeDetectionEngine for tracking real-time risk spikes, recovery time,
and summary metrics for Wellness Wave.
"""

import os
import json
from datetime import datetime, timedelta
from typing import Dict, List, Any, Optional

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
META_PATH = os.path.join(BASE_DIR, "models", "model_meta.json")

# Empirical production cutoffs (sigma=0.05 calibrated p66 thresholds)
DEFAULT_THRESHOLDS = {
    "Stress": {"p33": 0.2110, "p66": 0.4364},
    "Anxiety": {"p33": 0.1332, "p66": 0.2857},
    "Burnout": {"p33": 0.3070, "p66": 0.4627},
    "Addiction": {"p33": 0.2819, "p66": 0.3791}
}


def load_empiric_thresholds() -> Dict[str, Dict[str, float]]:
    if os.path.exists(META_PATH):
        try:
            with open(META_PATH, "r") as f:
                meta = json.load(f)
                th = meta.get("thresholds", {})
                if th:
                    return th
        except Exception as e:
            print(f"[SpikeEngine] Warning loading thresholds: {e}")
    return DEFAULT_THRESHOLDS


class SpikeEvent:
    def __init__(self, spike_id: str, user_id: str, label: str, start_time: float, peak_score: float, trigger_summary: str):
        self.spike_id: str = spike_id
        self.user_id: str = user_id
        self.label: str = label
        self.start_time: float = start_time
        self.end_time: Optional[float] = None
        self.peak_score: float = peak_score
        self.recovery_time_min: Optional[int] = None
        self.trigger_summary: str = trigger_summary
        self.is_resolved: bool = False

    def to_dict(self) -> Dict[str, Any]:
        return {
            "spike_id": self.spike_id,
            "user_id": self.user_id,
            "label": self.label,
            "start_time": datetime.fromtimestamp(self.start_time).isoformat(),
            "end_time": datetime.fromtimestamp(self.end_time).isoformat() if self.end_time else None,
            "peak_score": round(self.peak_score, 4),
            "recovery_time_min": self.recovery_time_min,
            "trigger_summary": self.trigger_summary,
            "is_resolved": self.is_resolved
        }


class SpikeDetectionEngine:
    def __init__(self):
        self.thresholds = load_empiric_thresholds()
        self.active_spikes: Dict[str, Dict[str, SpikeEvent]] = {}  # {user_id: {label: SpikeEvent}}
        self.spike_history: Dict[str, List[SpikeEvent]] = {}      # {user_id: [SpikeEvent]}
        self.raw_prediction_logs: Dict[str, List[Dict[str, Any]]] = {} # {user_id: [pred_records]}

    def build_trigger_summary(self, label: str, telemetry: Dict[str, Any]) -> str:
        app_switches = telemetry.get("app_switch_count", telemetry.get("app_switches", 0))
        scroll_speed = telemetry.get("scroll_speed", 0.0)
        social_mins = telemetry.get("social_app_minutes", 0.0)
        night_mins = telemetry.get("night_usage_minutes", telemetry.get("night_usage", 0.0))

        if label == "Stress":
            if scroll_speed > 400 or app_switches > 100:
                return "Rapid app switching & high scroll velocity"
            return "Elevated interaction agitation"
        elif label == "Anxiety":
            if social_mins > 60 or app_switches > 100:
                return "Frequent social app unlocks & shrinking open gaps"
            return "High arousal unlocking frequency"
        elif label == "Burnout":
            if night_mins > 30:
                return "High late-night usage & extended active hours"
            return "Cumulative daily screen time strain"
        elif label == "Addiction":
            if social_mins > 120:
                return "Excessive social media screen time ratio"
            return "High social app open frequency"
        return "Behavioral metric threshold surge"

    def check(self, user_id: str, label_scores: Dict[str, float], label_levels: Dict[str, str], telemetry: Dict[str, Any], now_dt: Optional[datetime] = None) -> List[SpikeEvent]:
        if now_dt is None:
            now_dt = datetime.now()
        timestamp = now_dt.timestamp()

        # 1. Store raw prediction log (last 20 entries)
        if user_id not in self.raw_prediction_logs:
            self.raw_prediction_logs[user_id] = []
        self.raw_prediction_logs[user_id].append({
            "timestamp": now_dt.isoformat(),
            "label_scores": {k: round(v, 4) for k, v in label_scores.items()},
            "label_levels": dict(label_levels)
        })
        if len(self.raw_prediction_logs[user_id]) > 20:
            self.raw_prediction_logs[user_id] = self.raw_prediction_logs[user_id][-20:]

        if user_id not in self.active_spikes:
            self.active_spikes[user_id] = {}
        if user_id not in self.spike_history:
            self.spike_history[user_id] = []

        modified_events = []

        for label, score in label_scores.items():
            th_high = self.thresholds.get(label, {}).get("p66", 0.5)

            if score >= th_high:
                # Spike Active or Starting
                if label not in self.active_spikes[user_id]:
                    spike_id = f"spike_{int(timestamp)}_{label}"
                    trigger = self.build_trigger_summary(label, telemetry)
                    new_spike = SpikeEvent(spike_id, user_id, label, timestamp, score, trigger)
                    self.active_spikes[user_id][label] = new_spike
                    modified_events.append(new_spike)
                    print(f"[SpikeEngine] NEW SPIKE DETECTED: {user_id} - {label} (Score: {score:.4f} >= {th_high})")
                else:
                    spike = self.active_spikes[user_id][label]
                    spike.peak_score = max(spike.peak_score, score)
            else:
                # Below High threshold -> Resolve if previously active
                if label in self.active_spikes[user_id]:
                    spike = self.active_spikes[user_id].pop(label)
                    spike.end_time = timestamp
                    elapsed = max(1, int(round((timestamp - spike.start_time) / 60.0)))
                    spike.recovery_time_min = elapsed
                    spike.is_resolved = True
                    self.spike_history[user_id].append(spike)
                    modified_events.append(spike)
                    print(f"[SpikeEngine] SPIKE RESOLVED: {user_id} - {label} (Recovery Time: {elapsed} mins)")

        return modified_events

    def get_last_raw_predictions(self, user_id: str, limit: int = 20) -> List[Dict[str, Any]]:
        return self.raw_prediction_logs.get(user_id, [])[-limit:]

    def get_recovery_summary(self, user_id: str) -> Dict[str, Any]:
        user_history = self.spike_history.get(user_id, [])
        active = list(self.active_spikes.get(user_id, {}).values())
        raw_logs = self.raw_prediction_logs.get(user_id, [])

        has_sufficient_history = len(raw_logs) > 0 or len(user_history) > 0 or len(active) > 0

        now_dt = datetime.now()
        one_week_ago = (now_dt - timedelta(days=7)).timestamp()
        two_weeks_ago = (now_dt - timedelta(days=14)).timestamp()

        trends = {}
        for label in ["Stress", "Anxiety", "Burnout", "Addiction"]:
            # Spikes starting in past 7 days (including active & resolved)
            all_current_spikes = [s for s in (user_history + active) if s.label == label and s.start_time >= one_week_ago]
            spikes_count = len(all_current_spikes)

            # Prior week spikes (days 8-14 ago) for baseline comparison
            prior_week_spikes = [s for s in user_history if s.label == label and two_weeks_ago <= s.start_time < one_week_ago]

            # Resolved recovery times
            resolved_current = [s.recovery_time_min for s in all_current_spikes if s.is_resolved and s.recovery_time_min is not None]
            resolved_prior = [s.recovery_time_min for s in prior_week_spikes if s.is_resolved and s.recovery_time_min is not None]

            if spikes_count == 0:
                # 0 spikes this week -> return null for all recovery stats
                avg_rec = None
                pct_trend = None
                is_faster = None
                trigger_summary = None
            else:
                avg_rec = int(round(sum(resolved_current) / len(resolved_current))) if resolved_current else None
                
                # Triggers
                triggers = [s.trigger_summary for s in all_current_spikes if s.trigger_summary]
                trigger_summary = max(set(triggers), key=triggers.count) if triggers else None

                # Calculate velocity trend percentage against prior week baseline
                if resolved_prior and len(resolved_prior) > 0 and avg_rec is not None:
                    avg_prior = sum(resolved_prior) / float(len(resolved_prior))
                    if avg_prior > 0:
                        pct_change = int(round(((avg_prior - avg_rec) / avg_prior) * 100.0))
                        is_faster = bool(pct_change >= 0)
                        pct_trend = abs(pct_change)
                    else:
                        pct_trend = None
                        is_faster = None
                else:
                    # No prior week baseline exists -> return null/None (do NOT fabricate 30% default)
                    pct_trend = None
                    is_faster = None

            trends[label] = {
                "spikes_this_week": spikes_count,
                "avg_recovery_time_min": avg_rec,
                "recovery_velocity_trend_pct": pct_trend,
                "is_faster": is_faster,
                "trigger_summary": trigger_summary
            }

        return {
            "user_id": user_id,
            "has_sufficient_history": has_sufficient_history,
            "recovery_trends": trends
        }

    def clear_user_data(self, user_id: str):
        """Clean up test data for a user."""
        self.active_spikes.pop(user_id, None)
        self.spike_history.pop(user_id, None)
        self.raw_prediction_logs.pop(user_id, None)


# Global singleton instance
spike_engine = SpikeDetectionEngine()
