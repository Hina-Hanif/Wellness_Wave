"""
prediction.py
=============
FastAPI Prediction Endpoint for Wellness Wave Behavioral Wellbeing Multi-Label Calibrated Model.
"""

import os
import json
import joblib
import numpy as np
import pandas as pd
from fastapi import APIRouter, HTTPException, Query
from datetime import datetime
from api.notification_engine import notification_engine
from api.spike_engine import spike_engine

router = APIRouter()

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODELS_DIR = os.path.join(BASE_DIR, "models")

MODEL_PATH = os.path.join(MODELS_DIR, "wellbeing_model.pkl")
PREPROCESSOR_PATH = os.path.join(MODELS_DIR, "preprocessor.pkl")
META_PATH = os.path.join(MODELS_DIR, "model_meta.json")

FEATURE_COLUMNS = [
    "screen_time_minutes", "unlock_count", "app_switch_count",
    "typing_speed_wpm", "scroll_speed", "night_usage_minutes",
    "social_app_minutes", "productive_app_minutes", "night_ratio",
    "unlock_intensity", "fragmentation_index", "agitation_score",
    "social_ratio", "social_opens_per_hour", "social_session_avg",
    "social_session_var", "late_night_social_ratio", "social_inter_open_gap_min",
    "prev_label_lag1", "roll_3d_screen_time_minutes", "roll_7d_screen_time_minutes",
    "roll_3d_social_ratio", "roll_7d_social_ratio", "roll_3d_app_switch_count",
    "roll_7d_app_switch_count", "roll_3d_scroll_speed", "roll_7d_scroll_speed",
    "roll_3d_typing_speed_wpm", "roll_7d_typing_speed_wpm",
    "dod_pct_screen_time_minutes", "dod_pct_app_switch_count",
    "dod_pct_scroll_speed", "dod_pct_typing_speed_wpm"
]

LABELS = ["Stress", "Anxiety", "Burnout", "Addiction"]


def load_ml_artifacts():
    print(f"\n[ML] Initializing Intelligence Engine from: {MODELS_DIR}")
    models_dict, preprocessor, meta = None, None, None

    if os.path.exists(MODEL_PATH):
        try:
            models_dict = joblib.load(MODEL_PATH)
            print(f"[ML] Multi-label Calibrated Models loaded from: {MODEL_PATH}")
        except Exception as e:
            print(f"[ML] ERROR loading model artifact: {e}")

    if os.path.exists(PREPROCESSOR_PATH):
        try:
            preprocessor = joblib.load(PREPROCESSOR_PATH)
            print(f"[ML] Preprocessor loaded from: {PREPROCESSOR_PATH}")
        except Exception as e:
            print(f"[ML] ERROR loading preprocessor artifact: {e}")

    if os.path.exists(META_PATH):
        try:
            with open(META_PATH, "r") as f:
                meta = json.load(f)
            print(f"[ML] Model Metadata loaded from: {META_PATH}")
        except Exception as e:
            print(f"[ML] ERROR loading metadata: {e}")

    return models_dict, preprocessor, meta


models_dict, preprocessor, meta = load_ml_artifacts()


