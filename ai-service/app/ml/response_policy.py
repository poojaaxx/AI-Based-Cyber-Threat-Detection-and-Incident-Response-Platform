"""
RL-inspired adaptive response selector - a genuinely-trained tabular
Q-learning agent that picks a response action for a detected threat, learning
from a reward function that penalizes disproportionate responses (e.g.
BLOCK_IP for a low-severity, low-confidence false positive) and rewards
proportionate ones.

Honesty note on scope vs. the base paper
------------------------------------------
Zhang et al. combine Bayesian game theory (modeling an adversary's strategy)
with MCTS-guided DRL policy optimization over a full sequential
decision process. None of that is implemented here, on purpose:
  - No Bayesian game theory / adversary modeling.
  - No Monte Carlo Tree Search.
  - No deep function approximation - this is tabular Q-learning over a small,
    fully-enumerated discrete state space (threatType x severity x confidence
    bucket), which is exact and interpretable but does not scale to large or
    continuous state spaces the way DRL does.
  - No multi-step MDP: choosing a response action for an already-detected
    threat is a one-shot decision (there is no "next state" the environment
    transitions to as a consequence of the action, in this simplified
    simulation), so training reduces to a contextual-bandit-style update
    (Q(s,a) <- Q(s,a) + alpha * (r - Q(s,a))) rather than a full Bellman
    backup across a trajectory. This is a deliberate, scoped-down
    simplification, not an attempt at the paper's full DRL formulation.

State / action space
---------------------
State  = (threatType, severity, confidenceBucket)
Action = one of BLOCK_IP, DISABLE_USER, QUARANTINE, NOTIFY_ONLY, ESCALATE

Run with:  python -m app.ml.response_policy
"""
import json
import logging
import random

from app.config import DATA_DIR

logger = logging.getLogger("ai-service")

Q_TABLE_PATH = DATA_DIR / "q_table.json"

# Mirrors backend enums ThreatType / Severity (com.cyberguard.platform.entity.enums)
THREAT_TYPES = [
    "MALWARE", "DDOS", "SQL_INJECTION", "XSS", "BRUTE_FORCE",
    "PORT_SCAN", "PHISHING", "RANSOMWARE", "INSIDER_THREAT", "UNKNOWN",
]
SEVERITIES = ["LOW", "MEDIUM", "HIGH", "CRITICAL"]
CONFIDENCE_BUCKETS = ["LOW", "MEDIUM", "HIGH"]  # <0.4, 0.4-0.75, >0.75

ACTIONS = ["BLOCK_IP", "DISABLE_USER", "QUARANTINE", "NOTIFY_ONLY", "ESCALATE"]

SEVERITY_RANK = {"LOW": 0, "MEDIUM": 1, "HIGH": 2, "CRITICAL": 3}
CONFIDENCE_RANK = {"LOW": 0, "MEDIUM": 1, "HIGH": 2}
ACTION_INTENSITY = {"NOTIFY_ONLY": 0, "QUARANTINE": 1, "DISABLE_USER": 2, "BLOCK_IP": 2, "ESCALATE": 3}

NUM_EPISODES = 40000
ALPHA = 0.1  # learning rate
EPSILON_START = 1.0
EPSILON_END = 0.05
RANDOM_SEED = 42


def confidence_bucket(confidence_score: float) -> str:
    if confidence_score < 0.4:
        return "LOW"
    if confidence_score < 0.75:
        return "MEDIUM"
    return "HIGH"


def state_key(threat_type: str, severity: str, confidence_bucket_label: str) -> str:
    return f"{threat_type}|{severity}|{confidence_bucket_label}"


def reward_fn(threat_type: str, severity: str, bucket: str, action: str) -> float:
    """Rewards proportionate responses, penalizes disproportionate ones.
    Deterministic given (state, action) - see module docstring for why this
    single-step formulation replaces a full multi-step MDP reward."""
    severity_rank = SEVERITY_RANK[severity]
    confidence_rank = CONFIDENCE_RANK[bucket]
    intensity = ACTION_INTENSITY[action]

    # Base: reward for matching the action's intensity to the threat's severity.
    reward = 3.0 - abs(intensity - severity_rank)

    # Confidence shaping: an aggressive action (BLOCK_IP/DISABLE_USER/ESCALATE)
    # taken on a low-confidence detection is likely acting on a false positive.
    is_aggressive = intensity >= 2
    if bucket == "LOW" and is_aggressive:
        reward -= 2.0
    if bucket == "LOW" and action in ("NOTIFY_ONLY", "QUARANTINE"):
        reward += 1.0  # appropriately cautious under uncertainty
    if bucket == "HIGH" and reward >= 2.0:
        reward += 1.0  # confident AND proportionate - reinforce strongly

    # The prompt's canonical example: LOW severity + LOW confidence + an
    # aggressive action (e.g. blocking an IP for what's probably a false
    # positive) should be penalized hard, and NOTIFY_ONLY rewarded instead.
    if severity == "LOW" and bucket == "LOW":
        if is_aggressive:
            reward -= 2.0
        elif action == "NOTIFY_ONLY":
            reward += 1.5

    # Threat-type shaping: the right tool for the job.
    if threat_type == "INSIDER_THREAT":
        if action == "DISABLE_USER":
            reward += 1.5
        if action == "BLOCK_IP":
            reward -= 1.0  # blocking an IP doesn't stop an authenticated insider
    if threat_type in ("DDOS", "BRUTE_FORCE") and action == "BLOCK_IP":
        reward += 1.0
    if severity == "CRITICAL" and action == "ESCALATE":
        reward += 1.0

    return reward


