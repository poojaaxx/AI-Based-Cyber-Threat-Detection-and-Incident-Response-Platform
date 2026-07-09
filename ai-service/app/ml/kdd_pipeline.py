"""
NSL-KDD ingestion pipeline for the attention-LSTM temporal detector ("Model B").

This is a SEPARATE data/feature pipeline from app/ml/dataset.py + train_model.py
(the original synthetic-feature RandomForest, "Model A"). Model A's 8-feature
schema (sourcePort/destinationPort/bytesTransferred/...) and Model B's NSL-KDD
schema (protocol_type/service/flag/duration/...) are not related, on purpose:
Model A stays exactly as it was, and Model B is built independently on real
NSL-KDD data to genuinely exercise a temporal detector, per the base paper.

Honesty note on the "temporal" framing
---------------------------------------
NSL-KDD records are independent, already-aggregated connection summaries - the
dataset has no timestamps and no ground-truth ordering of network flows. It is
NOT natively sequential data. Zhang et al. assume a real packet/flow stream
with genuine temporal structure for their attention-LSTM detector; we do not
have that here. As a documented, explicit simplification, this pipeline treats
the dataset's existing row order as a stand-in "session" ordering and groups
every WINDOW consecutive rows into one sequence, using the label of the LAST
row in the window as the sequence's target (i.e. "classify the most recent
event using the preceding W-1 events as context"). This lets us build and
train a genuine LSTM+attention sequence model end-to-end, but the "sequence"
is a synthetic windowing convenience, not reconstructed real traffic order.
Anyone extending this further should replace the windowing with true
timestamped/session-grouped flow data if real temporal fidelity is required.

Run with:  python -m app.ml.kdd_pipeline
"""
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.preprocessing import LabelEncoder, StandardScaler

from app.config import DATA_DIR

WINDOW = 10

RAW_DATA_DIR = Path(__file__).resolve().parents[3] / "dataset"
KDD_TRAIN_PATH = RAW_DATA_DIR / "KDDTrain.parquet"
KDD_TEST_PATH = RAW_DATA_DIR / "KDDTest.parquet"

KDD_PROCESSED_DIR = DATA_DIR / "kdd_processed"

CATEGORICAL_COLUMNS = ["protocol_type", "service", "flag"]
NON_FEATURE_COLUMNS = ["class", "classnum"]

# 5-class attack-category taxonomy used throughout the base paper's evaluation.
LABEL_CATEGORIES = ["Normal", "DoS", "Probe", "R2L", "U2R"]

# Standard NSL-KDD attack-name -> attack-category mapping (widely used in NSL-KDD
# literature/tooling). classnum in the raw file is NOT this 5-class label - it is
# a finer-grained per-attack numeric id - so the mapping below is applied to the
# "class" column explicitly rather than reusing classnum.
ATTACK_CATEGORY_MAP = {
    "normal": "Normal",
    # DoS
    "back": "DoS", "land": "DoS", "neptune": "DoS", "pod": "DoS", "smurf": "DoS",
    "teardrop": "DoS", "apache2": "DoS", "udpstorm": "DoS", "processtable": "DoS",
    "mailbomb": "DoS",
    # Probe
    "satan": "Probe", "ipsweep": "Probe", "nmap": "Probe", "portsweep": "Probe",
    "mscan": "Probe", "saint": "Probe",
    # R2L
    "guess_passwd": "R2L", "ftp_write": "R2L", "imap": "R2L", "phf": "R2L",
    "multihop": "R2L", "warezmaster": "R2L", "warezclient": "R2L", "spy": "R2L",
    "xlock": "R2L", "xsnoop": "R2L", "snmpguess": "R2L", "snmpgetattack": "R2L",
    "httptunnel": "R2L", "sendmail": "R2L", "named": "R2L", "worm": "R2L",
    # U2R
    "buffer_overflow": "U2R", "loadmodule": "U2R", "rootkit": "U2R", "perl": "U2R",
    "sqlattack": "U2R", "xterm": "U2R", "ps": "U2R",
}


def _map_labels(df: pd.DataFrame) -> pd.Series:
    classes = df["class"].astype(str)
    mapped = classes.map(ATTACK_CATEGORY_MAP)
    unmapped = classes[mapped.isna()].unique()
    if len(unmapped) > 0:
        raise ValueError(f"Unmapped NSL-KDD attack classes found (add to ATTACK_CATEGORY_MAP): {unmapped}")
    return mapped


def _fit_categorical_encoders(train_df: pd.DataFrame, test_df: pd.DataFrame) -> dict:
    """Fit one LabelEncoder per categorical column on the union of train+test
    categories, so an unseen-at-fit-time category in test never crashes
    .transform() (defensive - in practice NSL-KDD's test categories are a
    subset of train's, but this keeps the pipeline robust either way)."""
    encoders = {}
    for col in CATEGORICAL_COLUMNS:
        combined = pd.concat([train_df[col].astype(str), test_df[col].astype(str)], axis=0)
        encoder = LabelEncoder()
        encoder.fit(combined)
        encoders[col] = encoder
    return encoders


