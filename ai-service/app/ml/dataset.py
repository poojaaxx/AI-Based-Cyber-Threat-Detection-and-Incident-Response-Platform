"""
Synthetic network/event feature dataset generator for training the threat
classification model. Each threat class has a distinct, realistic feature
distribution (inspired by common IDS/NetFlow characteristics for that attack
category) so a RandomForest can learn genuinely separable decision boundaries
rather than memorizing noise.
"""
import numpy as np
import pandas as pd

from app.ml.constants import PROTOCOLS

RANDOM_SEED = 42


def _clip_nonneg(arr: np.ndarray) -> np.ndarray:
    return np.clip(arr, 0, None)


def generate_dataset(samples_per_class: int = 1200, seed: int = RANDOM_SEED) -> pd.DataFrame:
    rng = np.random.default_rng(seed)
    rows = []

    def add_rows(label, n, source_port, dest_port, protocol_choices, bytes_range, packet_range,
                 duration_range, failed_logins_range, flagged_prob):
        for _ in range(n):
            rows.append({
                "sourcePort": int(rng.integers(*source_port)),
                "destinationPort": int(rng.choice(dest_port)) if isinstance(dest_port, (list, np.ndarray)) else int(rng.integers(*dest_port)),
                "protocol": rng.choice(protocol_choices),
                "bytesTransferred": float(_clip_nonneg(rng.normal(*bytes_range))),
                "packetCount": int(_clip_nonneg(rng.normal(*packet_range))),
                "durationMs": float(_clip_nonneg(rng.normal(*duration_range))),
                "failedLogins": int(_clip_nonneg(rng.normal(*failed_logins_range))),
                "flagged": bool(rng.random() < flagged_prob),
                "threatType": label,
            })

    n = samples_per_class

    add_rows("MALWARE", n, (1024, 65535), [4444, 8080, 6667, 1337], ["TCP", "HTTP", "HTTPS"],
              (50000, 20000), (300, 100), (5000, 2000), (0, 0.2), 0.6)

    add_rows("DDOS", n, (1024, 65535), (1, 200), ["UDP", "TCP", "ICMP"],
              (500000, 150000), (5000, 1500), (800, 300), (0, 0.1), 0.85)

    add_rows("SQL_INJECTION", n, (1024, 65535), [80, 443, 8080], ["HTTP", "HTTPS"],
              (8000, 3000), (40, 15), (600, 300), (0, 0.1), 0.5)

    add_rows("XSS", n, (1024, 65535), [80, 443], ["HTTP", "HTTPS"],
              (2000, 800), (15, 6), (250, 100), (0, 0.05), 0.4)

    add_rows("BRUTE_FORCE", n, (1024, 65535), [22, 3389, 21, 23], ["SSH", "RDP", "FTP", "TCP"],
              (5000, 2000), (100, 40), (8000, 3000), (15, 5), 0.7)

    add_rows("PORT_SCAN", n, (1024, 65535), (1, 65535), ["TCP", "UDP", "ICMP"],
              (300, 150), (5, 3), (80, 40), (0, 0.05), 0.3)

    add_rows("PHISHING", n, (1024, 65535), [25, 587, 443, 465], ["SMTP", "HTTPS"],
              (12000, 5000), (60, 25), (2000, 800), (0, 0.1), 0.35)

    add_rows("RANSOMWARE", n, (1024, 65535), [445, 139], ["SMB", "TCP"],
              (900000, 250000), (8000, 2500), (15000, 5000), (0, 0.1), 0.75)

    add_rows("INSIDER_THREAT", n, (1024, 65535), (1024, 65535), ["HTTPS", "FTP", "TCP"],
              (150000, 60000), (600, 200), (10000, 4000), (2, 2), 0.4)

    add_rows("BENIGN", n, (1024, 65535), [80, 443, 22, 53, 3306], ["HTTP", "HTTPS", "TCP", "UDP"],
              (4000, 2000), (20, 10), (500, 300), (0, 0.05), 0.02)

    df = pd.DataFrame(rows)
    df["bytesTransferred"] = df["bytesTransferred"].round(0)
    df["durationMs"] = df["durationMs"].round(0)
    df = df.sample(frac=1, random_state=seed).reset_index(drop=True)
    return df
