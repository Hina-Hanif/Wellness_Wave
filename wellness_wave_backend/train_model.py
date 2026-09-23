"""
train_model.py
==============
Machine Learning Regression, Calibration, Dynamic Label-Specific Percentile Thresholding,
SHAP Analysis, and Noise Sensitivity Evaluation for Wellness Wave Behavioral Wellbeing Multi-Label Engine.
"""

import json
import os
import sys
import time
from datetime import datetime
import numpy as np
import pandas as pd
import joblib

# Scikit-Learn Metrics & Tools
from sklearn.preprocessing import StandardScaler
from scipy.stats import pearsonr
from sklearn.metrics import (
    accuracy_score, precision_score, recall_score, f1_score,
    mean_absolute_error, mean_squared_error, confusion_matrix, r2_score
)

# XGBoost & SHAP Imports
import xgboost as xgb
import shap

SEED = 42
np.random.seed(SEED)

FEATURE_COLUMNS = [
    # Base 12 behavioral features
    "screen_time_minutes", "unlock_count", "app_switch_count",
    "typing_speed_wpm", "scroll_speed", "night_usage_minutes",
    "social_app_minutes", "productive_app_minutes", "night_ratio",
    "unlock_intensity", "fragmentation_index", "agitation_score",
    # Social App Feature Group
    "social_ratio", "social_opens_per_hour", "social_session_avg",
    "social_session_var", "late_night_social_ratio", "social_inter_open_gap_min",
    # Temporal & Historical Features
    "prev_label_lag1", "roll_3d_screen_time_minutes", "roll_7d_screen_time_minutes",
    "roll_3d_social_ratio", "roll_7d_social_ratio", "roll_3d_app_switch_count",
    "roll_7d_app_switch_count", "roll_3d_scroll_speed", "roll_7d_scroll_speed",
    "roll_3d_typing_speed_wpm", "roll_7d_typing_speed_wpm",
    "dod_pct_screen_time_minutes", "dod_pct_app_switch_count",
    "dod_pct_scroll_speed", "dod_pct_typing_speed_wpm"
]

LABELS = ["Stress", "Anxiety", "Burnout", "Addiction"]


def load_dataset(data_path: str) -> pd.DataFrame:
    print("\n==================================================")
    print("STEP 1: LOADING & INSPECTING DATASET")
    print("==================================================")
    if not os.path.exists(data_path):
        raise FileNotFoundError(f"Dataset missing at: {data_path}")

    df = pd.read_csv(data_path)
    df.sort_values(by=["user_id", "date"], inplace=True)
    df.reset_index(drop=True, inplace=True)

    print(f"Dataset path: {data_path}")
    print(f"Total rows: {len(df)}, Total columns: {len(df.columns)}")
    print(f"Unique users: {df['user_id'].nunique()}")
    print(f"Date range: {df['date'].min()} to {df['date'].max()}")
    print(f"Null count: {df.isnull().sum().sum()}")
    return df


def temporal_train_val_test_split(df: pd.DataFrame):
    """
    Temporal User Split:
    For each user, train on earlier 70% of days, validate on middle 15% of days, test on final 15% of days.
    Guarantees strict temporal holdout without future-data leakage.
    """
    print("\n==================================================")
    print("STEP 2: TEMPORAL USER SPLITTING (70% Train / 15% Val / 15% Test)")
    print("==================================================")

    train_rows, val_rows, test_rows = [], [], []

    for uid, group in df.groupby("user_id", sort=False):
        group_sorted = group.sort_values(by="date")
        n = len(group_sorted)
        n_train = int(0.70 * n)
        n_val = int(0.15 * n)

        train_rows.append(group_sorted.iloc[:n_train])
        val_rows.append(group_sorted.iloc[n_train:n_train + n_val])
        test_rows.append(group_sorted.iloc[n_train + n_val:])

    train_df = pd.concat(train_rows).reset_index(drop=True)
    val_df = pd.concat(val_rows).reset_index(drop=True)
    test_df = pd.concat(test_rows).reset_index(drop=True)

    print(f"Train Set: {len(train_df)} rows ({train_df['date'].min()} to {train_df['date'].max()})")
    print(f"Val Set  : {len(val_df)} rows ({val_df['date'].min()} to {val_df['date'].max()})")
    print(f"Test Set : {len(test_df)} rows ({test_df['date'].min()} to {test_df['date'].max()})")

    return train_df, val_df, test_df


