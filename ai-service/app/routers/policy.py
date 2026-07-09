from fastapi import APIRouter

from app.ml.response_policy import response_policy
from app.schemas.policy import PolicyRecommendationRequest, PolicyRecommendationResponse

router = APIRouter(prefix="/api/v1/policy", tags=["Adaptive Response Policy"])


@router.post("/recommend-action", response_model=PolicyRecommendationResponse)
def recommend_action(request: PolicyRecommendationRequest) -> PolicyRecommendationResponse:
    """
    Recommends a response action (BLOCK_IP / DISABLE_USER / QUARANTINE /
    NOTIFY_ONLY / ESCALATE) for a detected threat using the offline-trained
    tabular Q-learning policy in app/ml/response_policy.py, along with the
    learned Q-value for every action so the caller can see the full ranking.
    """
    result = response_policy.recommend(request.threatType, request.severity, request.confidenceScore)
    return PolicyRecommendationResponse(**result)
