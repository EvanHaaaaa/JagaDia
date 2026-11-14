"""Baseline model training script (Random Forest & XGBoost).
Includes preprocessing, training, validation, and model serialization.
"""

from pathlib import Path
import joblib
import pandas as pd
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import OneHotEncoder
from sklearn.compose import ColumnTransformer
from sklearn.pipeline import Pipeline
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import classification_report, roc_auc_score, f1_score

from .config import (
    RAW_DATA_DIR,
    PROCESSED_DATA_DIR,
    MODELS_DIR,
    RANDOM_STATE,
    TARGET_COLUMN,
)
from .data_pipeline import generate_mock_crime_dataset, load_data

try:
    from xgboost import XGBClassifier
    HAS_XGBOOST = True
except ImportError:
    HAS_XGBOOST = False


def build_preprocessor(categorical_cols: list, numerical_cols: list) -> ColumnTransformer:
    """Build sklearn ColumnTransformer for encoding and scaling."""
    return ColumnTransformer(
        transformers=[
            ("cat", OneHotEncoder(handle_unknown="ignore"), categorical_cols),
            ("num", "passthrough", numerical_cols),
        ]
    )


def train_baseline():
    """Execute end-to-end baseline model training."""
    raw_csv = RAW_DATA_DIR / "sample_crime_data.csv"
    if not raw_csv.exists():
        print("Raw dataset not found. Generating sample dataset...")
        df = generate_mock_crime_dataset(n_samples=2500, output_path=raw_csv)
    else:
        df = load_data(raw_csv)

    # Feature definitions
    categorical_cols = ["road_type"]
    numerical_cols = [
        "latitude",
        "longitude",
        "street_light_density",
        "dist_to_police_station",
        "hour",
        "day_of_week",
        "is_weekend",
        "is_night",
        "hour_sin",
        "hour_cos",
    ]

    features = categorical_cols + numerical_cols
    X = df[features]
    y = df[TARGET_COLUMN]

    # Temporal / Stratified Train-Test Split (80% train, 20% test)
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=RANDOM_STATE, stratify=y
    )

    print(f"Dataset split: Train={len(X_train)} samples, Test={len(X_test)} samples")
    print(f"Positive (High Risk) ratio in train: {y_train.mean():.2%}")

    preprocessor = build_preprocessor(categorical_cols, numerical_cols)

    # 1. Random Forest Classifier
    rf_pipeline = Pipeline(
        steps=[
            ("preprocessor", preprocessor),
            (
                "classifier",
                RandomForestClassifier(
                    n_estimators=100,
                    max_depth=10,
                    random_state=RANDOM_STATE,
                    class_weight="balanced",
                ),
            ),
        ]
    )

    print("\nTraining Random Forest Baseline...")
    rf_pipeline.fit(X_train, y_train)
    rf_preds = rf_pipeline.predict(X_test)
    rf_probs = rf_pipeline.predict_proba(X_test)[:, 1]

    print("\n=== Random Forest Results ===")
    print(classification_report(y_test, rf_preds, target_names=["Aman", "Rawan"]))
    print(f"ROC-AUC Score: {roc_auc_score(y_test, rf_probs):.4f}")

    # 2. XGBoost Classifier (if available)
    best_model = rf_pipeline
    model_name = "random_forest_baseline"

    if HAS_XGBOOST:
        print("\nTraining XGBoost Classifier...")
        xgb_pipeline = Pipeline(
            steps=[
                ("preprocessor", preprocessor),
                (
                    "classifier",
                    XGBClassifier(
                        n_estimators=100,
                        max_depth=6,
                        learning_rate=0.08,
                        random_state=RANDOM_STATE,
                        eval_metric="logloss",
                    ),
                ),
            ]
        )
        xgb_pipeline.fit(X_train, y_train)
        xgb_preds = xgb_pipeline.predict(X_test)
        xgb_probs = xgb_pipeline.predict_proba(X_test)[:, 1]

        print("\n=== XGBoost Results ===")
        print(classification_report(y_test, xgb_preds, target_names=["Aman", "Rawan"]))
        print(f"ROC-AUC Score: {roc_auc_score(y_test, xgb_probs):.4f}")

        if f1_score(y_test, xgb_preds) >= f1_score(y_test, rf_preds):
            best_model = xgb_pipeline
            model_name = "xgboost_baseline"

    # Save model to disk
    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    model_output_path = MODELS_DIR / f"{model_name}.joblib"
    joblib.dump(best_model, model_output_path)
    print(f"\nModel saved successfully to: {model_output_path}")

    # Save processed splits for reproducibility
    PROCESSED_DATA_DIR.mkdir(parents=True, exist_ok=True)
    X_train.assign(target=y_train).to_csv(
        PROCESSED_DATA_DIR / "train_processed.csv", index=False
    )
    X_test.assign(target=y_test).to_csv(
        PROCESSED_DATA_DIR / "test_processed.csv", index=False
    )
    print(f"Processed splits saved to {PROCESSED_DATA_DIR}")


if __name__ == "__main__":
    train_baseline()