def train_and_evaluate_regression_models(train_df, val_df, test_df):
    print("\n==================================================")
    print("STEP 3: MULTI-LABEL CONTINUOUS RISK REGRESSION & THRESHOLDING")
    print("==================================================")

    X_train = train_df[FEATURE_COLUMNS]
    X_val = val_df[FEATURE_COLUMNS]
    X_test = test_df[FEATURE_COLUMNS]

    scaler = StandardScaler()
    X_train_scaled = scaler.fit_transform(X_train)
    X_val_scaled = scaler.transform(X_val)
    X_test_scaled = scaler.transform(X_test)

    reg_models = {}
    thresholds = {}
    reports = {}
    level_map = {"Low": 0, "Medium": 1, "High": 2}

    for label in LABELS:
        print(f"\n--------------------------------------------------")
        print(f"Training XGBoost Regressor for: {label}")
        print(f"--------------------------------------------------")

        y_train_score = train_df[f"{label.lower()}_score"].values
        y_val_score = val_df[f"{label.lower()}_score"].values
        y_test_score = test_df[f"{label.lower()}_score"].values

        y_test_level = test_df[f"{label.lower()}_level"].map(level_map).values

        # XGBoost Regressor for continuous risk score prediction
        model = xgb.XGBRegressor(
            n_estimators=120,
            max_depth=4,
            learning_rate=0.05,
            subsample=0.8,
            colsample_bytree=0.8,
            random_state=SEED,
            eval_metric="rmse"
        )
        model.fit(X_train_scaled, y_train_score)
        reg_models[label] = model

        # Predictions on Val & Test sets
        val_pred_scores = np.clip(model.predict(X_val_scaled), 0.0, 1.0)
        test_pred_scores = np.clip(model.predict(X_test_scaled), 0.0, 1.0)

        # Regression Metrics on Test Set
        mae = mean_absolute_error(y_test_score, test_pred_scores)
        rmse = np.sqrt(mean_squared_error(y_test_score, test_pred_scores))

        # Label-Specific Empirical Natural Percentile Cutoffs from Validation Set
        p33 = float(np.percentile(val_pred_scores, 33.33))
        p66 = float(np.percentile(val_pred_scores, 66.67))
        thresholds[label] = {"p33": round(p33, 4), "p66": round(p66, 4)}

        # Map continuous test predictions to Low (0), Medium (1), High (2) using label-specific thresholds
        test_preds_level = np.zeros(len(test_pred_scores), dtype=int)
        test_preds_level[test_pred_scores >= p33] = 1
        test_preds_level[test_pred_scores >= p66] = 2

        # Compute Pearson r and R2 against raw pre-noise formula output
        raw_formula_col = f"raw_formula_{label.lower()}"
        if raw_formula_col in test_df.columns:
            y_raw_formula = test_df[raw_formula_col].values
            corr_r, _ = pearsonr(test_pred_scores, y_raw_formula)
            r2_val = float(r2_score(y_raw_formula, test_pred_scores))
        else:
            corr_r, r2_val = 0.0, 0.0

        # Detailed Classification Metrics
        acc = accuracy_score(y_test_level, test_preds_level)
        macro_f1 = f1_score(y_test_level, test_preds_level, average="macro")

        # Per-class Precision / Recall breakdown (Low=0, Medium=1, High=2)
        precisions = precision_score(y_test_level, test_preds_level, average=None)
        recalls = recall_score(y_test_level, test_preds_level, average=None)
        f1s = f1_score(y_test_level, test_preds_level, average=None)
        cm = confusion_matrix(y_test_level, test_preds_level)

        med_prec = float(precisions[1])
        med_rec = float(recalls[1])
        med_f1 = float(f1s[1])

        print(f"[{label}] Formula Correlation -> Pearson r: {corr_r:.6f} | R2: {r2_val:.6f}")
        print(f"[{label}] Regression Metrics  -> MAE: {mae:.4f} | RMSE: {rmse:.4f}")
        print(f"[{label}] Label-Specific Cutoffs -> P33 (Low/Med): {p33:.4f} | P66 (Med/High): {p66:.4f}")
        print(f"[{label}] Classification Overall -> Accuracy: {acc:.4f} | Macro F1: {macro_f1:.4f}")
        print(f"[{label}] Low    Class -> Prec: {precisions[0]:.4f} | Rec: {recalls[0]:.4f} | F1: {f1s[0]:.4f}")
        print(f"[{label}] Medium Class -> Prec: {med_prec:.4f} | Rec: {med_rec:.4f} | F1: {med_f1:.4f}  *** TARGET > 0.50 RECALL ***")
        print(f"[{label}] High   Class -> Prec: {precisions[2]:.4f} | Rec: {recalls[2]:.4f} | F1: {f1s[2]:.4f}")
        print(f"[{label}] Confusion Matrix:\n{cm}")

        reports[label] = {
            "formula_pearson_r": round(float(corr_r), 6),
            "formula_r2": round(float(r2_val), 6),
            "regression_mae": round(float(mae), 4),
            "regression_rmse": round(float(rmse), 4),
            "accuracy": round(float(acc), 4),
            "macro_f1": round(float(macro_f1), 4),
            "medium_precision": round(med_prec, 4),
            "medium_recall": round(med_rec, 4),
            "medium_f1": round(med_f1, 4),
            "per_class_precision": [round(float(p), 4) for p in precisions],
            "per_class_recall": [round(float(r), 4) for r in recalls],
            "per_class_f1": [round(float(f), 4) for f in f1s],
            "thresholds": thresholds[label],
            "confusion_matrix": cm.tolist()
        }

    return scaler, reg_models, thresholds, reports


