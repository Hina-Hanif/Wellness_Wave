import os
import joblib
import pandas as pd
from fastapi import APIRouter, HTTPException, Query
from datetime import datetime

router = APIRouter()

BASE_DIR = os.path.dirname(os.path.dirname(__file__))
MODEL_PATH = os.path.join(BASE_DIR, "wellbeing_model.pkl")
LE_PATH = os.path.join(BASE_DIR, "label_encoder.pkl")

# Load new model and label encoder
print(f"Loading Intelligence Engine from: {MODEL_PATH}")
try:
    if os.path.exists(MODEL_PATH) and os.path.exists(LE_PATH):
        model = joblib.load(MODEL_PATH)
        le = joblib.load(LE_PATH)
        print("Intelligence Engine and Label Encoder loaded successfully.")
    else:
        model = None
        le = None
        print(f"MODEL OR ENCODER NOT FOUND: {MODEL_PATH}, {LE_PATH}")
except Exception as e:
    print(f"MODEL LOAD ERROR: {type(e).__name__} - {e}")
    model = None
    le = None

# Notification State Tracking
user_states = {}

def check_and_send_notification(user_id, state):
    global user_states
    now = datetime.now()
    
    if user_id not in user_states:
        user_states[user_id] = {"state": state, "start_time": now, "last_notified": None}
        return
        
    current_record = user_states[user_id]
    
    if current_record["state"] != state:
        # State changed, reset timer
        user_states[user_id] = {"state": state, "start_time": now, "last_notified": None}
        return
        
    # Same state continues
    duration_hours = (now - current_record["start_time"]).total_seconds() / 3600.0
    
    # Check if we should notify
    should_notify = False
    if state in ["Stress", "Addiction"] and duration_hours >= 1.0:
        should_notify = True
    elif state == "Burnout" and duration_hours >= 5.0:
        should_notify = True
        
    if should_notify:
        # Avoid repeated spam (notify at most once every 1 hour for the same ongoing state)
        last_notified = current_record["last_notified"]
        if last_notified is None or (now - last_notified).total_seconds() / 3600.0 >= 1.0:
            print(f"NOTIFICATION TRIGGERED: User {user_id} has been in state {state} for {duration_hours:.1f} hours.")
            current_record["last_notified"] = now

@router.get("/prediction")
async def get_prediction(user_id: str, test: bool = Query(False)):
    response = {
        "user_id": user_id,
        "date_evaluated": datetime.now().isoformat(),
        "stress_level": "Unknown",
        "real_time_feedback": "Analyzing your behavior..."
    }

    if model is None or le is None:
        response["stress_level"] = "AI Engine Offline"
        response["real_time_feedback"] = "Engine is initializing."
        return response

    try:
        # 1. Fetch latest user data from CSV
        data_path = os.path.join(BASE_DIR, "data", "user_behavior.csv")
        
        features_list = [
            'screen_time', 'app_switches', 'scroll_speed', 'typing_speed', 
            'unlock_count', 'night_usage', 'night_ratio', 'unlock_intensity', 
            'fragmentation_index', 'agitation_score'
        ]
        input_features = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0] # default empty
        
        screen_time = 0.0
        app_switches = 0.0
        scroll_speed = 0.0
        typing_speed = 0.0
        unlock_count = 0.0
        night_usage = 0.0

        if os.path.exists(data_path):
            df = pd.read_csv(data_path)
            user_df = df[df['user_id'] == user_id]
            if not user_df.empty:
                latest_row = user_df.iloc[-1]
                screen_time = float(latest_row.get('screen_time', 0))
                app_switches = float(latest_row.get('app_switches', 0))
                scroll_speed = float(latest_row.get('scroll_speed', 0))
                typing_speed = float(latest_row.get('typing_speed', 0))
                unlock_count = float(latest_row.get('unlock_count', 0))
                night_usage = float(latest_row.get('night_usage', 0))
        
        # 2. Test override mode
        if test:
            # Override with extreme values as requested
            screen_time = 500.0
            app_switches = 120.0
            scroll_speed = 900.0
            typing_speed = 10.0
            unlock_count = 150.0
            night_usage = 200.0
            
        # Feature Engineering to match train_model.py exactly
        night_ratio = round(night_usage / (screen_time + 1), 3)
        unlock_intensity = round(unlock_count / (screen_time / 60 + 0.1), 2)
        fragmentation_index = round(app_switches / (screen_time / 60 + 0.1), 2)
        agitation_score = round((scroll_speed / 1500 * 0.6) + (typing_speed / 15 * 0.4), 3)

        input_features = [
            screen_time, app_switches, scroll_speed, typing_speed, 
            unlock_count, night_usage, night_ratio, unlock_intensity, 
            fragmentation_index, agitation_score
        ]
            
        print("FEATURES VECTOR:", input_features)
        print("MODEL FILE USED:", MODEL_PATH)
        
        # Build feature vector dynamically
        df_input = pd.DataFrame([input_features], columns=features_list)
        
        # 3. Model Prediction
        prediction_enc = model.predict(df_input)[0]
        prediction_label = le.inverse_transform([prediction_enc])[0]
        
        proba = model.predict_proba(df_input)[0]
        conf = max(proba)
        
        prediction = str(prediction_label)
        print("PREDICTION RESULT:", prediction)
        print("CONFIDENCE SCORE:", round(conf, 4))
        
        # 4. Check and Send Notifications
        check_and_send_notification(user_id, prediction)
        
        # Feedback mapping
        feedback_map = {
            "Balanced": "Your patterns look balanced. Keep up the good habits!",
            "Stress": "Stress signals detected in rapid scrolling/app switching. Take a deep breath.",
            "Burnout": "High usage + low responsiveness suggests burnout. Consider a digital sunset.",
            "Addiction": "Intense usage patterns detected. Screen time is extremely high."
        }
        
        response["stress_level"] = prediction
        response["real_time_feedback"] = feedback_map.get(prediction, "Keep monitoring your behavior!")
        
        # Extra fields to prevent breaking the UI
        response["confidence_score"] = float(conf)
        response["anxiety_detected"] = False
        response["burnout_detected"] = bool(prediction == "Burnout")
        response["addiction_detected"] = bool(prediction == "Addiction")
        
        return response

    except Exception as e:
        print("PREDICTION ERROR:", e)
        raise HTTPException(status_code=500, detail=str(e))