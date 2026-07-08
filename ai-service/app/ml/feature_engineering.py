import numpy as np
import pandas as pd

FEATURE_COLUMNS = [
    "sourcePort", "destinationPort", "protocolEncoded",
    "bytesTransferred", "packetCount", "durationMs", "failedLogins", "flaggedInt",
]


def encode_protocol(protocol: str, protocol_encoder) -> int:
    protocol = (protocol or "TCP").upper()
    classes = list(protocol_encoder.classes_)
    if protocol not in classes:
        # Unseen protocol at inference time - fall back to the most common one (TCP)
        protocol = "TCP" if "TCP" in classes else classes[0]
    return int(protocol_encoder.transform([protocol])[0])


def dataframe_to_features(df: pd.DataFrame, protocol_encoder) -> pd.DataFrame:
    out = df.copy()
    out["protocolEncoded"] = protocol_encoder.transform(out["protocol"].str.upper())
    out["flaggedInt"] = out["flagged"].astype(int)
    return out[FEATURE_COLUMNS]


def request_to_feature_row(request, protocol_encoder) -> np.ndarray:
    return np.array([[
        request.sourcePort or 0,
        request.destinationPort or 0,
        encode_protocol(request.protocol, protocol_encoder),
        request.bytesTransferred or 0,
        request.packetCount or 0,
        request.durationMs or 0,
        request.failedLogins or 0,
        1 if request.flagged else 0,
    ]], dtype=float)