def calculate_derived_features(screen_time_minutes: float, unlock_count: float, app_switch_count: float,
                                typing_speed_wpm: float, scroll_speed: float, night_usage_minutes: float,
                                social_app_minutes: float, productive_app_minutes: float):
    safe_screen_time = max(1.0, float(screen_time_minutes))

    night_ratio = float(np.clip(night_usage_minutes / safe_screen_time, 0.0, 1.0))
    unlock_intensity = float(unlock_count / safe_screen_time)

    switches_per_hour = app_switch_count / (safe_screen_time / 60.0)
    fragmentation_index = float(np.clip(switches_per_hour / 120.0, 0.0, 1.0))

    s_scroll = np.clip(scroll_speed / 1500.0, 0.0, 1.0)
    s_frag = fragmentation_index
    s_unlock = np.clip(unlock_intensity / 0.60, 0.0, 1.0)
    s_night = night_ratio
    s_typing_elev = np.clip((typing_speed_wpm - 30.0) / 70.0, 0.0, 1.0)

    raw_agitation = (
        0.30 * s_scroll +
        0.30 * s_frag +
        0.15 * s_unlock +
        0.15 * s_night +
        0.10 * s_typing_elev
    )
    agitation_score = float(np.clip(round(raw_agitation, 4), 0.0, 1.0))

    # Social App Feature Group
    social_ratio = float(np.clip(social_app_minutes / safe_screen_time, 0.0, 1.0))
    social_share = social_app_minutes / safe_screen_time
    social_unlocks = max(1, int(unlock_count * social_share))
    social_opens_per_hour = float(social_unlocks / (safe_screen_time / 60.0))
    social_session_avg = float(social_app_minutes / social_unlocks)
    social_session_var = float((social_session_avg * 0.35) ** 2)
    late_night_social_minutes = float(night_usage_minutes * social_share)
    late_night_social_ratio = float(np.clip(late_night_social_minutes / max(1.0, social_app_minutes), 0.0, 1.0))
    social_inter_open_gap_min = float(max(0.5, (1440.0 - safe_screen_time) / social_unlocks))

    return {
        "night_ratio": round(night_ratio, 4),
        "unlock_intensity": round(unlock_intensity, 4),
        "fragmentation_index": round(fragmentation_index, 4),
        "agitation_score": round(agitation_score, 4),
        "social_ratio": round(social_ratio, 4),
        "social_opens_per_hour": round(social_opens_per_hour, 4),
        "social_session_avg": round(social_session_avg, 2),
        "social_session_var": round(social_session_var, 2),
        "late_night_social_ratio": round(late_night_social_ratio, 4),
        "social_inter_open_gap_min": round(social_inter_open_gap_min, 2)
    }


def build_inference_features(raw_data: dict) -> pd.DataFrame:
    screen_time_minutes = float(raw_data.get("screen_time_minutes", raw_data.get("screen_time", 180.0)))
    unlock_count = int(raw_data.get("unlock_count", 45))
    app_switch_count = int(raw_data.get("app_switch_count", raw_data.get("app_switches", 50)))
    typing_speed_wpm = float(raw_data.get("typing_speed_wpm", raw_data.get("typing_speed", 50.0)))
    scroll_speed = float(raw_data.get("scroll_speed", 250.0))
    night_usage_minutes = float(raw_data.get("night_usage_minutes", raw_data.get("night_usage", 15.0)))

    if "social_app_minutes" in raw_data and raw_data["social_app_minutes"] is not None and not pd.isna(raw_data["social_app_minutes"]):
        social_app_minutes = float(raw_data["social_app_minutes"])
    else:
        social_app_minutes = round(screen_time_minutes * 0.35, 2)

    if "productive_app_minutes" in raw_data and raw_data["productive_app_minutes"] is not None and not pd.isna(raw_data["productive_app_minutes"]):
        productive_app_minutes = float(raw_data["productive_app_minutes"])
    else:
        productive_app_minutes = round(screen_time_minutes * 0.20, 2)

    derived = calculate_derived_features(
        screen_time_minutes, unlock_count, app_switch_count, typing_speed_wpm,
        scroll_speed, night_usage_minutes, social_app_minutes, productive_app_minutes
    )

    feature_dict = {
        "screen_time_minutes": round(screen_time_minutes, 2),
        "unlock_count": unlock_count,
        "app_switch_count": app_switch_count,
        "typing_speed_wpm": round(typing_speed_wpm, 2),
        "scroll_speed": round(scroll_speed, 2),
        "night_usage_minutes": round(night_usage_minutes, 2),
        "social_app_minutes": round(social_app_minutes, 2),
        "productive_app_minutes": round(productive_app_minutes, 2),
        "night_ratio": derived["night_ratio"],
        "unlock_intensity": derived["unlock_intensity"],
        "fragmentation_index": derived["fragmentation_index"],
        "agitation_score": derived["agitation_score"],
        "social_ratio": derived["social_ratio"],
        "social_opens_per_hour": derived["social_opens_per_hour"],
        "social_session_avg": derived["social_session_avg"],
        "social_session_var": derived["social_session_var"],
        "late_night_social_ratio": derived["late_night_social_ratio"],
        "social_inter_open_gap_min": derived["social_inter_open_gap_min"],
        # Temporal defaults for instant real-time inference
        "prev_label_lag1": 0,
        "roll_3d_screen_time_minutes": round(screen_time_minutes, 2),
        "roll_7d_screen_time_minutes": round(screen_time_minutes, 2),
        "roll_3d_social_ratio": derived["social_ratio"],
        "roll_7d_social_ratio": derived["social_ratio"],
        "roll_3d_app_switch_count": float(app_switch_count),
        "roll_7d_app_switch_count": float(app_switch_count),
        "roll_3d_scroll_speed": round(scroll_speed, 2),
        "roll_7d_scroll_speed": round(scroll_speed, 2),
        "roll_3d_typing_speed_wpm": round(typing_speed_wpm, 2),
        "roll_7d_typing_speed_wpm": round(typing_speed_wpm, 2),
        "dod_pct_screen_time_minutes": 0.0,
        "dod_pct_app_switch_count": 0.0,
        "dod_pct_scroll_speed": 0.0,
        "dod_pct_typing_speed_wpm": 0.0
    }

    df_input = pd.DataFrame([feature_dict], columns=FEATURE_COLUMNS)
    return df_input