def train_q_table() -> dict:
    rng = random.Random(RANDOM_SEED)
    q_table = {
        state_key(t, s, b): {a: 0.0 for a in ACTIONS}
        for t in THREAT_TYPES for s in SEVERITIES for b in CONFIDENCE_BUCKETS
    }

    for episode in range(NUM_EPISODES):
        epsilon = EPSILON_START + (EPSILON_END - EPSILON_START) * (episode / NUM_EPISODES)

        threat_type = rng.choice(THREAT_TYPES)
        severity = rng.choice(SEVERITIES)
        bucket = rng.choice(CONFIDENCE_BUCKETS)
        key = state_key(threat_type, severity, bucket)

        if rng.random() < epsilon:
            action = rng.choice(ACTIONS)
        else:
            action = max(q_table[key], key=q_table[key].get)

        reward = reward_fn(threat_type, severity, bucket, action)
        q_table[key][action] += ALPHA * (reward - q_table[key][action])

    return q_table


def save_q_table(q_table: dict) -> None:
    DATA_DIR.mkdir(exist_ok=True)
    with open(Q_TABLE_PATH, "w") as f:
        json.dump(q_table, f, indent=2)


def load_q_table() -> dict:
    with open(Q_TABLE_PATH, "r") as f:
        return json.load(f)


def train_and_save() -> dict:
    print(f"Training tabular Q-learning response policy over {len(THREAT_TYPES)}x{len(SEVERITIES)}x"
          f"{len(CONFIDENCE_BUCKETS)} states x {len(ACTIONS)} actions, {NUM_EPISODES} episodes...")
    q_table = train_q_table()
    save_q_table(q_table)

    # ---- Pass-criteria self-check: the table must be non-uniform, i.e. it
    # actually learned to differentiate actions rather than leaving them equal.
    spreads = [max(values.values()) - min(values.values()) for values in q_table.values()]
    avg_spread = sum(spreads) / len(spreads)
    zero_spread_states = sum(1 for s in spreads if s < 1e-6)
    print(f"\nAverage Q-value spread (max-min) across states: {avg_spread:.4f}")
    print(f"States with ~zero spread (no differentiation learned): {zero_spread_states}/{len(spreads)}")
    assert avg_spread > 0.5, "Q-table did not learn meaningful action differentiation"

    example_key = state_key("INSIDER_THREAT", "HIGH", "HIGH")
    print(f"\nExample learned Q-values for {example_key}: {q_table[example_key]}")
    example_key_fp = state_key("PORT_SCAN", "LOW", "LOW")
    print(f"Example learned Q-values for {example_key_fp} (likely-false-positive case): {q_table[example_key_fp]}")

    print(f"\nSaved Q-table to {Q_TABLE_PATH}")
    return q_table


class ResponsePolicy:
    def __init__(self):
        self.q_table = None
        self.unavailable_reason = None
        try:
            self.load()
        except Exception as exc:
            # Non-fatal, same reasoning as TemporalThreatModel in
            # lstm_model_loader.py: this is a module-level singleton built at
            # import time, so a training failure here must not crash the
            # whole ai-service over the (unrelated) adaptive-response route.
            self.unavailable_reason = str(exc)
            logger.error(f"Q-learning response policy unavailable, /policy/recommend-action will return 503: {exc}")

    def load(self):
        if not Q_TABLE_PATH.exists():
            train_and_save()
        self.q_table = load_q_table()

    def recommend(self, threat_type: str, severity: str, confidence_score: float) -> dict:
        if self.q_table is None:
            raise RuntimeError(f"Q-learning response policy is not available in this deployment ({self.unavailable_reason}).")
        threat_type = threat_type if threat_type in THREAT_TYPES else "UNKNOWN"
        severity = severity if severity in SEVERITIES else "LOW"
        bucket = confidence_bucket(confidence_score)
        key = state_key(threat_type, severity, bucket)

        q_values = self.q_table.get(key, {a: 0.0 for a in ACTIONS})
        best_action = max(q_values, key=q_values.get)
        return {
            "state": key,
            "recommendedAction": best_action,
            "qValues": q_values,
        }


if __name__ == "__main__":
    train_and_save()
else:
    response_policy = ResponsePolicy()
