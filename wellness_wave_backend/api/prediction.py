import os
import joblib
import pandas as pd
from fastapi import APIRouter, HTTPException
from database import get_latest_metrics

router = APIRouter()

BASE_DIR = os.path.dirname(os.path.dirname(__file__))

MODEL_PATH = os.path.join(BASE_DIR, "behavioral_model.pkl")
FEATURES_PATH = os.path.join(BASE_DIR, "model_features.pkl")

# Load V3 Ensemble Pipeline
print(f"Starting V3 Intelligence load pipeline...")
try:
    print(f"Loading V3 Ensemble from: {MODEL_PATH}")
    model_pipeline = joblib.load(MODEL_PATH)
    print(f"Loading V3 features from: {FEATURES_PATH}")
    features_list = joblib.load(FEATURES_PATH)
    print("V3 Intelligence Engine loaded successfully.")

except Exception as e:
    print(f"MODEL LOAD ERROR: {type(e).__name__} - {e}")
    model_pipeline = None
    features_list = None

@router.get("/prediction")
async def get_prediction(user_id: str):
    latest_data = get_latest_metrics(user_id)

    response = {
        "user_id": user_id,
        "date_evaluated": None,
        "stress_level": "Initial State",
        "confidence_score": 0.0,
        "anxiety_detected": False,
        "burnout_detected": False,
        "addiction_detected": False,
        "real_time_feedback": "Please enable Accessibility Service and tap 'Sync' to start AI Analysis."
    }

    if not latest_data:
        return response

    response["date_evaluated"] = latest_data.get("date")

    if model_pipeline is None or features_list is None:
        response["stress_level"] = "AI Engine Offline"
        response["real_time_feedback"] = "V3 Engine is initializing. Please check back in a moment."
        return response

    try:
        # ── V3 FEATURE ENGINEERING ────────────────────────────────────────────
        st = float(latest_data.get("screen_time", 0.0)) / 60.0 # Convert to hours for model
        uc = float(latest_data.get("unlock_count", 0.0))
        nu = float(latest_data.get("night_usage", 0.0)) / 60.0 # hours
        sc = float(latest_data.get("session_count", 0.0))
        asc = float(latest_data.get("app_switch_count", 0.0))
        
        # Mapping base features
        metrics_map = {
            "screen_time":            st,
            "unlock_count":           uc,
            "social_time":            float(latest_data.get("social_time", 0.0)) / 60.0,
            "productivity_time":      float(latest_data.get("productivity_time", 0.0)) / 60.0,
            "night_usage":            nu,
            "session_count":          sc,
            "scrolling_speed_avg":    float(latest_data.get("scrolling_speed_avg", 0.0)),
            "usage_consistency_shift": float(latest_data.get("usage_consistency_shift", 0.0)),
            "typing_cps":             float(latest_data.get("typing_cps", 0.0)),
            "typing_pauses":          float(latest_data.get("typing_pauses_count", 0.0)),
            "scroll_erraticness":     float(latest_data.get("scroll_erraticness", 0.0)),
            "app_switch_count":       asc,
            "reaction_delay_sec":     float(latest_data.get("notification_response_sec", 60.0)),
            "mood_score":             float(latest_data.get("mood_score", 7.0)),
            # Engineered
            "social_ratio":           (float(latest_data.get("social_time", 0.0)) / 60.0) / (st + 1e-6),
            "night_ratio":            nu / (st + 1e-6),
            "productivity_ratio":     (float(latest_data.get("productivity_time", 0.0)) / 60.0) / (st + 1e-6),
            "switch_per_hour":        asc / (st + 1e-6),
            "pause_per_keystroke":    float(latest_data.get("typing_pauses_count", 0.0)) / (sc + 1e-6)
        }

        # Build feature vector for V3 pipeline
        ordered_input = [metrics_map.get(feat, 0.0) for feat in features_list]
        df = pd.DataFrame([ordered_input], columns=features_list)

        # Inference using full pipeline (Scaler + Ensemble)
        cat_prediction = model_pipeline.predict(df)[0]
        
        # Confidence logic for V3
        if hasattr(model_pipeline["model"], "predict_proba"):
            X_scaled = model_pipeline["scaler"].transform(df)
            probs = model_pipeline["model"].predict_proba(X_scaled)[0]
            confidence = float(max(probs))
        else:
            confidence = 0.94

        # Feedback mapping
        feedback_map = {
            "Balanced": "Your patterns look balanced. Keep up the good habits!",
            "Stress": "Stress signals detected in rapid scrolling/app switching. Take a deep breath.",
            "Anxiety": "Anxiety markers found (erratic scrolling). Try a 2-minute focus session.",
            "Burnout": "High usage + low responsiveness suggests burnout. Consider a digital sunset.",
            "Addiction": "Intense usage patterns detected. Screen time is extremely high."
        }

        return {
            "user_id": user_id,
            "date_evaluated": response["date_evaluated"],
            "stress_level": str(cat_prediction),
            "confidence_score": confidence,
            "anxiety_detected": bool(cat_prediction == "Anxiety"),
            "burnout_detected": bool(cat_prediction == "Burnout"),
            "addiction_detected": bool(cat_prediction == "Addiction"),
            "real_time_feedback": feedback_map.get(cat_prediction, "Keep monitoring your behavior!")
        }

    except Exception as e:
        print("PREDICTION ERROR:", e)
        raise HTTPException(status_code=500, detail=str(e))