"""
train_model.py
==============
Digital Wellbeing Classifier
Predicts: Balanced | Stress | Addiction | Burnout

Features used:
  - screen_time      (minutes/day)
  - app_switches     (focus fragmentation proxy)
  - scroll_speed     (pixels/second)
  - typing_speed     (characters/second)
  - unlock_count     (times/day)
  - night_usage      (minutes after 10 PM)
"""

import os
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import train_test_split, StratifiedKFold, cross_val_score
from sklearn.preprocessing import LabelEncoder
from sklearn.metrics import classification_report, confusion_matrix
import joblib
import warnings
warnings.filterwarnings("ignore")


# ─────────────────────────────────────────────
# 1. LABELING ENGINE  (rule-based ground truth)
# ─────────────────────────────────────────────

def compute_subscores(row):
    """
    Returns a dict of independent sub-scores for each symptom dimension.
    Each dimension maps to a distinct psychological construct.
    """
    screen_time  = row["screen_time"]
    app_switches = row["app_switches"]
    scroll_speed = row["scroll_speed"]
    typing_speed = row["typing_speed"]
    unlock_count = row["unlock_count"]
    night_usage  = row["night_usage"]

    # ── COMPULSION score (Addiction signal) ──────────────────────
    # High unlock count + high app switching = compulsive checking loop
    compulsion = 0
    if unlock_count > 120:   compulsion += 4
    elif unlock_count > 80:  compulsion += 3
    elif unlock_count > 50:  compulsion += 2
    elif unlock_count > 25:  compulsion += 1

    if app_switches > 120:   compulsion += 3
    elif app_switches > 80:  compulsion += 2
    elif app_switches > 45:  compulsion += 1

    # ── OVERLOAD score (Burnout signal) ──────────────────────────
    # Sustained high screen time + night usage = cognitive overload / no recovery
    overload = 0
    if screen_time > 420:    overload += 4   # 7+ hrs
    elif screen_time > 360:  overload += 3   # 6+ hrs
    elif screen_time > 240:  overload += 2   # 4+ hrs
    elif screen_time > 120:  overload += 1

    if night_usage > 150:    overload += 3   # 2.5+ hrs late night
    elif night_usage > 90:   overload += 2
    elif night_usage > 45:   overload += 1

    # ── AGITATION score (Stress / Anxiety signal) ─────────────────
    # Fast scrolling + fast typing = nervous, hurried interaction style
    agitation = 0
    if scroll_speed > 1000:  agitation += 3
    elif scroll_speed > 700: agitation += 2
    elif scroll_speed > 400: agitation += 1

    if typing_speed > 10:    agitation += 2
    elif typing_speed > 7:   agitation += 1

    # night usage also raises anxiety (disrupted sleep → anxious state)
    if night_usage > 90:     agitation += 2
    elif night_usage > 45:   agitation += 1

    return compulsion, overload, agitation


def assign_label(row):
    """
    Maps subscores → label using a priority hierarchy:

    Priority order (most severe wins when criteria are met):
      1. Burnout   — extreme overload + at least some compulsion
      2. Addiction — compulsion-dominant pattern
      3. Stress    — agitation-dominant, moderate usage
      4. Balanced  — none of the above

    Rationale:
      • Burnout requires BOTH sustained usage (overload) AND compulsive
        engagement — it is the exhaustion end-state of addiction.
      • Addiction is flagged when compulsive checking is dominant even
        if total screen time is moderate (e.g., 80 unlocks, low scroll).
      • Stress/Anxiety is flagged by agitated interaction style with
        moderate-to-high total usage.
      • Balanced = low scores across all dimensions.
    """
    compulsion, overload, agitation = compute_subscores(row)

    # ── Burnout: exhaustion from prolonged digital overload ──────
    if overload >= 5 and compulsion >= 3:
        return "Burnout"

    # ── Addiction: compulsive engagement pattern ─────────────────
    if compulsion >= 5:
        return "Addiction"

    # Addiction also when compulsion moderate but screen time very high
    if compulsion >= 3 and overload >= 4:
        return "Addiction"

    # ── Stress / Anxiety: agitated, hurried usage ────────────────
    if agitation >= 4:
        return "Stress"

    # Stress also when agitation moderate with significant night usage
    if agitation >= 2 and overload >= 3:
        return "Stress"

    # ── Balanced ─────────────────────────────────────────────────
    return "Balanced"


