"""
Compares a single-shot classifier against the attention-LSTM temporal
detector ("Model B") on the same NSL-KDD test set, and prints a
metrics + latency table.

Why not literally reuse the deployed /predict RandomForest ("Model A")?
Model A is trained on a completely different, synthetic 8-feature schema
(sourcePort/destinationPort/bytesTransferred/...; see app/ml/dataset.py) that
has no correspondence to NSL-KDD's 36-feature connection records - it is
architecturally incapable of taking NSL-KDD input, so it cannot be evaluated
on this test set. To get a fair, apples-to-apples "single-shot vs temporal"
comparison that isolates exactly the variable the base paper's attention-LSTM
claims to improve on, this script trains a single-shot RandomForest baseline
on the SAME NSL-KDD features Model B uses, but with no sequence/window
context (only the most recent event in each window - i.e. what a
non-temporal classifier would see). Model B gets the full 10-event window.

Run with:  python -m app.ml.compare_models
"""
import time

import numpy as np
import torch
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import accuracy_score, precision_recall_fscore_support

from app.ml.kdd_pipeline import KDD_PROCESSED_DIR, LABEL_CATEGORIES, build_and_save
from app.ml.lstm_attention_model import LSTMAttentionClassifier
from app.ml.train_lstm import LSTM_MODEL_PATH, train_and_save


def _load_processed_data():
    if not (KDD_PROCESSED_DIR / "X_train.npy").exists():
        build_and_save()
    X_train = np.load(KDD_PROCESSED_DIR / "X_train.npy")
    y_train = np.load(KDD_PROCESSED_DIR / "y_train.npy")
    X_test = np.load(KDD_PROCESSED_DIR / "X_test.npy")
    y_test = np.load(KDD_PROCESSED_DIR / "y_test.npy")
    return X_train, y_train, X_test, y_test


def _evaluate(name: str, y_true, y_pred, elapsed_seconds: float) -> dict:
    acc = accuracy_score(y_true, y_pred)
    precision, recall, f1, _ = precision_recall_fscore_support(y_true, y_pred, average="macro", zero_division=0)
    per_sample_ms = elapsed_seconds / len(y_true) * 1000
    return {
        "name": name,
        "accuracy": acc,
        "precision": precision,
        "recall": recall,
        "f1": f1,
        "total_ms": elapsed_seconds * 1000,
        "per_sample_ms": per_sample_ms,
    }


def run_comparison():
    X_train, y_train, X_test, y_test = _load_processed_data()

    # --- Single-shot baseline: RandomForest on only the last event of each window ---
    X_train_last = X_train[:, -1, :]
    X_test_last = X_test[:, -1, :]

    print("Training single-shot RandomForest baseline (no temporal context)...")
    rf = RandomForestClassifier(n_estimators=200, max_depth=16, random_state=42, n_jobs=-1, class_weight="balanced")
    rf.fit(X_train_last, y_train)

    start = time.perf_counter()
    rf_pred = rf.predict(X_test_last)
    rf_elapsed = time.perf_counter() - start
    rf_metrics = _evaluate("Single-shot RandomForest (last event only)", y_test, rf_pred, rf_elapsed)

    # --- Attention-LSTM: full 10-event window ---
    if not LSTM_MODEL_PATH.exists():
        train_and_save()
    checkpoint = torch.load(LSTM_MODEL_PATH, weights_only=False)
    lstm = LSTMAttentionClassifier(
        input_size=checkpoint["input_size"],
        hidden_size=checkpoint["hidden_size"],
        num_classes=checkpoint["num_classes"],
    )
    lstm.load_state_dict(checkpoint["state_dict"])
    lstm.eval()

    with torch.no_grad():
        start = time.perf_counter()
        logits, _ = lstm(torch.from_numpy(X_test))
        lstm_elapsed = time.perf_counter() - start
        lstm_pred = logits.argmax(dim=1).numpy()
    lstm_metrics = _evaluate("Attention-LSTM (10-event window)", y_test, lstm_pred, lstm_elapsed)

    header = f"{'Model':<40}{'Acc':>8}{'Prec':>8}{'Recall':>8}{'F1':>8}{'Total ms':>12}{'ms/sample':>12}"
    print("\n" + header)
    print("-" * len(header))
    for m in (rf_metrics, lstm_metrics):
        print(f"{m['name']:<40}{m['accuracy']:>8.4f}{m['precision']:>8.4f}{m['recall']:>8.4f}"
              f"{m['f1']:>8.4f}{m['total_ms']:>12.2f}{m['per_sample_ms']:>12.4f}")

    print(f"\nLabel categories: {LABEL_CATEGORIES}")
    return {"single_shot_rf": rf_metrics, "attention_lstm": lstm_metrics}


if __name__ == "__main__":
    run_comparison()
