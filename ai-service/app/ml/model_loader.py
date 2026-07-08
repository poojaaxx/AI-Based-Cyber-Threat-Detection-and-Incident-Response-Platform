import logging

import joblib

from app.config import (
    CONFIDENCE_THRESHOLD,
    LABEL_ENCODER_PATH,
    MODEL_PATH,
    PROTOCOL_ENCODER_PATH,
    SCALER_PATH,
)
from app.ml.constants import BASE_SEVERITY, EXPLANATIONS, RECOMMENDED_ACTIONS
from app.ml.explainability import build_contributing_factors, build_reasoning, compute_risk_score
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
        self.load()

    def load(self):
        if not MODEL_PATH.exists():
            logger.info("No trained model found - training a fresh model now (first boot)...")
            train_and_save()

        self.model = joblib.load(MODEL_PATH)
        self.protocol_encoder = joblib.load(PROTOCOL_ENCODER_PATH)
        self.label_encoder = joblib.load(LABEL_ENCODER_PATH)
        self.scaler = joblib.load(SCALER_PATH)
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

        return PredictionResponse(
            threatType=predicted_label if predicted_label != "BENIGN" else "UNKNOWN",
            severity=severity,
            confidenceScore=round(confidence, 4),
            recommendedAction=RECOMMENDED_ACTIONS.get(predicted_label, "Continue monitoring."),
            explanation=EXPLANATIONS.get(predicted_label, "No significant anomaly detected."),
            riskScore=risk_score,
            reasoning=reasoning,
            contributingFactors=contributing_factors,
        )

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
