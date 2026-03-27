import pandas as pd
import numpy as np
import random
import joblib
import os
from sklearn.ensemble import GradientBoostingClassifier, RandomForestClassifier, VotingClassifier
from sklearn.preprocessing import StandardScaler
from sklearn.pipeline import Pipeline
from sklearn.model_selection import train_test_split, StratifiedKFold, cross_val_score
from sklearn.metrics import classification_report, accuracy_score, confusion_matrix

np.random.seed(42)
random.seed(42)

# ─────────────────────────────────────────────────────────────────────────────
# 1.  ENHANCED SYNTHETIC DATA with stronger, overlapping signals + noise
# ─────────────────────────────────────────────────────────────────────────────
def generate_synthetic_data(num_samples=5000):
    data = []
    categories = ["Balanced", "Stress", "Anxiety", "Burnout", "Addiction"]
    weights   = [0.30,       0.20,     0.20,      0.15,       0.15]

    for _ in range(num_samples):
        cat = random.choices(categories, weights=weights)[0]

        if cat == "Balanced":
            screen_time           = np.random.normal(4.0,  1.0)
            unlock_count          = int(np.random.normal(40,   10))
            social_time           = np.random.normal(1.5,  0.5)
            productivity_time     = np.random.normal(4.0,  1.0)
            night_usage           = np.random.normal(0.3,  0.2)
            session_count         = int(np.random.normal(50,   15))
            scrolling_speed_avg   = np.random.normal(150,  40)
            typing_cps            = np.random.normal(4.5,  0.8)
            typing_pauses         = int(np.random.normal(5,    3))
            scroll_erraticness    = np.random.normal(0.4,  0.2)
            app_switch_count      = int(np.random.normal(20,   8))
            reaction_delay_sec    = np.random.normal(60,   20)
            mood_score            = int(np.random.normal(8,    1))

        elif cat == "Stress":
            screen_time           = np.random.normal(7.0,  1.5)
            unlock_count          = int(np.random.normal(90,   20))
            social_time           = np.random.normal(2.0,  0.8)
            productivity_time     = np.random.normal(5.0,  1.5)
            night_usage           = np.random.normal(1.5,  0.5)
            session_count         = int(np.random.normal(120,  30))
            scrolling_speed_avg   = np.random.normal(380,  50)
            typing_cps            = np.random.normal(7.0,  1.5)
            typing_pauses         = int(np.random.normal(10,   5))
            scroll_erraticness    = np.random.normal(1.8,  0.4)
            app_switch_count      = int(np.random.normal(55,   15))
            reaction_delay_sec    = np.random.normal(4.0,  2.0)
            mood_score            = int(np.random.normal(4,    1))

        elif cat == "Anxiety":
            screen_time           = np.random.normal(6.0,  1.5)
            unlock_count          = int(np.random.normal(110,  25))
            social_time           = np.random.normal(4.5,  1.0)
            productivity_time     = np.random.normal(2.0,  1.0)
            night_usage           = np.random.normal(2.0,  0.8)
            session_count         = int(np.random.normal(130,  30))
            scrolling_speed_avg   = np.random.normal(300,  60)
            typing_cps            = np.random.normal(9.0,  1.5)
            typing_pauses         = int(np.random.normal(20,   6))
            scroll_erraticness    = np.random.normal(1.3,  0.4)
            app_switch_count      = int(np.random.normal(65,   15))
            reaction_delay_sec    = np.random.normal(3.0,  1.5)
            mood_score            = int(np.random.normal(4,    1))

        elif cat == "Burnout":
            screen_time           = np.random.normal(9.5,  1.0)
            unlock_count          = int(np.random.normal(70,   20))
            social_time           = np.random.normal(1.0,  0.5)
            productivity_time     = np.random.normal(1.0,  0.8)
            night_usage           = np.random.normal(3.5,  0.8)
            session_count         = int(np.random.normal(80,   20))
            scrolling_speed_avg   = np.random.normal(200,  60)
            typing_cps            = np.random.normal(2.5,  0.8)
            typing_pauses         = int(np.random.normal(35,   8))
            scroll_erraticness    = np.random.normal(0.7,  0.3)
            app_switch_count      = int(np.random.normal(25,   10))
            reaction_delay_sec    = np.random.normal(90,   20)
            mood_score            = int(np.random.normal(2,    1))

        else:  # Addiction
            screen_time           = np.random.normal(10.5, 1.0)
            unlock_count          = int(np.random.normal(130,  25))
            social_time           = np.random.normal(5.5,  1.0)
            productivity_time     = np.random.normal(0.5,  0.4)
            night_usage           = np.random.normal(2.5,  0.8)
            session_count         = int(np.random.normal(170,  30))
            scrolling_speed_avg   = np.random.normal(250,  60)
            typing_cps            = np.random.normal(5.0,  1.0)
            typing_pauses         = int(np.random.normal(8,    4))
            scroll_erraticness    = np.random.normal(0.9,  0.3)
            app_switch_count      = int(np.random.normal(95,   20))
            reaction_delay_sec    = np.random.normal(2.5,  1.0)
            mood_score            = int(np.random.normal(5,    2))

        # ── Clip to realistic ranges ──────────────────────────────────────────
        screen_time           = float(np.clip(screen_time,         0.5,  16.0))
        unlock_count          = int(np.clip(unlock_count,          0,    200))
        social_time           = float(np.clip(social_time,         0.0,  12.0))
        productivity_time     = float(np.clip(productivity_time,   0.0,  12.0))
        night_usage           = float(np.clip(night_usage,         0.0,   6.0))
        session_count         = int(np.clip(session_count,         5,    250))
        scrolling_speed_avg   = float(np.clip(scrolling_speed_avg, 20,   600))
        typing_cps            = float(np.clip(typing_cps,          0.5,  15.0))
        typing_pauses         = int(np.clip(typing_pauses,         0,    60))
        scroll_erraticness    = float(np.clip(scroll_erraticness,  0.0,   4.0))
        app_switch_count      = int(np.clip(app_switch_count,      0,    150))
        reaction_delay_sec    = float(np.clip(reaction_delay_sec,  0.5, 180.0))
        mood_score            = int(np.clip(mood_score,            1,    10))

        # ── Engineered features (key improvement!) ────────────────────────────
        social_ratio          = social_time / (screen_time + 1e-6)
        night_ratio           = night_usage / (screen_time + 1e-6)
        productivity_ratio    = productivity_time / (screen_time + 1e-6)
        switch_per_hour       = app_switch_count / (screen_time + 1e-6)
        pause_per_keystroke   = typing_pauses / (session_count + 1e-6)
        usage_consistency_shift = np.random.uniform(-0.3, 0.3)

        data.append({
            "screen_time":            screen_time,
            "unlock_count":           unlock_count,
            "social_time":            social_time,
            "productivity_time":      productivity_time,
            "night_usage":            night_usage,
            "session_count":          session_count,
            "scrolling_speed_avg":    scrolling_speed_avg,
            "usage_consistency_shift": usage_consistency_shift,
            "typing_cps":             typing_cps,
            "typing_pauses":          typing_pauses,
            "scroll_erraticness":     scroll_erraticness,
            "app_switch_count":       app_switch_count,
            "reaction_delay_sec":     reaction_delay_sec,
            "mood_score":             mood_score,
            # Engineered
            "social_ratio":           social_ratio,
            "night_ratio":            night_ratio,
            "productivity_ratio":     productivity_ratio,
            "switch_per_hour":        switch_per_hour,
            "pause_per_keystroke":    pause_per_keystroke,
            "category":               cat
        })

    return pd.DataFrame(data)


