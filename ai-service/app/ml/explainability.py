"""
Real, model-grounded explainability for the threat classifier.

Rather than static placeholder text, contributing factors are derived from the
trained RandomForest's own `feature_importances_` combined with the actual
submitted feature values, so "why was this flagged?" reflects what the model
actually learned and what the specific request contained.

Phase 3 extension: build_contributing_factors() below ranks features by the
model's *global* feature_importances_ - the same ranking every request gets,
regardless of input. build_shap_factors() adds a genuine per-instance
explanation (SHAP, for the RandomForest) and build_attention_explanation()
adds one for the attention-LSTM (Model B), using the attention weights the
model itself produced for that specific input sequence. Both new functions
are additive - build_contributing_factors()'s behavior/shape is unchanged.
"""
from typing import Any, Dict, List

from app.ml.constants import EXPLANATIONS

FEATURE_ORDER = [
    "sourcePort", "destinationPort", "protocolEncoded",
    "bytesTransferred", "packetCount", "durationMs", "failedLogins", "flaggedInt",
]

FEATURE_LABELS = {
    "sourcePort": "Source Port",
    "destinationPort": "Destination Port",
    "protocolEncoded": "Protocol",
    "bytesTransferred": "Bytes Transferred",
    "packetCount": "Packet Count",
    "durationMs": "Duration (ms)",
    "failedLogins": "Failed Login Attempts",
    "flaggedInt": "Pre-flagged by Perimeter Defenses",
}

WELL_KNOWN_PORTS = {
    22: "SSH", 21: "FTP", 23: "Telnet", 25: "SMTP", 53: "DNS", 80: "HTTP",
    443: "HTTPS", 445: "SMB", 587: "SMTP (submission)", 3306: "MySQL",
    3389: "RDP", 4444: "commonly used by malware C2/Metasploit", 8080: "HTTP-alt",
}

SEVERITY_WEIGHT = {"LOW": 25, "MEDIUM": 50, "HIGH": 75, "CRITICAL": 100}


def _port_hint(port) -> str:
    if port in WELL_KNOWN_PORTS:
        return f" ({WELL_KNOWN_PORTS[port]})"
    return ""


def _describe_feature(feature: str, raw_value: Any) -> str:
    if feature == "failedLogins":
        v = raw_value or 0
        return (f"{v} failed login attempts — consistent with credential brute-forcing."
                if v > 5 else f"{v} failed login attempts observed.")
    if feature == "packetCount":
        v = raw_value or 0
        return (f"Packet count of {v} is unusually high, suggesting a volumetric/flood pattern."
                if v > 1000 else f"Packet count of {v} is within a typical range.")
    if feature == "bytesTransferred":
        v = raw_value or 0
        return (f"{v} bytes transferred — abnormally large, consistent with bulk data movement or exfiltration."
                if v > 100000 else f"{v} bytes transferred, a modest transfer size.")
    if feature == "durationMs":
        v = raw_value or 0
        if v < 300:
            return f"Session duration of {v} ms is unusually short (rapid burst activity)."
        if v > 8000:
            return f"Session duration of {v} ms is unusually long (sustained connection)."
        return f"Session duration of {v} ms is typical."
    if feature == "destinationPort":
        return f"Destination port {raw_value}{_port_hint(raw_value)}."
    if feature == "sourcePort":
        return f"Source port {raw_value}."
    if feature == "protocolEncoded":
        return "Protocol used for this connection."
    if feature == "flaggedInt":
        return ("Already flagged by an upstream firewall/IDS sensor before AI analysis."
                if raw_value else "Not pre-flagged by perimeter defenses — detected purely by AI classification.")
    return f"{feature}: {raw_value}"