# ─────────────────────────────────────────────
# 2. DATA LOADING / GENERATION
# ─────────────────────────────────────────────

CSV_PATH = "digital_behavior_data.csv"

def load_or_generate_data(path=CSV_PATH, n_samples=1200):
    """Load CSV if present, else generate synthetic data for demo."""
    if os.path.exists(path):
        print(f"[INFO] Loading data from '{path}' ...")
        df = pd.read_csv(path)
        required = ["screen_time","app_switches","scroll_speed",
                    "typing_speed","unlock_count","night_usage"]
        missing = [c for c in required if c not in df.columns]
        if missing:
            raise ValueError(f"CSV missing columns: {missing}")
    else:
        print(f"[INFO] '{path}' not found — generating {n_samples} synthetic samples for demo.")
        rng = np.random.default_rng(42)

        # Simulate realistic, skewed distributions
        df = pd.DataFrame({
            "screen_time":  rng.gamma(shape=3, scale=80, size=n_samples).clip(30, 600),
            "app_switches": rng.gamma(shape=2, scale=40, size=n_samples).clip(5, 200),
            "scroll_speed": rng.gamma(shape=2, scale=300, size=n_samples).clip(50, 1500),
            "typing_speed": rng.gamma(shape=2, scale=4,   size=n_samples).clip(1, 15),
            "unlock_count": rng.gamma(shape=2, scale=35,  size=n_samples).clip(5, 200),
            "night_usage":  rng.gamma(shape=1.5, scale=50, size=n_samples).clip(0, 300),
        })
        df = df.round(1)

    return df


# ─────────────────────────────────────────────
# 3. FEATURE ENGINEERING
# ─────────────────────────────────────────────

def add_features(df):
    """Derived features that help the model learn interaction patterns."""
    df = df.copy()

    # Ratio of night usage to total screen time (sleep disruption ratio)
    df["night_ratio"] = (df["night_usage"] / (df["screen_time"] + 1)).round(3)

    # Unlock frequency proxy (unlocks per hour of screen time)
    df["unlock_intensity"] = (df["unlock_count"] / (df["screen_time"] / 60 + 0.1)).round(2)

    # Focus fragmentation index: app switches per hour
    df["fragmentation_index"] = (df["app_switches"] / (df["screen_time"] / 60 + 0.1)).round(2)

    # Agitation composite: scroll + typing speed normalized
    df["agitation_score"] = (
        df["scroll_speed"] / 1500 * 0.6 +
        df["typing_speed"] / 15   * 0.4
    ).round(3)

    return df


# ─────────────────────────────────────────────
# 4. MAIN PIPELINE
# ─────────────────────────────────────────────

