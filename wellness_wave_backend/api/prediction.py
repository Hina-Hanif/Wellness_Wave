import os
import joblib
import pandas as pd
from fastapi import APIRouter, HTTPException, Query
from datetime import datetime

router = APIRouter()

BASE_DIR = os.path.dirname(os.path.dirname(__file__))
MODEL_PATH = os.path.join(BASE_DIR, "stress_model.pkl")

# Load new model
print(f"Loading Intelligence Engine from: {MODEL_PATH}")
try:
    if os.path.exists(MODEL_PATH):
        model = joblib.load(MODEL_PATH)
        print("Intelligence Engine loaded successfully.")
    else:
        model = None
        print(f"MODEL NOT FOUND: {MODEL_PATH}")
except Exception as e:
    print(f"MODEL LOAD ERROR: {type(e).__name__} - {e}")
    model = None

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

    if model is None:
        response["stress_level"] = "AI Engine Offline"
        response["real_time_feedback"] = "Engine is initializing."
        return response

    try:
        # 1. Fetch latest user data from CSV
        data_path = os.path.join(BASE_DIR, "data", "user_behavior.csv")
        
        features_list = ['screen_time', 'app_switches', 'scroll_speed', 'typing_speed', 'unlock_count', 'night_usage']
        input_features = [0, 0, 0, 0, 0, 0] # default empty
        
        if os.path.exists(data_path):
            df = pd.read_csv(data_path)
            user_df = df[df['user_id'] == user_id]
            if not user_df.empty:
                latest_row = user_df.iloc[-1]
                input_features = [
                    float(latest_row.get('screen_time', 0)),
                    float(latest_row.get('app_switches', 0)),
                    float(latest_row.get('scroll_speed', 0)),
                    float(latest_row.get('typing_speed', 0)),
                    float(latest_row.get('unlock_count', 0)),
                    float(latest_row.get('night_usage', 0))
                ]
        
        # 2. Test override mode
        if test:
            # Override with extreme values as requested
            input_features = [500, 120, 900, 10, 150, 200]
            
        print("FEATURES:", input_features)
        print("INPUT:", input_features)
        
        # Build feature vector dynamically
        df_input = pd.DataFrame([input_features], columns=features_list)
        
        # 3. Model Prediction
        prediction = model.predict(df_input)[0]
        print("PREDICTION:", prediction)
        
        # 4. Check and Send Notifications
        check_and_send_notification(user_id, prediction)
        
        # Feedback mapping
        feedback_map = {
            "Balanced": "Your patterns look balanced. Keep up the good habits!",
            "Stress": "Stress signals detected in rapid scrolling/app switching. Take a deep breath.",
            "Burnout": "High usage + low responsiveness suggests burnout. Consider a digital sunset.",
            "Addiction": "Intense usage patterns detected. Screen time is extremely high."
        }
        
        response["stress_level"] = str(prediction)
        response["real_time_feedback"] = feedback_map.get(str(prediction), "Keep monitoring your behavior!")
        
        # Extra fields to prevent breaking the UI
        response["confidence_score"] = 0.95
        response["anxiety_detected"] = False
        response["burnout_detected"] = bool(prediction == "Burnout")
        response["addiction_detected"] = bool(prediction == "Addiction")
        
        return response

    except Exception as e:
        print("PREDICTION ERROR:", e)
        raise HTTPException(status_code=500, detail=str(e))