import logging

import joblib
import shap

from app.config import (
    CONFIDENCE_THRESHOLD,
    LABEL_ENCODER_PATH,
    MODEL_PATH,
    PROTOCOL_ENCODER_PATH,
    SCALER_PATH,
)
from app.ml.constants import BASE_SEVERITY, EXPLANATIONS, RECOMMENDED_ACTIONS
from app.ml.explainability import (
    FEATURE_ORDER,
    build_contributing_factors,
    build_reasoning,
    build_shap_factors,
    compute_risk_score,
)
from app.ml.feature_engineering import request_to_feature_row
from app.ml.train_model import train_and_save
from app.schemas.threat import PredictionResponse, ThreatDetectionRequest

logger = logging.getLogger("ai-service")


class ThreatModel:
    def __init__(self):
        self.model = None
        self.protocol_encoder = None
        self.label_encoder = None
        self.scaler = None
        self.shap_explainer = None
        self.load()

    def load(self):
        if not MODEL_PATH.exists():
            logger.info("No trained model found - training a fresh model now (first boot)...")
            train_and_save()

        self.model = joblib.load(MODEL_PATH)
        self.protocol_encoder = joblib.load(PROTOCOL_ENCODER_PATH)
        self.label_encoder = joblib.load(LABEL_ENCODER_PATH)
        self.scaler = joblib.load(SCALER_PATH)
        # Built once at load time (not per-request) since constructing a
        # TreeExplainer walks the whole forest - reused for every /predict call.
        self.shap_explainer = shap.TreeExplainer(self.model)
        logger.info("Threat detection model loaded successfully.")

    def predict(self, request: ThreatDetectionRequest) -> PredictionResponse:
        features = request_to_feature_row(request, self.protocol_encoder)
        features_scaled = self.scaler.transform(features)

        probabilities = self.model.predict_proba(features_scaled)[0]
        best_idx = int(probabilities.argmax())
        confidence = float(probabilities[best_idx])
        predicted_label = self.label_encoder.inverse_transform([best_idx])[0]

        if confidence < CONFIDENCE_THRESHOLD:
            predicted_label = "BENIGN"

        severity = self._adjust_severity(BASE_SEVERITY.get(predicted_label, "LOW"), confidence)

        contributing_factors = build_contributing_factors(
            request, request.protocol or "TCP", self.model.feature_importances_
        )
        risk_score = compute_risk_score(severity, confidence)
        reasoning = build_reasoning(predicted_label, contributing_factors)
        shap_factors = self._shap_factors(features_scaled, request, best_idx)

        return PredictionResponse(
            threatType=predicted_label if predicted_label != "BENIGN" else "UNKNOWN",
            severity=severity,
            confidenceScore=round(confidence, 4),
            recommendedAction=RECOMMENDED_ACTIONS.get(predicted_label, "Continue monitoring."),
            explanation=EXPLANATIONS.get(predicted_label, "No significant anomaly detected."),
            riskScore=risk_score,
            reasoning=reasoning,
            contributingFactors=contributing_factors,
            shapExplanation=shap_factors,
        )

    def _shap_factors(self, features_scaled, request: ThreatDetectionRequest, predicted_class_idx: int):
        shap_raw = self.shap_explainer.shap_values(features_scaled)
        # shap>=0.45 returns one ndarray shaped (n_samples, n_features, n_classes);
        # older versions return a list of per-class (n_samples, n_features) arrays.
        if isinstance(shap_raw, list):
            shap_row = shap_raw[predicted_class_idx][0]
        else:
            shap_row = shap_raw[0, :, predicted_class_idx]

        raw_values = {
            "sourcePort": request.sourcePort or 0,
            "destinationPort": request.destinationPort or 0,
            "protocolEncoded": request.protocol or "TCP",
            "bytesTransferred": request.bytesTransferred or 0,
            "packetCount": request.packetCount or 0,
            "durationMs": request.durationMs or 0,
            "failedLogins": request.failedLogins or 0,
            "flaggedInt": "Yes" if request.flagged else "No",
        }
        return build_shap_factors(shap_row, FEATURE_ORDER, raw_values)

    @staticmethod
    def _adjust_severity(base_severity: str, confidence: float) -> str:
        order = ["LOW", "MEDIUM", "HIGH", "CRITICAL"]
        idx = order.index(base_severity)
        if confidence < 0.5 and idx > 0:
            idx -= 1
        elif confidence > 0.9 and idx < len(order) - 1:
            idx = min(idx + 1, len(order) - 1)
        return order[idx]


threat_model = ThreatModel()
