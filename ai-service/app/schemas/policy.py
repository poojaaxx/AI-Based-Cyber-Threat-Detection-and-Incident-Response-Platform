from typing import Dict

from pydantic import BaseModel


class PolicyRecommendationRequest(BaseModel):
    threatType: str
    severity: str
    confidenceScore: float


class PolicyRecommendationResponse(BaseModel):
    state: str
    recommendedAction: str
    qValues: Dict[str, float]
