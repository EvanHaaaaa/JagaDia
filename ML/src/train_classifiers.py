"""Supervised Risk Classification Module (Tahap 2).
Compares Logistic Regression, Support Vector Machine (RBF), and Random Forest.
Based on COMP657701 Machine Learning:
- Session 07-08: Logistic Regression
- Session 09-10: Model Selection and Regularization (Cross Validation & Hyperparameter Tuning)
- Session 15-16: Decision Tree and Ensemble Learning (Random Forest)
- Session 17-18: Support Vector Machine (RBF Kernel)
"""

from pathlib import Path
from typing import Dict, Tuple
import joblib
import numpy as np
import pandas as pd

from sklearn.compose import ColumnTransformer
from sklearn.preprocessing import StandardScaler, OneHotEncoder
from sklearn.pipeline import Pipeline
from sklearn.linear_model import LogisticRegression
from sklearn.svm import SVC
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import train_test_split, StratifiedKFold, cross_validate
from sklearn.metrics import (
    accuracy_score,
    precision_score,
    recall_score,
    f1_score,
    roc_auc_score,
    average_precision_score,
    confusion_matrix,
    classification_report,
)

from .config import RANDOM_STATE, PROCESSED_DATA_DIR, MODELS_DIR
from .dataset_builder import build_classification_dataset

try:
    from xgboost import XGBClassifier
    HAS_XGBOOST = True
except ImportError:
    HAS_XGBOOST = False

NUMERICAL_FEATURES = [
    "latitude",
    "longitude",
    "hour",
    "is_night",
    "day_of_week",
    "is_weekend",
    "hour_sin",
    "hour_cos",
    "street_light_density",
    "dist_to_hotspot_m",
    "closest_hotspot_risk",
    "dist_to_police_m",
]
CATEGORICAL_FEATURES = ["road_type"]
ALL_FEATURES = NUMERICAL_FEATURES + CATEGORICAL_FEATURES
TARGET_COL = "is_high_risk"


def build_preprocessor() -> ColumnTransformer:
    """Build standardized feature preprocessor.
    Scales numerical features for distance/gradient based models (LogReg & SVM)
    and one-hot encodes road types.
    """
    return ColumnTransformer(
        transformers=[
            ("num", StandardScaler(), NUMERICAL_FEATURES),
            ("cat", OneHotEncoder(handle_unknown="ignore"), CATEGORICAL_FEATURES),
        ]
    )


def initialize_candidate_models() -> Dict[str, Pipeline]:
    """Instantiate candidate models in unified Scikit-Learn pipelines."""
    preprocessor = build_preprocessor()
    models = {
        "Logistic Regression (Session 07-08)": Pipeline([
            ("preprocessor", preprocessor),
            ("classifier", LogisticRegression(
                C=1.0, penalty="l2", solver="lbfgs", max_iter=1000, random_state=RANDOM_STATE
            )),
        ]),
        "Support Vector Machine (Session 17-18)": Pipeline([
            ("preprocessor", preprocessor),
            ("classifier", SVC(
                kernel="rbf", C=1.5, gamma="scale", probability=True, random_state=RANDOM_STATE
            )),
        ]),
        "Random Forest (Session 15-16)": Pipeline([
            ("preprocessor", preprocessor),
            ("classifier", RandomForestClassifier(
                n_estimators=150, max_depth=12, min_samples_split=4,
                class_weight="balanced", random_state=RANDOM_STATE
            )),
        ]),
    }

    if HAS_XGBOOST:
        models["XGBoost (Session 15-16)"] = Pipeline([
            ("preprocessor", preprocessor),
            ("classifier", XGBClassifier(
                n_estimators=120, max_depth=5, learning_rate=0.08,
                eval_metric="logloss", random_state=RANDOM_STATE
            )),
        ])

    return models


def evaluate_cross_validation(
    models: Dict[str, Pipeline], X: pd.DataFrame, y: pd.Series, cv_folds: int = 5
) -> pd.DataFrame:
    """Perform Stratified K-Fold Cross Validation across all models (Session 09-10)."""
    skf = StratifiedKFold(n_splits=cv_folds, shuffle=True, random_state=RANDOM_STATE)
    scoring = {
        "accuracy": "accuracy",
        "precision": "precision",
        "recall": "recall",
        "f1": "f1",
        "roc_auc": "roc_auc",
    }

    results = []
    print(f"\n--- MENJALANKAN STRATIFIED {cv_folds}-FOLD CROSS VALIDATION ---")
    for name, pipeline in models.items():
        cv_res = cross_validate(pipeline, X, y, cv=skf, scoring=scoring, n_jobs=-1)
        results.append({
            "Model": name,
            "CV Accuracy": f"{cv_res['test_accuracy'].mean():.4f} ± {cv_res['test_accuracy'].std():.3f}",
            "CV Precision": f"{cv_res['test_precision'].mean():.4f} ± {cv_res['test_precision'].std():.3f}",
            "CV Recall (Priority)": f"{cv_res['test_recall'].mean():.4f} ± {cv_res['test_recall'].std():.3f}",
            "CV F1-Score": f"{cv_res['test_f1'].mean():.4f} ± {cv_res['test_f1'].std():.3f}",
            "CV ROC-AUC": f"{cv_res['test_roc_auc'].mean():.4f} ± {cv_res['test_roc_auc'].std():.3f}",
            "_raw_recall": cv_res["test_recall"].mean(),
            "_raw_f1": cv_res["test_f1"].mean(),
            "_raw_auc": cv_res["test_roc_auc"].mean(),
        })

    df_results = pd.DataFrame(results).sort_values(by="_raw_auc", ascending=False)
    return df_results


