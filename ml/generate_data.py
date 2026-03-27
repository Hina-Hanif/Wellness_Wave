import pandas as pd
import numpy as np
import random
from datetime import datetime, timedelta

# Set seed for reproducibility
np.random.seed(42)
random.seed(42)

def generate_synthetic_data(num_samples=1000):
    data = []
    
    categories = ["Balanced", "Stressed", "Anxious", "Burnout", "Addicted"]
    
    for _ in range(num_samples):
        # Base behaviors
        screen_time = np.random.uniform(1.0, 12.0)  # hours
        unlock_count = np.random.randint(10, 150)
        social_time = np.random.uniform(0.5, 6.0)
        productivity_time = np.random.uniform(0.0, 8.0)
        night_usage = np.random.uniform(0.0, 5.0)  # usage between 11 PM and 5 AM
        session_count = np.random.randint(20, 200)
        scrolling_speed_avg = np.random.uniform(50, 500)  # arbitrary speed units
        usage_consistency_shift = np.random.uniform(-0.5, 0.5)  # % change from baseline
        mood_score = np.random.randint(1, 11)
        
        # Determine category based on rules (to give the model something to learn)
        if night_usage > 3.0 and screen_time > 8.0:
            category = "Burnout"
            mood_score = random.randint(1, 3)
        elif unlock_count > 100 and session_count > 150:
            category = "Addicted"
            mood_score = random.randint(2, 5)
        elif scrolling_speed_avg > 350 and usage_consistency_shift > 0.2:
            category = "Stressed"
            mood_score = random.randint(3, 6)
        elif unlock_count > 80 and social_time > 4.0 and night_usage > 1.5:
            category = "Anxious"
            mood_score = random.randint(3, 5)
        else:
            category = "Balanced"
            mood_score = random.randint(7, 10)
            
        data.append({
            "screen_time": screen_time,
            "unlock_count": unlock_count,
            "social_time": social_time,
            "productivity_time": productivity_time,
            "night_usage": night_usage,
            "session_count": session_count,
            "scrolling_speed_avg": scrolling_speed_avg,
            "usage_consistency_shift": usage_consistency_shift,
            "mood_score": mood_score,
            "category": category
        })
        
    return pd.DataFrame(data)

if __name__ == "__main__":
    print("Generating synthetic behavioral dataset...")
    df = generate_synthetic_data(1500)
    df.to_csv("ml/behavioral_dataset.csv", index=False)
    print(f"Dataset saved to ml/behavioral_dataset.csv with {len(df)} rows.")
    print("Class distribution:")
    print(df['category'].value_counts())
