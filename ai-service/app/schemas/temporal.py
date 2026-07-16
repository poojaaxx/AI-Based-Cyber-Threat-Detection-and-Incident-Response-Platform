"""
Request/response schemas for the attention-LSTM temporal detector
(POST /predict/temporal). This is a deliberately separate schema from
schemas/threat.py's ThreatDetectionRequest/PredictionResponse - Model B
operates on raw NSL-KDD connection-record fields and a 5-class
Normal/DoS/Probe/R2L/U2R taxonomy, not Model A's 8-feature/10-threat-type
schema, so reusing those types would be misleading.
"""
from typing import Dict, List, Optional

from pydantic import BaseModel, Field

from app.schemas.threat import ContributingFactor


class KddConnectionRecord(BaseModel):
    """One NSL-KDD-style connection record. Field names/defaults mirror the
    dataset's raw columns (dataset/KDDTrain.parquet / KDDTest.parquet)."""

    duration: float = 0
    protocol_type: str = "tcp"
    service: str = "http"
    flag: str = "SF"
    src_bytes: float = 0
    dst_bytes: float = 0
    land: int = 0
    wrong_fragment: int = 0
    urgent: int = 0
    hot: int = 0
    num_failed_logins: int = 0
    num_compromised: int = 0
    root_shell: int = 0
    su_attempted: int = 0
    num_root: int = 0
    num_file_creations: int = 0
    num_shells: int = 0
    num_access_files: int = 0
    num_outbound_cmds: int = 0
    is_host_login: int = 0
    is_guest_login: int = 0
    count: int = 0
    serror_rate: float = 0
    srv_serror_rate: float = 0
    srv_rerror_rate: float = 0
    same_srv_rate: float = 1.0
    diff_srv_rate: float = 0
    dst_host_count: int = 0
    dst_host_same_srv_rate: float = 1.0
    dst_host_diff_srv_rate: float = 0
    dst_host_same_src_port_rate: float = 0
    dst_host_srv_diff_host_rate: float = 0
    dst_host_serror_rate: float = 0
    dst_host_srv_serror_rate: float = 0
    dst_host_rerror_rate: float = 0
    dst_host_srv_rerror_rate: float = 0


class TemporalPredictionRequest(BaseModel):
    """A sequence of up to `window` (10) consecutive connection records - the
    most recent event should be last. If fewer than `window` records are
    supplied (e.g. for a quick manual test with a single record), the
    sequence is left-padded by repeating the first record so the model still
    receives a fixed-length window; the real deployment use-case is a rolling
    buffer of the last 10 connection records for a session."""

    records: List[KddConnectionRecord] = Field(..., min_length=1, max_length=10)


class TemporalPredictionResponse(BaseModel):
    threatCategory: str
    confidenceScore: float
    classProbabilities: Dict[str, float]
    attentionWeights: List[float]
    modelVersion: str = "attention-lstm-v1"
    note: str = (
        "Model B (attention-LSTM, trained on NSL-KDD) uses a different label "
        "taxonomy (Normal/DoS/Probe/R2L/U2R) than the primary /predict "
        "RandomForest model (Model A, trained on synthetic data, 10 threat "
        "types). The two are independent, complementary detectors, not "
        "interchangeable."
    )
    attentionExplanation: Optional[List[Dict[str, float]]] = None
    # Per-instance SHAP explanation via shap.KernelExplainer (see
    # lstm_model_loader.py's _shap_factors()). Additive/optional - empty list
    # if the deployment predates this or the SHAP computation itself failed.
    shapExplanation: List[ContributingFactor] = Field(default_factory=list)
    # baseValue + shapExplanation + otherContribution == the predicted class's
    # probability (confidenceScore) - lets a frontend waterfall chart show a
    # mathematically honest running total instead of just the top factors.
    shapBaseValue: float = 0.0
    shapOtherContribution: float = 0.0
    shapOtherFeatureCount: int = 0