def read_latest_user_telemetry(data_path: str, user_id: str) -> dict:
    if not os.path.exists(data_path):
        return {}

    records = []
    try:
        import csv
        with open(data_path, "r", encoding="utf-8") as f:
            reader = csv.reader(f)
            header = next(reader, None)
            if not header:
                return {}
            for row in reader:
                if not row or len(row) < 2:
                    continue
                row_dict = {}
                for idx, col in enumerate(header):
                    if idx < len(row):
                        row_dict[col] = row[idx]
                if len(header) >= 8 and len(row) >= 10:
                    row_dict["social_app_minutes"] = row[8]
                    row_dict["productive_app_minutes"] = row[9]
                if row_dict.get("user_id") == user_id:
                    records.append(row_dict)
    except Exception as e:
        print(f"[ML] CSV read warning: {e}")
        return {}

    if not records:
        return {}

    latest = records[-1]
    return {
        "screen_time_minutes": float(latest.get("screen_time_minutes", latest.get("screen_time", 180.0))),
        "unlock_count": int(latest.get("unlock_count", 45)),
        "app_switch_count": int(latest.get("app_switch_count", latest.get("app_switches", 50))),
        "typing_speed_wpm": float(latest.get("typing_speed_wpm", latest.get("typing_speed", 50.0))),
        "scroll_speed": float(latest.get("scroll_speed", 250.0)),
        "night_usage_minutes": float(latest.get("night_usage_minutes", latest.get("night_usage", 15.0))),
        "social_app_minutes": float(latest["social_app_minutes"]) if "social_app_minutes" in latest and latest["social_app_minutes"] != "" else None,
        "productive_app_minutes": float(latest["productive_app_minutes"]) if "productive_app_minutes" in latest and latest["productive_app_minutes"] != "" else None
    }


