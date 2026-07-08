import re

from app.ml.constants import RECOMMENDED_ACTIONS
from app.ml.knowledge_base import find_general_topic_match, find_threat_match

CVE_PATTERN = re.compile(r"CVE-\d{4}-\d{4,7}", re.IGNORECASE)

GREETINGS = {"hi", "hello", "hey", "good morning", "good afternoon", "good evening"}

HELP_TEXT = (
    "I'm the CyberGuard AI Security Assistant. I can help you with:\n"
    "- Explaining cyber threats (malware, DDoS, SQL injection, XSS, brute force, port scan, phishing, ransomware, insider threats)\n"
    "- Explaining CVEs (e.g. \"explain CVE-2021-44228\")\n"
    "- Recommending mitigations for a given threat\n"
    "- General cybersecurity concepts (CVSS, MITRE ATT&CK, MFA, firewalls, incident response)\n\n"
    "Try asking: \"What is ransomware?\" or \"How do I mitigate a brute force attack?\""
)


def generate_reply(message: str) -> str:
    text = message.strip()
    lower = text.lower()

    if not text:
        return HELP_TEXT

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
        "I don't have a specific answer for that yet, but I can help explain any of the nine detected threat "
        "categories (malware, DDoS, SQL injection, XSS, brute force, port scan, phishing, ransomware, insider threat), "
        "CVEs, MITRE ATT&CK techniques, or general cybersecurity concepts. Could you rephrase your question or ask "
        "about one of those topics?"
    )
