"""
Loads the trained attention-LSTM ("Model B") + its NSL-KDD preprocessing
artifacts, and exposes a predict_sequence() that turns a list of raw
KddConnectionRecord-shaped dicts into a classification, exactly mirroring how
model_loader.py's ThreatModel wraps the RandomForest ("Model A").
"""
import logging

import joblib
import numpy as np
import torch

from app.config import DATA_DIR
from app.ml.kdd_pipeline import KDD_PROCESSED_DIR, build_and_save
from app.ml.lstm_attention_model import LSTMAttentionClassifier
from app.ml.train_lstm import LSTM_MODEL_PATH, train_and_save

logger = logging.getLogger("ai-service")


class TemporalThreatModel:
    def __init__(self):
        self.model = None
        self.feature_names = None
        self.categorical_encoders = None
        self.scaler = None
        self.label_encoder = None
        self.window = None
        self.unavailable_reason = None
        try:
            self.load()
        except Exception as exc:
            # Deliberately non-fatal: this is a module-level singleton
            # constructed at import time (see bottom of this file), so an
            # unhandled exception here would crash the entire ai-service on
            # boot - including the unrelated, working RandomForest /predict
            # route - over a problem with only the experimental temporal
            # detector (e.g. the NSL-KDD dataset not being present in a
            # deployment whose Docker build context doesn't include it).
            # predict_sequence() below raises a clear, catchable error for
            # just the /predict/temporal route instead.
            self.unavailable_reason = str(exc)
            logger.error(f"Attention-LSTM temporal detector unavailable, /predict/temporal will return 503: {exc}")

    def load(self):
        preprocessing_path = KDD_PROCESSED_DIR / "preprocessing.joblib"
        if not preprocessing_path.exists():
            logger.info("No processed NSL-KDD data found - building it now (first boot)...")
            build_and_save()

        if not LSTM_MODEL_PATH.exists():
            logger.info("No trained attention-LSTM model found - training a fresh one now (first boot)...")
            train_and_save()

        preprocessing = joblib.load(preprocessing_path)
        self.feature_names = preprocessing["feature_names"]
        self.categorical_encoders = preprocessing["categorical_encoders"]
        self.scaler = preprocessing["scaler"]
        self.label_encoder = preprocessing["label_encoder"]
        self.window = preprocessing["window"]

        checkpoint = torch.load(LSTM_MODEL_PATH, weights_only=False)
        self.model = LSTMAttentionClassifier(
            input_size=checkpoint["input_size"],
            hidden_size=checkpoint["hidden_size"],
            num_classes=checkpoint["num_classes"],
        )
        self.model.load_state_dict(checkpoint["state_dict"])
        self.model.eval()
        logger.info("Attention-LSTM temporal detection model loaded successfully.")

    def _encode_category(self, column: str, value: str) -> int:
        encoder = self.categorical_encoders[column]
        value = str(value)
        if value not in encoder.classes_:
            logger.warning(f"Unseen '{column}' value '{value}' at inference time - falling back to '{encoder.classes_[0]}'")
            value = encoder.classes_[0]
        return int(encoder.transform([value])[0])

    def _record_to_row(self, record: dict) -> np.ndarray:
        row = []
        for name in self.feature_names:
            value = record.get(name, 0)
            if name in self.categorical_encoders:
                row.append(self._encode_category(name, value))
            else:
                row.append(float(value))
        return np.array(row, dtype=np.float32)

    def predict_sequence(self, records: list[dict]) -> dict:
        if self.model is None:
            raise RuntimeError(
                "Attention-LSTM temporal detector is not available in this deployment "
                f"({self.unavailable_reason})."
            )
        records = list(records)
        if len(records) < self.window:
            pad = [records[0]] * (self.window - len(records))
            records = pad + records
        records = records[-self.window:]

        rows = np.stack([self._record_to_row(r) for r in records])  # (window, n_features)
        rows_scaled = self.scaler.transform(rows).astype(np.float32)
        x = torch.from_numpy(rows_scaled).unsqueeze(0)  # (1, window, n_features)

        with torch.no_grad():
            logits, attn_weights = self.model(x)
            probabilities = torch.softmax(logits, dim=1).squeeze(0).numpy()
            attn_weights = attn_weights.squeeze(0).numpy()

        best_idx = int(probabilities.argmax())
        category = self.label_encoder.classes_[best_idx]
        class_probabilities = {
            cls: float(probabilities[i]) for i, cls in enumerate(self.label_encoder.classes_)
        }

        return {
            "category": category,
            "confidence": float(probabilities[best_idx]),
            "class_probabilities": class_probabilities,
            "attention_weights": [float(w) for w in attn_weights],
        }


temporal_threat_model = TemporalThreatModel()
