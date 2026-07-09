from typing import Optional, Dict, Any, List
from pydantic import BaseModel, Field


class ThreatDetectionRequest(BaseModel):
    sourceIp: Optional[str] = None
    destinationIp: Optional[str] = None
    sourcePort: Optional[int] = Field(default=0)
    destinationPort: Optional[int] = Field(default=0)
    protocol: Optional[str] = Field(default="TCP")
    bytesTransferred: Optional[int] = Field(default=0)
    packetCount: Optional[int] = Field(default=0)
    durationMs: Optional[int] = Field(default=0)
    failedLogins: Optional[int] = Field(default=0)
    flagged: Optional[bool] = Field(default=False)
    extraFeatures: Optional[Dict[str, Any]] = None


class ContributingFactor(BaseModel):
    feature: str
    value: str
    importance: float
    description: str


class PredictionResponse(BaseModel):
    threatType: str
    severity: str
    confidenceScore: float
    recommendedAction: str
    explanation: str
    riskScore: float = 0.0
    reasoning: str = ""
    contributingFactors: List[ContributingFactor] = Field(default_factory=list)
    # Phase 3 addition: per-instance SHAP explanation (see app/ml/explainability.py
    # build_shap_factors()). Additive/optional so existing clients that only read
    # contributingFactors are unaffected.
    shapExplanation: List[ContributingFactor] = Field(default_factory=list)