def build_contributing_factors(request, protocol_label: str, feature_importances, top_n: int = 5) -> List[Dict[str, Any]]:
    raw_values = {
        "sourcePort": request.sourcePort,
        "destinationPort": request.destinationPort,
        "protocolEncoded": protocol_label,
        "bytesTransferred": request.bytesTransferred,
        "packetCount": request.packetCount,
        "durationMs": request.durationMs,
        "failedLogins": request.failedLogins,
        "flaggedInt": "Yes" if request.flagged else "No",
    }

    ranked = sorted(zip(FEATURE_ORDER, feature_importances), key=lambda x: x[1], reverse=True)

    factors = []
    for feature, importance in ranked[:top_n]:
        factors.append({
            "feature": FEATURE_LABELS[feature],
            "value": str(raw_values[feature]),
            "importance": round(float(importance) * 100, 1),
            "description": _describe_feature(feature, request.__getattribute__(feature) if feature in
                                               ("sourcePort", "destinationPort", "bytesTransferred", "packetCount",
                                                "durationMs", "failedLogins") else raw_values[feature]),
        })
    return factors


def build_shap_factors(
    shap_values_row: "Any", feature_order: List[str], raw_values: Dict[str, Any], top_n: int = 5
) -> List[Dict[str, Any]]:
    """
    Per-instance SHAP contributing factors for the RandomForest (Model A),
    computed from shap.TreeExplainer on the trained model. Unlike
    build_contributing_factors() (ranked by global feature_importances_ - the
    same order for every request), this ranks by each feature's actual SHAP
    value for THIS specific input, so both the ranking and the magnitudes
    genuinely vary from one request to the next.
    """
    ranked = sorted(zip(feature_order, shap_values_row), key=lambda x: abs(x[1]), reverse=True)
    factors = []
    for feature, shap_value in ranked[:top_n]:
        shap_value = float(shap_value)
        direction = "toward" if shap_value > 0 else "away from"
        factors.append({
            "feature": FEATURE_LABELS.get(feature, feature),
            "value": str(raw_values.get(feature)),
            "importance": round(shap_value, 4),
            "description": (
                f"SHAP value {shap_value:+.4f} ({direction} the predicted class) "
                f"for this request's {FEATURE_LABELS.get(feature, feature)} = {raw_values.get(feature)}."
            ),
        })
    return factors


def build_attention_explanation(attention_weights: List[float]) -> List[Dict[str, float]]:
    """
    Real, model-grounded explanation for the attention-LSTM (Model B): which
    events in the input window the trained attention layer weighted most
    heavily when producing this specific classification. Higher weight = that
    timestep's connection record contributed more to the final decision.
    Returned sorted by descending attention weight (most influential first).
    """
    window_size = len(attention_weights)
    explanation = [
        {
            "timestep": i,
            "eventsBeforeMostRecent": float(window_size - 1 - i),
            "attentionWeight": round(float(weight), 4),
        }
        for i, weight in enumerate(attention_weights)
    ]
    return sorted(explanation, key=lambda x: x["attentionWeight"], reverse=True)


def compute_risk_score(severity: str, confidence: float) -> float:
    base = SEVERITY_WEIGHT.get(severity, 25)
    confidence_factor = 0.6 + 0.4 * confidence
    return round(min(100.0, base * confidence_factor), 1)


_UNREMARKABLE_MARKERS = ("typical", "modest transfer")


def build_reasoning(threat_type: str, contributing_factors: List[Dict[str, Any]]) -> str:
    base_explanation = EXPLANATIONS.get(threat_type, "No significant anomaly detected.")
    if not contributing_factors:
        return base_explanation

    # Prefer factors whose value is actually anomalous over ones the model weighs
    # heavily but which happen to look unremarkable for this specific request -
    # keeps the narrative focused on what's genuinely notable, not just importance rank.
    notable = [f for f in contributing_factors if not any(m in f["description"] for m in _UNREMARKABLE_MARKERS)]
    fallback = [f for f in contributing_factors if f not in notable]
    top_factors = (notable + fallback)[:3]

    factor_summary = "; ".join(f["description"] for f in top_factors)
    return f"{base_explanation} Key indicators driving this classification: {factor_summary}"