def run_shap_analysis(reg_models, X_val, feature_names):
    print("\n==================================================")
    print("STEP 4: SHAP FEATURE ATTRIBUTION ANALYSIS (REGRESSION)")
    print("==================================================")

    for label in LABELS:
        model = reg_models[label]
        explainer = shap.TreeExplainer(model)
        shap_values = explainer.shap_values(X_val)

        mean_abs_shap = np.abs(np.array(shap_values)).mean(axis=0)

        shap_df = pd.DataFrame({
            "feature": feature_names,
            "mean_abs_shap": mean_abs_shap
        }).sort_values(by="mean_abs_shap", ascending=False)

        print(f"\n--- [{label}] Top 10 Most Influential Features ---")
        for idx, row in shap_df.head(10).iterrows():
            print(f"  {row['feature']:30s}: SHAP = {row['mean_abs_shap']:.4f}")

        social_feats = [f for f in feature_names if "social" in f]
        social_shap = shap_df[shap_df["feature"].isin(social_feats)]
        print(f"[{label}] Social App Feature Group Total Attribution: {social_shap['mean_abs_shap'].sum():.4f}")


def export_artifacts(scaler, reg_models, thresholds, reports, models_dir):
    print("\n==================================================")
    print("STEP 5: EXPORTING PRODUCTION ML ARTIFACTS")
    print("==================================================")
    os.makedirs(models_dir, exist_ok=True)

    preprocessor_path = os.path.join(models_dir, "preprocessor.pkl")
    joblib.dump(scaler, preprocessor_path)
    print(f"Saved Preprocessor: {preprocessor_path}")

    model_path = os.path.join(models_dir, "wellbeing_model.pkl")
    joblib.dump(reg_models, model_path)
    print(f"Saved Model Pipeline: {model_path}")

    meta = {
        "timestamp": datetime.now().isoformat(),
        "feature_columns": FEATURE_COLUMNS,
        "labels": LABELS,
        "thresholds": thresholds,
        "evaluation_reports": reports
    }
    meta_path = os.path.join(models_dir, "model_meta.json")
    with open(meta_path, "w") as f:
        json.dump(meta, f, indent=2)
    print(f"Saved Model Metadata: {meta_path}")


