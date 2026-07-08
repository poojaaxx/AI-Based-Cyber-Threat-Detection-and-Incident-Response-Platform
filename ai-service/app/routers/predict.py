from fastapi import APIRouter

from app.ml.model_loader import threat_model
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
