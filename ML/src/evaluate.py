"""Model evaluation module for JagaDia.
Calculates key metrics, precision-recall curve, ROC curve, and risk threshold tuning.
"""

from pathlib import Path
import joblib
import pandas as pd
import numpy as np
from sklearn.metrics import (
    classification_report,
    roc_auc_score,
    average_precision_score,
    confusion_matrix,
    precision_recall_curve,
)
from .config import MODELS_DIR, PROCESSED_DATA_DIR, TARGET_COLUMN

def evaluate_model(model_path: Path, test_data_path: Path):
    """Evaluate model performance on test set and print diagnostic metrics."""
    if not model_path.exists():
        raise FileNotFoundError(f"Model file not found at {model_path}")
    if not test_data_path.exists():
        raise FileNotFoundError(f"Test data file not found at {test_data_path}")

    model = joblib.load(model_path)
    df_test = pd.read_csv(test_data_path)

    y_test = df_test["target"]
    X_test = df_test.drop(columns=["target"])

    probs = model.predict_proba(X_test)[:, 1]
    preds = model.predict(X_test)

    print("=" * 50)
    print(f"EVALUATION REPORT: {model_path.name}")
    print("=" * 50)

    print("\n[1] Classification Report (Default Threshold = 0.5):")
    print(classification_report(y_test, preds, target_names=["Aman", "Rawan"]))

    roc_auc = roc_auc_score(y_test, probs)
    pr_auc = average_precision_score(y_test, probs)
    print(f"[2] ROC-AUC Score: {roc_auc:.4f}")
    print(f"[3] PR-AUC (Average Precision): {pr_auc:.4f}")

    cm = confusion_matrix(y_test, preds)
    tn, fp, fn, tp = cm.ravel()
    print(f"\n[4] Confusion Matrix:")
    print(f"    True Negative (Aman terdeteksi Aman) : {tn}")
    print(f"    False Positive (Alarm palsu/Aman dinilai Rawan): {fp}")
    print(f"    False Negative (BAHAYA: Rawan lolos tak terdeteksi): {fn}")
    print(f"    True Positive (Rawan terdeteksi Rawan): {tp}")

    # Optimal threshold suggestion (e.g. prioritizing high recall to protect users)
    precisions, recalls, thresholds = precision_recall_curve(y_test, probs)
    # Threshold where recall >= 0.85
    idx_target_recall = np.where(recalls >= 0.85)[0]
    if len(idx_target_recall) > 0:
        recommended_idx = idx_target_recall[-1]
        recommended_thresh = thresholds[recommended_idx] if recommended_idx < len(thresholds) else 0.5
        print(f"\n[5] Safety Recommendation:")
        print(f"    Untuk aplikasi keselamatan, prioritaskan Recall tinggi.")
        print(f"    Rekomendasi Ambang Batas (Threshold) untuk Alert: ~{recommended_thresh:.2f}")

    return {
        "roc_auc": roc_auc,
        "pr_auc": pr_auc,
        "confusion_matrix": cm,
    }

if __name__ == "__main__":
    latest_models = list(MODELS_DIR.glob("*.joblib"))
    test_csv = PROCESSED_DATA_DIR / "test_processed.csv"
    if latest_models and test_csv.exists():
        evaluate_model(latest_models[0], test_csv)
    else:
        print("Model or test dataset not yet generated. Run train_baseline.py first.")
