from fastapi import APIRouter, HTTPException

from app.ml.explainability import build_attention_explanation
from app.ml.lstm_model_loader import temporal_threat_model
from app.ml.model_loader import threat_model
from app.schemas.temporal import TemporalPredictionRequest, TemporalPredictionResponse
from app.schemas.threat import PredictionResponse, ThreatDetectionRequest

router = APIRouter(prefix="/api/v1", tags=["Threat Detection"])


@router.post("/predict", response_model=PredictionResponse)
def predict_threat(request: ThreatDetectionRequest) -> PredictionResponse:
    """
    Classifies a network/event feature set into one of the supported threat
    categories (malware, DDoS, SQL injection, XSS, brute force, port scan,
    phishing, ransomware, insider threat) using the trained RandomForest model.
    """
    return threat_model.predict(request)


@router.post("/predict/temporal", response_model=TemporalPredictionResponse)
def predict_temporal(request: TemporalPredictionRequest) -> TemporalPredictionResponse:
    """
    Classifies a short sequence (window) of NSL-KDD-style connection records
    into Normal/DoS/Probe/R2L/U2R using the attention-LSTM temporal detector
    ("Model B"). Independent of, and not a replacement for, the /predict
    RandomForest route above - see TemporalPredictionResponse.note.
    """
    try:
        result = temporal_threat_model.predict_sequence([r.model_dump() for r in request.records])
    except RuntimeError as exc:
        raise HTTPException(status_code=503, detail=str(exc))
    return TemporalPredictionResponse(
        threatCategory=result["category"],
        confidenceScore=round(result["confidence"], 4),
        classProbabilities={k: round(v, 4) for k, v in result["class_probabilities"].items()},
        attentionWeights=[round(w, 4) for w in result["attention_weights"]],
        attentionExplanation=build_attention_explanation(result["attention_weights"]),
        shapExplanation=result["shap_factors"],
        shapBaseValue=round(result["shap_base_value"], 4),
        shapOtherContribution=round(result["shap_other_contribution"], 4),
        shapOtherFeatureCount=result["shap_other_feature_count"],
    )