@router.get("/prediction")
async def get_prediction(user_id: str, test: bool = Query(False)):
    response = {
        "user_id": user_id,
        "date_evaluated": datetime.now().isoformat(),
        "stress_level": "Unknown",
        "real_time_feedback": "Analyzing your behavioral metrics..."
    }

    if models_dict is None or preprocessor is None:
        response["stress_level"] = "AI Engine Offline"
        response["real_time_feedback"] = "Engine artifacts are initializing."
        return response

    try:
        data_path = os.path.join(BASE_DIR, "data", "user_behavior.csv")
        raw_telemetry = read_latest_user_telemetry(data_path, user_id)

        if test:
            raw_telemetry = {
                "screen_time_minutes": 540.0,
                "unlock_count": 190,
                "app_switch_count": 330,
                "typing_speed_wpm": 55.0,
                "scroll_speed": 650.0,
                "night_usage_minutes": 180.0,
                "social_app_minutes": 380.0,
                "productive_app_minutes": 30.0
            }

        df_input = build_inference_features(raw_telemetry)
        X_scaled = preprocessor.transform(df_input)

        label_scores = {}
        label_levels = {}

        meta_thresholds = meta.get("thresholds", {}) if meta else {}

        for label in LABELS:
            reg = models_dict[label]
            if hasattr(reg, "predict_proba"):
                probs = reg.predict_proba(X_scaled)[0]
                pred_score = float(probs[2]) if len(probs) > 2 else float(probs[-1])
            else:
                pred_score = float(np.clip(reg.predict(X_scaled)[0], 0.0, 1.0))
            label_scores[label] = pred_score

            th = meta_thresholds.get(label, {"p33": 0.33, "p66": 0.66})
            p33 = th.get("p33", 0.33)
            p66 = th.get("p66", 0.66)

            if pred_score >= p66:
                lvl = "High"
            elif pred_score >= p33:
                lvl = "Medium"
            else:
                lvl = "Low"
            label_levels[label] = lvl

        # Select primary state (highest risk score)
        primary_label = max(label_scores, key=label_scores.get)
        primary_score = label_scores[primary_label]
        primary_level = label_levels[primary_label]

        if primary_level == "Low" and max(label_scores.values()) < 0.20:
            final_display_state = "Balanced"
        else:
            final_display_state = primary_label

        feedback_messages = []
        if label_levels["Stress"] in ["Medium", "High"]:
            feedback_messages.append("Elevated app switching and rapid scrolling detected. Try focusing on one task.")
        if label_levels["Anxiety"] in ["Medium", "High"]:
            feedback_messages.append("Frequent social unlocks and shrinking open gaps signal heightened distraction.")
        if label_levels["Burnout"] in ["Medium", "High"]:
            feedback_messages.append("High late-night phone usage detected. Ensure proper rest and sleep hygiene.")
        if label_levels["Addiction"] in ["Medium", "High"]:
            feedback_messages.append("Social media usage accounts for a large portion of your screen time. Consider setting daily app limits.")

        if not feedback_messages:
            feedback = "Your daily phone usage shows healthy, balanced digital habits. Keep it up!"
        else:
            feedback = " ".join(feedback_messages)

        response["stress_level"] = label_levels["Stress"]
        response["primary_behavioral_state"] = final_display_state
        response["confidence_score"] = round(primary_score, 4)
        response["real_time_feedback"] = feedback

        response["anxiety_detected"] = bool(label_levels["Anxiety"] in ["Medium", "High"])
        response["burnout_detected"] = bool(label_levels["Burnout"] in ["Medium", "High"])
        response["addiction_detected"] = bool(label_levels["Addiction"] in ["Medium", "High"])

        response["multi_label_states"] = {
            label: {
                "score": round(label_scores[label], 4),
                "level": label_levels[label]
            }
            for label in LABELS
        }

        # Invoke SpikeDetectionEngine in live prediction request path
        spike_engine.check(
            user_id=user_id,
            label_scores=label_scores,
            label_levels=label_levels,
            telemetry=raw_telemetry,
            now_dt=datetime.now()
        )

        # Evaluate Notification Policy Engine (8-point anti-fatigue policy)
        notif_decision = notification_engine.evaluate(
            user_id=user_id,
            label_levels=label_levels,
            label_scores=label_scores,
            telemetry=raw_telemetry,
            now_dt=datetime.now()
        )

        response["should_notify_push"] = notif_decision["should_notify_push"]
        response["notification_title"] = notif_decision["notification_title"]
        response["notification_body"] = notif_decision["notification_body"]
        response["notification_type"] = notif_decision["notification_type"]
        response["suppression_reason"] = notif_decision["suppression_reason"]
        response["notification_decision"] = notif_decision

        print(f"[ML PREDICTION] User: {user_id} -> Primary: {final_display_state} (Level: {primary_level}, Score: {primary_score:.4f}) | Push: {notif_decision['should_notify_push']} ({notif_decision.get('suppression_reason') or notif_decision.get('notification_type')})")
        return response

    except Exception as e:
        print(f"[ML PREDICTION ERROR] {type(e).__name__}: {e}")
        raise HTTPException(status_code=500, detail=f"Inference error: {str(e)}")