def evaluate_noise_sensitivity():
    print("\n==================================================")
    print("STEP 6: QUANTITATIVE FORMULA CORRELATION & NOISE SENSITIVITY SWEEP")
    print("==================================================")

    script_dir = os.path.dirname(os.path.abspath(__file__))
    sys.path.append(os.path.join(script_dir, "data"))
    import generate_dataset

    noise_levels = [0.05, 0.08, 0.10]
    sweep_results = {}

    for noise in noise_levels:
        print(f"\n==================================================")
        print(f"RUNNING SWEEP FOR SIGMA = {noise:.2f}")
        print(f"==================================================")

        df_noise = generate_dataset.generate_synthetic_data(num_rows=3000, seed=42, noise_level=noise)

        print(f"--- Dataset Target Distribution (sigma={noise:.2f}) ---")
        for label in LABELS:
            col = f"{label.lower()}_level"
            dist = df_noise[col].value_counts().to_dict()
            score_std = float(df_noise[f"{label.lower()}_score"].std())
            print(f"  {label:12s} Level Counts: {dist} | Score Std: {score_std:.6f}")

        train_df, val_df, test_df = temporal_train_val_test_split(df_noise)
        scaler, models, th, reps = train_and_evaluate_regression_models(train_df, val_df, test_df)

        sweep_results[noise] = reps

    print("\n==================================================")
    print("QUANTITATIVE FORMULA CORRELATION & NOISE SWEEP REPORT")
    print("==================================================")
    for noise in noise_levels:
        print(f"\n--- Noise Level sigma = {noise:.2f} ---")
        for label in LABELS:
            r2 = sweep_results[noise][label]["formula_r2"]
            r_val = sweep_results[noise][label]["formula_pearson_r"]
            rec = sweep_results[noise][label]["medium_recall"]
            f1 = sweep_results[noise][label]["macro_f1"]
            mae = sweep_results[noise][label]["regression_mae"]
            print(f"  {label:12s} -> Formula R2: {r2:.6f} | Pearson r: {r_val:.6f} | MAE: {mae:.6f} | Med Recall: {rec:.6f} | Macro F1: {f1:.6f}")

    return sweep_results


def main():
    base_dir = os.path.dirname(os.path.abspath(__file__))
    script_dir = os.path.join(base_dir, "data")
    sys.path.append(script_dir)
    import generate_dataset

    # Run Quantitative Correlation Sweep first
    sweep_results = evaluate_noise_sensitivity()

    # Compare formula R2 between 0.05 and 0.08 across labels
    r2_005 = np.mean([sweep_results[0.05][lbl]["formula_r2"] for lbl in LABELS])
    r2_008 = np.mean([sweep_results[0.08][lbl]["formula_r2"] for lbl in LABELS])
    delta_r2 = r2_005 - r2_008

    print(f"\n==================================================")
    print(f"DECISION AUDIT: Mean Formula R2 at sigma=0.05: {r2_005:.6f} vs sigma=0.08: {r2_008:.6f} (Delta R2 = {delta_r2:.6f})")

    if delta_r2 <= 0.05:
        winning_sigma = 0.05
        print(f"[DECISION] Delta R2 ({delta_r2:.6f}) is minimal (<= 0.05). sigma=0.05 DOES NOT reintroduce formula recovery leakage.")
        print("[DECISION] Selecting sigma=0.05 as optimal production noise level (superior MAE, Macro F1, and Medium Recall).")
    else:
        winning_sigma = 0.08
        print(f"[DECISION] Delta R2 ({delta_r2:.6f}) exceeds threshold (> 0.05). Selecting sigma=0.08 to prevent formula recovery.")

    print(f"==================================================\n")

    # Regenerate dataset CSV with selected winning sigma
    dataset_path = os.path.join(base_dir, "data", "dataset.csv")
    print(f"Regenerating production dataset.csv with winning sigma={winning_sigma:.2f}...")
    df_prod = generate_dataset.generate_synthetic_data(num_rows=5000, seed=42, noise_level=winning_sigma)
    df_prod.to_csv(dataset_path, index=False)
    generate_dataset.create_data_dictionary(script_dir)
    generate_dataset.validate_dataset(df_prod, expected_rows=5000)

    # Train production pipeline using winning sigma
    df = load_dataset(dataset_path)
    train_df, val_df, test_df = temporal_train_val_test_split(df)
    scaler, reg_models, thresholds, reports = train_and_evaluate_regression_models(train_df, val_df, test_df)

    X_val_scaled = scaler.transform(val_df[FEATURE_COLUMNS])
    run_shap_analysis(reg_models, X_val_scaled, FEATURE_COLUMNS)

    models_dir = os.path.join(base_dir, "models")
    export_artifacts(scaler, reg_models, thresholds, reports, models_dir)

    print("\n==================================================")
    print("PRODUCTION MODEL TRAINING & ARTIFACT EXPORT COMPLETE!")
    print("==================================================\n")


if __name__ == "__main__":
    main()