# ─────────────────────────────────────────────────────────────────────────────
# 2.  ENSEMBLE MODEL TRAINING
# ─────────────────────────────────────────────────────────────────────────────
def train_and_save_model():
    print("=" * 60)
    print("  WELLNESS WAVE — AI MODEL UPGRADE V3")
    print("=" * 60)

    print("\n[1/4] Generating enhanced 5,000-sample dataset...")
    df = generate_synthetic_data(num_samples=5000)
    os.makedirs("ml", exist_ok=True)
    df.to_csv("ml/behavioral_dataset_v3.csv", index=False)
    print(f"      Dataset saved — {len(df)} rows, {len(df.columns)-1} features")

    print("\n[2/4] Building Ensemble Model (GBM + Random Forest)...")
    X = df.drop(columns=["category"])
    y = df["category"]

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, stratify=y, random_state=42
    )

    # Gradient Boosting — strong, sequential learner
    gbm = GradientBoostingClassifier(
        n_estimators=200,
        max_depth=5,
        learning_rate=0.08,
        subsample=0.85,
        min_samples_split=10,
        random_state=42
    )

    # Random Forest — diverse, parallel learner
    rf = RandomForestClassifier(
        n_estimators=200,
        max_depth=12,
        min_samples_leaf=3,
        random_state=42,
        n_jobs=-1
    )

    # Soft-voting ensemble: averages probabilities from both models
    ensemble = VotingClassifier(
        estimators=[("gbm", gbm), ("rf", rf)],
        voting="soft"
    )

    # Wrap in a StandardScaler pipeline (benefits GBM)
    pipeline = Pipeline([
        ("scaler", StandardScaler()),
        ("model",  ensemble)
    ])

    print("\n[3/4] Training... (this may take ~30 seconds)")
    pipeline.fit(X_train, y_train)

    y_pred = pipeline.predict(X_test)
    acc = accuracy_score(y_test, y_pred)

    print(f"\n      ✅  Test Accuracy : {acc * 100:.2f}%")
    print("\n      Classification Report:")
    print(classification_report(y_test, y_pred, target_names=sorted(y.unique())))

    print("\n[4/4] Saving upgraded model & feature list...")
    os.makedirs("wellness_wave_backend", exist_ok=True)
    joblib.dump(pipeline,             "wellness_wave_backend/behavioral_model.pkl")
    joblib.dump(X.columns.tolist(),   "wellness_wave_backend/model_features.pkl")

    print("\n  ✅  behavioral_model.pkl  →  wellness_wave_backend/")
    print("  ✅  model_features.pkl    →  wellness_wave_backend/")
    print("=" * 60)
    print("  AI UPGRADE COMPLETE — Ensemble GBM+RF is live!")
    print("=" * 60)


if __name__ == "__main__":
    train_and_save_model()
