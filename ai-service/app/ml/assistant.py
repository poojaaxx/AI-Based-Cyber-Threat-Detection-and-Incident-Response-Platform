import logging
import re

from groq import Groq, GroqError

from app.config import GROQ_API_KEY
from app.ml.constants import RECOMMENDED_ACTIONS
from app.ml.knowledge_base import find_general_topic_match, find_threat_match

logger = logging.getLogger(__name__)

CVE_PATTERN = re.compile(r"CVE-\d{4}-\d{4,7}", re.IGNORECASE)

GREETINGS = {"hi", "hello", "hey", "good morning", "good afternoon", "good evening"}

HELP_TEXT = (
    "I'm the CyberGuard AI Security Assistant. I can help you with:\n"
    "- Explaining cyber threats (malware, DDoS, SQL injection, XSS, brute force, port scan, phishing, ransomware, insider threats)\n"
    "- Explaining CVEs (e.g. \"explain CVE-2021-44228\")\n"
    "- Recommending mitigations for a given threat\n"
    "- General cybersecurity concepts (CVSS, MITRE ATT&CK, MFA, firewalls, incident response)\n"
    "- Pretty much any other question you ask\n\n"
    "Try asking: \"What is ransomware?\" or \"How do I mitigate a brute force attack?\""
)

SYSTEM_PROMPT = (
    "You are the CyberGuard AI Security Assistant, embedded in an AI-based cyber threat "
    "detection and incident response platform. Users are analysts and engineers using the "
    "platform. You explain cyber threats (malware, DDoS, SQL injection, XSS, brute force, "
    "port scanning, phishing, ransomware, insider threats), CVEs, MITRE ATT&CK techniques, "
    "and general cybersecurity concepts, and recommend mitigations. You can also answer "
    "general questions outside cybersecurity. Keep answers concise and practical. If asked "
    "about a specific CVE, note you may not have live NVD data and point the user to the "
    "platform's Threat Intelligence page for authoritative CVSS scores and MITRE ATT&CK "
    "mappings."
)

_client = Groq(api_key=GROQ_API_KEY) if GROQ_API_KEY else None


def _parse_context(context: str | None, current_message: str) -> list[dict]:
    """Turns the backend's "SENDER: text" transcript into a Messages API history.

    The backend saves the user's message before building context, so the last
    line is always a duplicate of current_message - drop it here.
    """
    if not context:
        return []

    turns = []
    for line in context.strip().splitlines():
        if ": " not in line:
            continue
        sender, text = line.split(": ", 1)
        role = "assistant" if sender.strip().upper() == "ASSISTANT" else "user"
        turns.append({"role": role, "content": text})

    if turns and turns[-1]["role"] == "user" and turns[-1]["content"] == current_message:
        turns.pop()
    while turns and turns[0]["role"] == "assistant":
        turns.pop(0)
    return turns


def _call_groq(message: str, context: str | None) -> str:
    messages = [{"role": "system", "content": SYSTEM_PROMPT}]
    messages.extend(_parse_context(context, message))
    messages.append({"role": "user", "content": message})

    response = _client.chat.completions.create(
        model="llama-3.3-70b-versatile",
        max_tokens=1024,
        messages=messages,
    )
    reply = response.choices[0].message.content
    return reply if reply else _offline_reply(message)


def generate_reply(message: str, context: str | None = None) -> str:
    text = message.strip()
    if not text:
        return HELP_TEXT

    if _client is None:
        logger.warning("GROQ_API_KEY not configured; using offline fallback responder.")
        return _offline_reply(text)

    try:
        return _call_groq(text, context)
    except GroqError as exc:
        logger.error("Groq API call failed, falling back to offline responder: %s", exc)
        return _offline_reply(text)


def _offline_reply(message: str) -> str:
    """Keyword/rule-based responder used when the Groq API is unavailable."""
    text = message.strip()
    lower = text.lower()

    if lower in GREETINGS or lower.startswith(tuple(GREETINGS)):
        return "Hello! I'm your AI Security Assistant. " + HELP_TEXT

    if "help" in lower and len(lower) < 20:
        return HELP_TEXT

    cve_match = CVE_PATTERN.search(text)
    if cve_match:
        cve_id = cve_match.group(0).upper()
        return (
            f"{cve_id}: I don't have live NVD access in this environment, but you can look this CVE up on the "
            f"Threat Intelligence page for its CVSS score, affected products, and MITRE ATT&CK mapping. "
            f"In general, treat any CVE with a CVSS score >= 9.0 as CRITICAL and prioritize patching immediately, "
            f"especially if it's remotely exploitable without authentication."
        )

    threat_type, explanation = find_threat_match(text)
    if threat_type:
        mitigation = RECOMMENDED_ACTIONS.get(threat_type, "")
        wants_mitigation_only = any(k in lower for k in ["mitigat", "prevent", "fix", "how do i", "how to stop", "recommend"])
        if wants_mitigation_only:
            return f"Recommended mitigation for {threat_type.replace('_', ' ').title()}: {mitigation}"
        return f"{explanation}\n\nRecommended mitigation: {mitigation}"

    topic, explanation = find_general_topic_match(text)
    if topic:
        return explanation

    if "incident" in lower and ("analy" in lower or "explain" in lower):
        return (
            "To analyze an incident: check its severity, associated threat (if any), the timeline of actions taken, "
            "and comments from assigned analysts on the Incidents page. Look for the recommended action tied to the "
            "originating threat detection, and verify whether automated response actions (IP block, quarantine) "
            "already ran successfully."
        )

    return (
        "The AI assistant service is currently unavailable. Please try again shortly, or consult the "
        "Threat Intelligence page for CVE and MITRE ATT&CK details."
    )