def train_and_select_best_model(
    dataset_path: Path = None,
) -> Tuple[Pipeline, pd.DataFrame, Dict]:
    """Train models on Train-Test split, evaluate test performance, and save the best model."""
    if dataset_path is None or not dataset_path.exists():
        dataset_path = PROCESSED_DATA_DIR / "classification_dataset.csv"
        if not dataset_path.exists():
            print("Dataset belum ditemukan. Membangun dataset klasifikasi...")
            build_classification_dataset(n_samples=2500, output_path=dataset_path)

    df = pd.read_csv(dataset_path)
    X = df[ALL_FEATURES]
    y = df[TARGET_COL]

    # Stratified Split: 80% Train, 20% Test
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.20, random_state=RANDOM_STATE, stratify=y
    )

    models = initialize_candidate_models()

    # 1. Cross-Validation Comparison
    cv_summary = evaluate_cross_validation(models, X_train, y_train, cv_folds=5)

    # 2. Fit each model on full training set and evaluate on test set
    test_metrics = []
    trained_pipelines = {}

    print("\n--- EVALUASI DATA TEST (HOLDOUT 20%) ---")
    for name, pipeline in models.items():
        pipeline.fit(X_train, y_train)
        trained_pipelines[name] = pipeline

        y_pred = pipeline.predict(X_test)
        y_prob = pipeline.predict_proba(X_test)[:, 1]

        acc = accuracy_score(y_test, y_pred)
        prec = precision_score(y_test, y_pred)
        rec = recall_score(y_test, y_pred)
        f1 = f1_score(y_test, y_pred)
        auc = roc_auc_score(y_test, y_prob)
        pr_auc = average_precision_score(y_test, y_prob)
        cm = confusion_matrix(y_test, y_pred)

        test_metrics.append({
            "Model": name,
            "Accuracy": round(acc, 4),
            "Precision": round(prec, 4),
            "Recall (Sensitivitas)": round(rec, 4),
            "F1-Score": round(f1, 4),
            "ROC-AUC": round(auc, 4),
            "PR-AUC": round(pr_auc, 4),
            "Confusion Matrix [TN, FP, FN, TP]": cm.ravel().tolist(),
        })

    df_test_comparison = pd.DataFrame(test_metrics).sort_values(by="ROC-AUC", ascending=False)

    # 3. Select Best Model (prioritizing high ROC-AUC & Recall)
    best_model_name = df_test_comparison.iloc[0]["Model"]
    best_pipeline = trained_pipelines[best_model_name]

    # 4. Save to Disk
    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    save_path = MODELS_DIR / "best_risk_classifier.joblib"
    joblib.dump(best_pipeline, save_path)
    print(f"\nModel Terbaik Terpilih: {best_model_name}")
    print(f"Disimpan ke: {save_path}")

    return best_pipeline, df_test_comparison, trained_pipelines


def predict_road_point_risk(
    pipeline: Pipeline,
    lat: float,
    lon: float,
    hour: int,
    road_type: str = "lokal",
    street_light_density: float = 0.5,
    dist_to_hotspot_m: float = 400.0,
    closest_hotspot_risk: float = 0.7,
    dist_to_police_m: float = 1200.0,
    day_of_week: int = 5,
) -> Dict[str, float]:
    """Single point inference function for FastAPI / Mobile integration.
    Returns probability of crime risk in [0.0 - 1.0].
    """
    input_data = pd.DataFrame([{
        "latitude": lat,
        "longitude": lon,
        "hour": hour,
        "is_night": 1 if (hour >= 18 or hour < 6) else 0,
        "day_of_week": day_of_week,
        "is_weekend": 1 if day_of_week in [5, 6] else 0,
        "hour_sin": np.sin(2 * np.pi * hour / 24.0),
        "hour_cos": np.cos(2 * np.pi * hour / 24.0),
        "street_light_density": street_light_density,
        "dist_to_hotspot_m": dist_to_hotspot_m,
        "closest_hotspot_risk": closest_hotspot_risk,
        "dist_to_police_m": dist_to_police_m,
        "road_type": road_type,
    }])

    prob = float(pipeline.predict_proba(input_data)[0, 1])
    is_risk = int(prob >= 0.5)

    return {
        "risk_probability": round(prob, 4),
        "is_high_risk": is_risk,
        "safety_level": "BAHAYA / RAWAN" if prob >= 0.65 else "WASPADA" if prob >= 0.40 else "AMAN",
    }