def _build_feature_matrix(df: pd.DataFrame, encoders: dict) -> pd.DataFrame:
    out = df.copy()
    for col in CATEGORICAL_COLUMNS:
        out[col] = encoders[col].transform(out[col].astype(str))
    feature_columns = [c for c in df.columns if c not in NON_FEATURE_COLUMNS]
    return out[feature_columns].astype(np.float32)


def _windowize(features: np.ndarray, labels: np.ndarray, window: int) -> tuple:
    """Groups consecutive rows into non-overlapping windows of `window` rows.
    See the module docstring's "Honesty note" - this is a documented
    simplification since NSL-KDD rows have no real temporal/session order.
    The sequence's label is the label of its LAST row (classify the most
    recent event given the preceding rows as context); any leftover rows that
    don't fill a full window are dropped."""
    n_sequences = len(features) // window
    trimmed = n_sequences * window
    X = features[:trimmed].reshape(n_sequences, window, features.shape[1])
    y = labels[window - 1:trimmed:window]
    return X, y


def build_and_save() -> dict:
    if not KDD_TRAIN_PATH.exists() or not KDD_TEST_PATH.exists():
        raise FileNotFoundError(
            f"NSL-KDD parquet files not found. Expected: {KDD_TRAIN_PATH} and {KDD_TEST_PATH}"
        )

    print(f"Loading NSL-KDD data from {RAW_DATA_DIR} ...")
    train_df = pd.read_parquet(KDD_TRAIN_PATH)
    test_df = pd.read_parquet(KDD_TEST_PATH)
    print(f"Raw train shape: {train_df.shape}, raw test shape: {test_df.shape}")

    train_categories = _map_labels(train_df)
    test_categories = _map_labels(test_df)

    label_encoder = LabelEncoder()
    label_encoder.fit(LABEL_CATEGORIES)
    y_train_raw = label_encoder.transform(train_categories)
    y_test_raw = label_encoder.transform(test_categories)

    categorical_encoders = _fit_categorical_encoders(train_df, test_df)

    X_train_df = _build_feature_matrix(train_df, categorical_encoders)
    X_test_df = _build_feature_matrix(test_df, categorical_encoders)
    feature_names = list(X_train_df.columns)

    scaler = StandardScaler()
    X_train_scaled = scaler.fit_transform(X_train_df.values).astype(np.float32)
    X_test_scaled = scaler.transform(X_test_df.values).astype(np.float32)

    X_train_seq, y_train_seq = _windowize(X_train_scaled, y_train_raw, WINDOW)
    X_test_seq, y_test_seq = _windowize(X_test_scaled, y_test_raw, WINDOW)

    KDD_PROCESSED_DIR.mkdir(parents=True, exist_ok=True)
    np.save(KDD_PROCESSED_DIR / "X_train.npy", X_train_seq)
    np.save(KDD_PROCESSED_DIR / "y_train.npy", y_train_seq)
    np.save(KDD_PROCESSED_DIR / "X_test.npy", X_test_seq)
    np.save(KDD_PROCESSED_DIR / "y_test.npy", y_test_seq)
    joblib.dump(
        {
            "feature_names": feature_names,
            "categorical_encoders": categorical_encoders,
            "scaler": scaler,
            "label_encoder": label_encoder,
            "window": WINDOW,
            "label_categories": LABEL_CATEGORIES,
        },
        KDD_PROCESSED_DIR / "preprocessing.joblib",
    )

    # ---- Pass-criteria self-check ----
    print(f"\nTrain sequence tensor shape: {X_train_seq.shape}, labels: {y_train_seq.shape}")
    print(f"Test sequence tensor shape:  {X_test_seq.shape}, labels: {y_test_seq.shape}")

    assert X_train_seq.shape[0] > 0 and X_test_seq.shape[0] > 0, "Train/test tensors must be non-empty"

    train_class_counts = {
        label_encoder.classes_[i]: int((y_train_seq == i).sum()) for i in range(len(label_encoder.classes_))
    }
    print(f"\nTrain sequence label distribution: {train_class_counts}")
    missing = [cls for cls, count in train_class_counts.items() if count == 0]
    assert not missing, f"These label categories have 0 samples in the train sequences: {missing}"

    nan_count = int(np.isnan(X_train_seq).sum() + np.isnan(X_test_seq).sum())
    print(f"\nNaN count in final tensors: {nan_count}")
    assert nan_count == 0, "Final tensors contain NaNs"

    print(f"\nAll Phase 1 pass-criteria checks passed. Saved processed tensors to {KDD_PROCESSED_DIR}")
    return {
        "X_train_shape": X_train_seq.shape,
        "X_test_shape": X_test_seq.shape,
        "train_class_counts": train_class_counts,
    }


if __name__ == "__main__":
    build_and_save()
