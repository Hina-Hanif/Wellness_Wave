from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import Optional
import firebase_admin
from firebase_admin import credentials, firestore
import os
from datetime import datetime
import pickle
import pandas as pd

# Initialize FastAPI
app = FastAPI(title="Wellness Wave API")

# Setup Firebase (Placeholder for actual key JSON)
# For local dev, we assume serviceAccountKey.json is present
try:
    if not firebase_admin._apps:
        cred = credentials.Certificate("serviceAccountKey.json")
        firebase_admin.initialize_app(cred)
    db = firestore.client()
except Exception as e:
    print(f"Firebase initialization failed: {e}. Running in mock mode.")
    db = None

from api.prediction import router as prediction_router
app.include_router(prediction_router)

class UsageMetrics(BaseModel):
    user_id: str
    date: str
    screen_time: int
    unlock_count: int
    social_time: int
    productivity_time: int
    night_usage: int
    night_ratio: float
    session_count: int
    scrolling_speed_avg: float
    scroll_erraticness: float
    typing_cps: float
    typing_hesitation_ms: int
    backspace_rate: float
    notification_response_sec: float
    app_switch_count_per_hour: int
    usage_consistency_shift: float
    typing_pauses_count: int
    max_typing_pause_ms: int
    mood_score: int

@app.get("/health")
def health_check():
    return {"status": "active", "message": "Wellness Wave API is running smoothly."}

@app.post("/daily-data")
async def submit_daily_data(metrics: UsageMetrics):
    if not db:
        return {"status": "mock_success", "message": "Firebase not connected, data not stored."}
    
    try:
        doc_ref = db.collection("users").document(metrics.user_id).collection("daily_metrics").document(metrics.date)
        doc_ref.set(metrics.dict())
        return {"status": "success", "message": "Daily metrics stored securely."}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/history")
async def get_history(user_id: str):
    # This will be replaced by actual historical queries from Firebase
    import random
    from datetime import timedelta
    
    today = datetime.now()
    history = []
    
    # Generate 7 days of mock history
    for i in range(6, -1, -1):
        past_date = today - timedelta(days=i)
        
        # Adding slight randomization for a realistic looking graph
        stress_score = max(0.2, min(0.9, 0.4 + random.uniform(-0.15, 0.3)))
        screen_time_hours = max(2.0, min(8.0, 4.5 + random.uniform(-1.0, 2.5)))
        
        history.append({
            "date": past_date.strftime("%Y-%m-%d"),
            "stress_score": round(stress_score, 2),
            "screen_time_hours": round(screen_time_hours, 1)
        })
        
    return {
        "user_id": user_id,
        "history": history
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
