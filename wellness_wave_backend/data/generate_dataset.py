"""
generate_dataset.py
===================
Synthetic Dataset Generator for Wellness Wave Behavioral Wellbeing Multi-Label Classification
with Strict Temporal Isolation and Realistic Stochastic Label Variability.
"""

import argparse
import os
import sys
from datetime import datetime, timedelta
import numpy as np
import pandas as pd


def set_seed(seed: int = 42) -> None:
    """Set global numpy random seed for exact reproducibility."""
    np.random.seed(seed)


def generate_synthetic_data(num_rows: int = 5000, seed: int = 42, noise_level: float = 0.08) -> pd.DataFrame:
    set_seed(seed)

    num_users = min(100, max(10, num_rows // 10))
    user_ids = [f"user_{i+1:03d}" for i in range(num_users)]
    start_date = datetime(2026, 1, 1)

    target_classes = ["Balanced", "Stress", "Addiction", "Burnout"]

    user_primary_profile = np.random.choice(target_classes, size=num_users)
    user_traits = {
        uid: {
            "profile": user_primary_profile[i],
            "screen_time_bias": np.random.normal(0, 15),
            "unlock_bias": np.random.normal(0, 5),
            "typing_bias": np.random.normal(0, 3),
            "scroll_bias": np.random.normal(0, 20),
        }
        for i, uid in enumerate(user_ids)
    }

    records = []
    rows_per_user = num_rows // num_users
    remainder = num_rows % num_users

    current_record_num = 1

    for u_idx, uid in enumerate(user_ids):
        user_days = rows_per_user + (1 if u_idx < remainder else 0)
        u_trait = user_traits[uid]

        for d in range(user_days):
            date_str = (start_date + timedelta(days=d)).strftime("%Y-%m-%d")

            if np.random.rand() < 0.90:
                profile = u_trait["profile"]
            else:
                profile = np.random.choice(target_classes)

            if profile == "Balanced":
                screen_time = np.random.normal(180, 35) + u_trait["screen_time_bias"]
                unlocks = np.random.normal(45, 10) + u_trait["unlock_bias"]
                switches = np.random.normal(50, 15)
                typing_speed = np.random.normal(50, 8) + u_trait["typing_bias"]
                scroll_speed = np.random.normal(250, 40) + u_trait["scroll_bias"]
                night_minutes = np.random.exponential(12)
                social_share = np.random.uniform(0.20, 0.40)
                product_share = np.random.uniform(0.25, 0.45)
                self_report = int(np.clip(np.random.normal(4.3, 0.6), 1, 5))

            elif profile == "Stress":
                screen_time = np.random.normal(320, 55) + u_trait["screen_time_bias"]
                unlocks = np.random.normal(110, 25) + u_trait["unlock_bias"]
                switches = np.random.normal(260, 45)
                typing_speed = np.random.normal(68, 10) + u_trait["typing_bias"]
                scroll_speed = np.random.normal(850, 120) + u_trait["scroll_bias"]
                night_minutes = np.random.normal(90, 25)
                social_share = np.random.uniform(0.25, 0.45)
                product_share = np.random.uniform(0.20, 0.40)
                self_report = int(np.clip(np.random.normal(2.7, 0.8), 1, 5))

            elif profile == "Addiction":
                screen_time = np.random.normal(540, 75) + u_trait["screen_time_bias"]
                unlocks = np.random.normal(190, 35) + u_trait["unlock_bias"]
                switches = np.random.normal(330, 60)
                typing_speed = np.random.normal(55, 9) + u_trait["typing_bias"]
                scroll_speed = np.random.normal(650, 90) + u_trait["scroll_bias"]
                night_minutes = np.random.normal(180, 45)
                social_share = np.random.uniform(0.55, 0.80)
                product_share = np.random.uniform(0.05, 0.15)
                self_report = int(np.clip(np.random.normal(2.5, 0.8), 1, 5))

            else:  # Burnout
                screen_time = np.random.normal(480, 65) + u_trait["screen_time_bias"]
                unlocks = np.random.normal(95, 20) + u_trait["unlock_bias"]
                switches = np.random.normal(110, 30)
                typing_speed = np.random.normal(24, 6) + u_trait["typing_bias"]
                scroll_speed = np.random.normal(220, 50) + u_trait["scroll_bias"]
                night_minutes = np.random.normal(210, 40)
                social_share = np.random.uniform(0.30, 0.50)
                product_share = np.random.uniform(0.05, 0.15)
                self_report = int(np.clip(np.random.normal(1.6, 0.6), 1, 5))

            screen_time_minutes = float(np.clip(screen_time, 15.0, 960.0))
            unlock_count = int(np.clip(unlocks, 1, 300))
            app_switch_count = int(np.clip(switches, 1, 600))
            typing_speed_wpm = float(np.clip(typing_speed, 10.0, 100.0))
            scroll_spd = float(np.clip(scroll_speed, 50.0, 1500.0))
            night_usage_minutes = float(np.clip(night_minutes, 0.0, screen_time_minutes))

            max_social = screen_time_minutes * social_share
            social_app_minutes = float(np.clip(max_social, 0.0, screen_time_minutes))
            rem_time = max(0.0, screen_time_minutes - social_app_minutes)
            max_productive = screen_time_minutes * product_share
            productive_app_minutes = float(np.clip(max_productive, 0.0, rem_time))

            # --- Basic Derived Features ---
            night_ratio = float(np.clip(night_usage_minutes / screen_time_minutes, 0.0, 1.0))
            unlock_intensity = float(unlock_count / screen_time_minutes)

            switches_per_hour = app_switch_count / (screen_time_minutes / 60.0)
            fragmentation_index = float(np.clip(switches_per_hour / 120.0, 0.0, 1.0))

            s_scroll = np.clip(scroll_spd / 1500.0, 0.0, 1.0)
            s_frag = fragmentation_index
            s_unlock = np.clip(unlock_intensity / 0.60, 0.0, 1.0)
            s_night = night_ratio
            s_typing_elev = np.clip((typing_speed_wpm - 30.0) / 70.0, 0.0, 1.0)

            raw_agitation = (
                0.30 * s_scroll
                + 0.30 * s_frag
                + 0.15 * s_unlock
                + 0.15 * s_night
                + 0.10 * s_typing_elev
            )
            agitation_score = float(np.clip(np.round(raw_agitation, 4), 0.0, 1.0))

            # --- Social App Feature Group ---
            social_ratio = float(np.clip(social_app_minutes / screen_time_minutes, 0.0, 1.0))
            social_unlocks = max(1, int(unlock_count * social_share))
            social_opens_per_hour = float(social_unlocks / (screen_time_minutes / 60.0))
            social_session_avg = float(social_app_minutes / social_unlocks)
            social_session_var = float((social_session_avg * 0.35) ** 2)
            late_night_social_minutes = float(night_usage_minutes * social_share)
            late_night_social_ratio = float(np.clip(late_night_social_minutes / max(1.0, social_app_minutes), 0.0, 1.0))
            social_inter_open_gap_min = float(max(0.5, (1440.0 - screen_time_minutes) / social_unlocks))

            record_id = f"REC_{current_record_num:06d}"
            current_record_num += 1

            records.append({
                "record_id": record_id,
                "user_id": uid,
                "date": date_str,
                "screen_time_minutes": round(screen_time_minutes, 2),
                "unlock_count": unlock_count,
                "app_switch_count": app_switch_count,
                "typing_speed_wpm": round(typing_speed_wpm, 2),
                "scroll_speed": round(scroll_spd, 2),
                "night_usage_minutes": round(night_usage_minutes, 2),
                "social_app_minutes": round(social_app_minutes, 2),
                "productive_app_minutes": round(productive_app_minutes, 2),
                "night_ratio": round(night_ratio, 4),
                "unlock_intensity": round(unlock_intensity, 4),
                "fragmentation_index": round(fragmentation_index, 4),
                "agitation_score": round(agitation_score, 4),
                "social_ratio": round(social_ratio, 4),
                "social_opens_per_hour": round(social_opens_per_hour, 4),
                "social_session_avg": round(social_session_avg, 2),
                "social_session_var": round(social_session_var, 2),
                "late_night_social_ratio": round(late_night_social_ratio, 4),
                "social_inter_open_gap_min": round(social_inter_open_gap_min, 2),
                "self_report_score": self_report,
                "label": profile,
            })

    df = pd.DataFrame(records)

    # --- Strict Temporal Isolation for Rolling / Lag Features ---
    df.sort_values(by=["user_id", "date"], inplace=True)
    df.reset_index(drop=True, inplace=True)

    label_map = {"Balanced": 0, "Stress": 1, "Addiction": 2, "Burnout": 3}
    # Lag-1 previous label (strictly yesterday's label)
    df["prev_label_lag1"] = df.groupby("user_id")["label"].shift(1).map(label_map).fillna(0).astype(int)

    # Rolling averages on PRIOR days only (shift 1 so today is excluded from historical average)
    for col in ["screen_time_minutes", "social_ratio", "app_switch_count", "scroll_speed", "typing_speed_wpm"]:
        df[f"roll_3d_{col}"] = df.groupby("user_id")[col].transform(lambda x: x.shift(1).rolling(3, min_periods=1).mean()).fillna(df[col]).round(4)
        df[f"roll_7d_{col}"] = df.groupby("user_id")[col].transform(lambda x: x.shift(1).rolling(7, min_periods=1).mean()).fillna(df[col]).round(4)

    # Day-over-Day % Changes (strictly prior day comparison)
    for col in ["screen_time_minutes", "app_switch_count", "scroll_speed", "typing_speed_wpm"]:
        prev_val = df.groupby("user_id")[col].shift(1).fillna(df[col])
        dod_change = (df[col] - prev_val) / np.maximum(1.0, prev_val)
        df[f"dod_pct_{col}"] = dod_change.clip(-2.0, 2.0).round(4)

    # --- Stochastic Multi-label Score & Ground-truth Level Generation ---
    # --- Multi-label Raw Formula & Stochastic Score Generation ---
    raw_formula_stress = (
        0.35 * (df["scroll_speed"] / 1500.0) +
        0.35 * df["fragmentation_index"] +
        0.15 * (df["typing_speed_wpm"] / 100.0) +
        0.15 * (df["screen_time_minutes"] / 960.0)
    )
    df["raw_formula_stress"] = np.clip(raw_formula_stress, 0.0, 1.0).round(4)
    df["stress_score"] = np.clip(raw_formula_stress + np.random.normal(0, noise_level, size=len(df)), 0.0, 1.0).round(4)

    raw_formula_anxiety = (
        0.40 * df["agitation_score"] +
        0.30 * (1.0 / (1.0 + df["social_inter_open_gap_min"])) +
        0.30 * df["fragmentation_index"]
    )
    df["raw_formula_anxiety"] = np.clip(raw_formula_anxiety, 0.0, 1.0).round(4)
    df["anxiety_score"] = np.clip(raw_formula_anxiety + np.random.normal(0, noise_level, size=len(df)), 0.0, 1.0).round(4)

    raw_formula_burnout = (
        0.45 * df["night_ratio"] +
        0.35 * (df["screen_time_minutes"] / 960.0) +
        0.20 * (1.0 - df["typing_speed_wpm"] / 100.0)
    )
    df["raw_formula_burnout"] = np.clip(raw_formula_burnout, 0.0, 1.0).round(4)
    df["burnout_score"] = np.clip(raw_formula_burnout + np.random.normal(0, noise_level, size=len(df)), 0.0, 1.0).round(4)

    raw_formula_addiction = (
        0.40 * df["social_ratio"] +
        0.30 * df["late_night_social_ratio"] +
        0.30 * (df["social_opens_per_hour"] / 30.0)
    )
    df["raw_formula_addiction"] = np.clip(raw_formula_addiction, 0.0, 1.0).round(4)
    df["addiction_score"] = np.clip(raw_formula_addiction + np.random.normal(0, noise_level, size=len(df)), 0.0, 1.0).round(4)

    # Meaningful, realistic target level cutoffs
    for metric in ["stress", "anxiety", "burnout", "addiction"]:
        p33 = df[f"{metric}_score"].quantile(0.33)
        p66 = df[f"{metric}_score"].quantile(0.66)
        df[f"{metric}_level"] = pd.cut(
            df[f"{metric}_score"],
            bins=[-np.inf, p33, p66, np.inf],
            labels=["Low", "Medium", "High"]
        )

    return df


def validate_dataset(df: pd.DataFrame, expected_rows: int) -> bool:
    print("\n==================================================")
    print("RUNNING AUTOMATED DATASET VALIDATION CHECKS")
    print("==================================================")

    assert len(df) == expected_rows, f"Row count mismatch! Expected {expected_rows}, got {len(df)}"
    print(f"[PASS] 1. Row count matches expected ({len(df)} rows).")

    assert df["record_id"].nunique() == len(df), "record_id is not unique!"
    print("[PASS] 2. record_id is strictly unique.")

    duplicates = df.duplicated(subset=["user_id", "date"]).sum()
    assert duplicates == 0, f"Found {duplicates} duplicate user_id + date pairs!"
    print("[PASS] 3. No duplicate user_id + date combinations.")

    assert df.isnull().sum().sum() == 0, "Missing values found!"
    print("[PASS] 4. No missing (null) values.")

    assert not df.isna().any().any(), "NaN values found!"
    print("[PASS] 5. No NaN values.")

    numeric_cols = df.select_dtypes(include=[np.number]).columns
    assert not np.isinf(df[numeric_cols]).any().any(), "Infinite values found!"
    print("[PASS] 6. No infinity values.")

    new_social_cols = [
        "social_ratio", "social_opens_per_hour", "social_session_avg",
        "social_session_var", "late_night_social_ratio", "social_inter_open_gap_min"
    ]
    for col in new_social_cols:
        assert col in df.columns, f"Missing social feature column: {col}"
    print("[PASS] 7. All 6 Social App Feature Group columns present.")

    temporal_cols = [
        "prev_label_lag1", "roll_3d_screen_time_minutes", "roll_7d_screen_time_minutes",
        "dod_pct_screen_time_minutes", "dod_pct_app_switch_count"
    ]
    for col in temporal_cols:
        assert col in df.columns, f"Missing temporal feature column: {col}"
    print("[PASS] 8. All Temporal/Historical features present.")

    multi_label_cols = ["stress_level", "anxiety_level", "burnout_level", "addiction_level"]
    for col in multi_label_cols:
        assert col in df.columns, f"Missing multi-label column: {col}"
    print("[PASS] 9. All 4 multi-label target levels present.")

    print("\n[SUCCESS] Dataset validation passed clean.")
    return True


def create_data_dictionary(output_dir: str) -> str:
    dict_records = [
        {"column_name": "record_id", "data_type": "string", "description": "Unique record identifier"},
        {"column_name": "user_id", "data_type": "string", "description": "Synthetic user identifier"},
        {"column_name": "date", "data_type": "string", "description": "Calendar date (YYYY-MM-DD)"},
        {"column_name": "screen_time_minutes", "data_type": "float", "description": "Daily screen time minutes"},
        {"column_name": "unlock_count", "data_type": "integer", "description": "Phone unlock count"},
        {"column_name": "app_switch_count", "data_type": "integer", "description": "Application switch count"},
        {"column_name": "typing_speed_wpm", "data_type": "float", "description": "Typing speed wpm"},
        {"column_name": "scroll_speed", "data_type": "float", "description": "Scroll speed px/s"},
        {"column_name": "night_usage_minutes", "data_type": "float", "description": "Night usage minutes"},
        {"column_name": "social_app_minutes", "data_type": "float", "description": "Social app usage minutes"},
        {"column_name": "productive_app_minutes", "data_type": "float", "description": "Productive app usage minutes"},
        {"column_name": "social_ratio", "data_type": "float", "description": "Social app minutes / total screen time"},
        {"column_name": "social_opens_per_hour", "data_type": "float", "description": "Social app opens per hour"},
        {"column_name": "social_session_avg", "data_type": "float", "description": "Average social app session duration"},
        {"column_name": "social_session_var", "data_type": "float", "description": "Variance of social app session duration"},
        {"column_name": "late_night_social_ratio", "data_type": "float", "description": "Late-night social usage ratio"},
        {"column_name": "social_inter_open_gap_min", "data_type": "float", "description": "Average minutes between social opens"},
        {"column_name": "prev_label_lag1", "data_type": "integer", "description": "Previous day state (lag-1)"},
        {"column_name": "stress_score", "data_type": "float", "description": "Continuous stress risk score"},
        {"column_name": "anxiety_score", "data_type": "float", "description": "Continuous anxiety risk score"},
        {"column_name": "burnout_score", "data_type": "float", "description": "Continuous burnout risk score"},
        {"column_name": "addiction_score", "data_type": "float", "description": "Continuous addiction risk score"},
        {"column_name": "stress_level", "data_type": "string", "description": "Stress severity level (Low, Medium, High)"},
        {"column_name": "anxiety_level", "data_type": "string", "description": "Anxiety severity level (Low, Medium, High)"},
        {"column_name": "burnout_level", "data_type": "string", "description": "Burnout severity level (Low, Medium, High)"},
        {"column_name": "addiction_level", "data_type": "string", "description": "Addiction severity level (Low, Medium, High)"},
        {"column_name": "label", "data_type": "string", "description": "Primary behavioral state"}
    ]

    dict_df = pd.DataFrame(dict_records)
    dict_path = os.path.join(output_dir, "data_dictionary.csv")
    dict_df.to_csv(dict_path, index=False)
    print(f"Data dictionary created at: {dict_path}")
    return dict_path


def main():
    parser = argparse.ArgumentParser(description="Generate synthetic multi-label dataset for Wellness Wave.")
    parser.add_argument("--rows", type=int, default=5000, help="Number of rows (default: 5000)")
    parser.add_argument("--seed", type=int, default=42, help="Random seed (default: 42)")
    parser.add_argument("--noise", type=float, default=0.08, help="Label noise std level (default: 0.08)")

    args = parser.parse_args()

    script_dir = os.path.dirname(os.path.abspath(__file__))
    output_csv = os.path.join(script_dir, "dataset.csv")

    print(f"Generating multi-label synthetic dataset with {args.rows} rows (seed: {args.seed}, noise: {args.noise})...")
    df = generate_synthetic_data(num_rows=args.rows, seed=args.seed, noise_level=args.noise)

    df.to_csv(output_csv, index=False)
    print(f"Dataset successfully saved to: {output_csv}")

    create_data_dictionary(script_dir)
    validate_dataset(df, expected_rows=args.rows)


if __name__ == "__main__":
    main()