def main():
    print("\n" + "="*60)
    print("   Digital Wellbeing Classifier — train_model.py")
    print("="*60 + "\n")

    # ── Load data ────────────────────────────────────────────────
    df = load_or_generate_data()
    print(f"[INFO] Dataset shape: {df.shape}\n")

    # ── Apply labels ─────────────────────────────────────────────
    df["label"] = df.apply(assign_label, axis=1)

    print("[INFO] Label distribution:")
    dist = df["label"].value_counts()
    for label, count in dist.items():
        pct = count / len(df) * 100
        print(f"        {label:<12} {count:>5}  ({pct:.1f}%)")
    print()

    # ── Feature engineering ───────────────────────────────────────
    df = add_features(df)

    FEATURES = [
        "screen_time", "app_switches", "scroll_speed",
        "typing_speed", "unlock_count", "night_usage",
        "night_ratio", "unlock_intensity", "fragmentation_index",
        "agitation_score"
    ]
    X = df[FEATURES]
    y = df["label"]

    # ── Encode labels ─────────────────────────────────────────────
    le = LabelEncoder()
    y_enc = le.fit_transform(y)
    print(f"[INFO] Classes: {list(le.classes_)}\n")

    # ── Train/test split ──────────────────────────────────────────
    X_train, X_test, y_train, y_test = train_test_split(
        X, y_enc, test_size=0.2, random_state=42, stratify=y_enc
    )

    # ── Train model ───────────────────────────────────────────────
    print("[INFO] Training Random Forest ...")
    model = RandomForestClassifier(
        n_estimators=200,
        max_depth=10,
        min_samples_leaf=3,
        class_weight="balanced",   # handles imbalanced labels
        random_state=42,
        n_jobs=-1
    )
    model.fit(X_train, y_train)

    # ── Cross-validation ──────────────────────────────────────────
    cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=42)
    cv_scores = cross_val_score(model, X, y_enc, cv=cv, scoring="f1_weighted")
    print(f"[INFO] 5-Fold CV F1 (weighted): {cv_scores.mean():.3f} ± {cv_scores.std():.3f}\n")

    # ── Test evaluation ───────────────────────────────────────────
    y_pred = model.predict(X_test)
    print("[RESULTS] Classification Report:")
    print(classification_report(y_test, y_pred, target_names=le.classes_))

    print("[RESULTS] Confusion Matrix (rows=actual, cols=predicted):")
    cm = confusion_matrix(y_test, y_pred)
    cm_df = pd.DataFrame(cm, index=le.classes_, columns=le.classes_)
    print(cm_df.to_string())
    print()

    # ── Feature importance ────────────────────────────────────────
    print("[INFO] Feature Importances:")
    importances = pd.Series(model.feature_importances_, index=FEATURES)
    importances = importances.sort_values(ascending=False)
    for feat, imp in importances.items():
        bar = "#" * int(imp * 60)
        print(f"  {feat:<22} {imp:.4f}  {bar}")
    print()

    # ── Save artifacts ────────────────────────────────────────────
    joblib.dump(model, "wellbeing_model.pkl")
    joblib.dump(le,    "label_encoder.pkl")

    # Save labeled dataset
    df.to_csv("labeled_data.csv", index=False)

    print("[INFO] Saved: wellbeing_model.pkl")
    print("[INFO] Saved: label_encoder.pkl")
    print("[INFO] Saved: labeled_data.csv")

    # ── Quick inference demo ──────────────────────────────────────
    print("\n" + "-"*60)
    print("  Quick Inference Demo")
    print("-"*60)

    demo_cases = [
        {"name": "Relaxed user",
         "screen_time": 90,  "app_switches": 20, "scroll_speed": 250,
         "typing_speed": 4,  "unlock_count": 15, "night_usage": 10},

        {"name": "Stressed professional",
         "screen_time": 300, "app_switches": 85, "scroll_speed": 800,
         "typing_speed": 9,  "unlock_count": 60, "night_usage": 75},

        {"name": "Addicted scroller",
         "screen_time": 240, "app_switches": 130,"scroll_speed": 600,
         "typing_speed": 5,  "unlock_count": 140,"night_usage": 40},

        {"name": "Burnt-out worker",
         "screen_time": 480, "app_switches": 110,"scroll_speed": 950,
         "typing_speed": 11, "unlock_count": 95, "night_usage": 160},
    ]

    for case in demo_cases:
        name = case.pop("name")
        row = pd.Series(case)
        row_df = pd.DataFrame([case])
        row_df = add_features(row_df)
        pred_enc = model.predict(row_df[FEATURES])[0]
        pred_label = le.inverse_transform([pred_enc])[0]
        proba = model.predict_proba(row_df[FEATURES])[0]
        conf = max(proba) * 100

        # also show rule-based label for comparison
        rule_label = assign_label(row)

        print(f"\n  [USER] {name}")
        print(f"     Model → {pred_label:<12} ({conf:.1f}% confidence)")
        print(f"     Rules → {rule_label}")

    print("\n" + "="*60)
    print("  Training complete.")
    print("="*60 + "\n")


if __name__ == "__main__":
    main()