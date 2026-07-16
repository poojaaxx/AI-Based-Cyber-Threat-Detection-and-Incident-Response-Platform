"""
Loads the trained attention-LSTM ("Model B") + its NSL-KDD preprocessing
artifacts, and exposes a predict_sequence() that turns a list of raw
KddConnectionRecord-shaped dicts into a classification, exactly mirroring how
model_loader.py's ThreatModel wraps the RandomForest ("Model A").
"""
import logging

import joblib
import numpy as np
import shap
import torch

from app.config import DATA_DIR
from app.ml.explainability import build_temporal_shap_factors
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
        self.feature_mean = None
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
        # Older preprocessing.joblib files (pre-SHAP) won't have this key -
        # fall back to None, which _shap_factors() below treats as "SHAP
        # unavailable" rather than crashing.
        self.feature_mean = preprocessing.get("feature_mean")

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

        try:
            shap_result = self._shap_factors(rows_scaled, best_idx, records[-1])
        except Exception as exc:
            # Additive explanation feature - a SHAP computation failure must not
            # break the classification itself (unlike Model A's TreeExplainer,
            # KernelExplainer here does iterative numerical fitting and is less
            # bulletproof against edge cases).
            logger.warning(f"SHAP explanation for temporal detector failed, returning empty list: {exc}")
            shap_result = {"factors": [], "base_value": 0.0, "other_contribution": 0.0, "other_count": 0}

        return {
            "category": category,
            "confidence": float(probabilities[best_idx]),
            "class_probabilities": class_probabilities,
            "attention_weights": [float(w) for w in attn_weights],
            "shap_factors": shap_result["factors"],
            "shap_base_value": shap_result["base_value"],
            "shap_other_contribution": shap_result["other_contribution"],
            "shap_other_feature_count": shap_result["other_count"],
        }

    def _shap_factors(self, window_scaled: np.ndarray, predicted_class_idx: int, most_recent_raw_record: dict) -> dict:
        """window_scaled: (window, n_features) SCALED array - the exact input fed
        to the model for this request. most_recent_raw_record: the raw (un-scaled,
        un-encoded) dict for the window's last event, used only for display values.

        Uses shap.KernelExplainer, masking each of the window's raw features as
        one Shapley "player" replaced uniformly across all timesteps at once
        (not a per-timestep breakdown). This was chosen after empirically
        testing shap.DeepExplainer (doesn't support nn.LSTM at all - fails its
        own additivity assertion by orders of magnitude) and
        shap.GradientExplainer (runs, but ~15-20% non-additive on this model);
        this masking-based KernelExplainer approach was verified additive to
        within floating-point precision. See ai-service/app/ml/explainability.py's
        build_temporal_shap_factors() for the output formatting.

        Returns base_value (the model's output with every feature masked to its
        dataset mean) and other_contribution/other_count (the summed SHAP value
        and count of the features NOT in the returned top-N factors) alongside
        the factors themselves, so a frontend waterfall chart can show
        base_value -> top factors -> "N other features" -> the real prediction
        without the displayed steps silently failing to add up.
        """
        empty = {"factors": [], "base_value": 0.0, "other_contribution": 0.0, "other_count": 0}
        if self.feature_mean is None:
            return empty

        n_features = window_scaled.shape[1]
        feature_mean = self.feature_mean

        def masked_predict(mask_batch: np.ndarray) -> np.ndarray:
            n = mask_batch.shape[0]
            real = np.tile(window_scaled[None, :, :], (n, 1, 1))
            baseline = np.tile(feature_mean[None, None, :], (n, self.window, 1))
            mask = mask_batch[:, None, :]
            x = mask * real + (1 - mask) * baseline
            with torch.no_grad():
                logits, _ = self.model(torch.from_numpy(x.astype(np.float32)))
                return torch.softmax(logits, dim=1).numpy()

        explainer = shap.KernelExplainer(masked_predict, np.zeros((1, n_features)))
        shap_values = explainer.shap_values(np.ones((1, n_features)), nsamples="auto", silent=True)
        shap_row = np.array(shap_values)[0, :, predicted_class_idx]
        base_value = float(masked_predict(np.zeros((1, n_features)))[0, predicted_class_idx])

        raw_values = {name: most_recent_raw_record.get(name, 0) for name in self.feature_names}
        top_n = 5
        factors = build_temporal_shap_factors(shap_row, self.feature_names, raw_values, top_n=top_n)

        ranked = sorted(zip(self.feature_names, shap_row), key=lambda x: abs(x[1]), reverse=True)
        other_contribution = float(sum(v for _, v in ranked[top_n:]))
        other_count = max(0, len(ranked) - top_n)

        return {
            "factors": factors,
            "base_value": base_value,
            "other_contribution": other_contribution,
            "other_count": other_count,
        }


temporal_threat_model = TemporalThreatModel()
