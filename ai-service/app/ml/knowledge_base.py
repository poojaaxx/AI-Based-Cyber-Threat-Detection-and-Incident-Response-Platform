"""
Lightweight, dependency-free knowledge base powering the AI Security
Assistant. Uses keyword/intent matching rather than an external LLM API so
the assistant works fully offline and with zero external cost - appropriate
for a self-hosted student/enterprise deployment.
"""

THREAT_KNOWLEDGE = {
    "MALWARE": "Malware is malicious software (viruses, trojans, worms, spyware) designed to damage, disrupt, or gain "
               "unauthorized access to systems. Detection relies on signature matching, heuristic/behavioral analysis, "
               "and sandboxing. Mitigation: isolate the host, run EDR/antivirus scans, and check file hashes against "
               "threat intel (VirusTotal, AlienVault OTX).",
    "DDOS": "A Distributed Denial-of-Service (DDoS) attack floods a target with traffic from many sources to exhaust "
            "bandwidth or compute resources, making services unavailable. Mitigation: rate-limiting, upstream "
            "scrubbing/CDN protection (e.g., Cloudflare), auto-scaling, and blackholing malicious source IPs.",
    "SQL_INJECTION": "SQL Injection (SQLi) exploits unsanitized user input in database queries to read, modify, or "
                      "delete data. Corresponds to MITRE ATT&CK T1190 (Exploit Public-Facing Application). Mitigation: "
                      "use parameterized queries/ORMs, input validation, and a Web Application Firewall (WAF).",
    "XSS": "Cross-Site Scripting (XSS) injects malicious scripts into trusted web pages, executing in victims' "
           "browsers to steal cookies/sessions or deface content. Mitigation: output encoding, Content-Security-Policy "
           "headers, and framework-level auto-escaping (React/Angular escape by default).",
    "BRUTE_FORCE": "Brute force attacks (MITRE T1110) systematically try many username/password combinations to gain "
                    "unauthorized access. Mitigation: account lockout policies, rate limiting, CAPTCHA, and MFA.",
    "PORT_SCAN": "Port scanning (MITRE T1046/T1595) is reconnaissance to discover open ports and running services "
                 "before an attack. Mitigation: close unused ports, deploy IDS/IPS to detect scan patterns, and use "
                 "network segmentation.",
    "PHISHING": "Phishing (MITRE T1566) uses deceptive emails or websites to trick users into revealing credentials "
                "or installing malware. Mitigation: email filtering/DMARC-DKIM-SPF, security awareness training, and "
                "blocking known malicious domains.",
    "RANSOMWARE": "Ransomware encrypts victim data and demands payment for the decryption key, often after lateral "
                  "movement via SMB (MITRE T1486). Mitigation: offline/immutable backups, network segmentation, "
                  "least-privilege access, and rapid isolation of infected hosts.",
    "INSIDER_THREAT": "Insider threats (MITRE T1078) come from employees or contractors misusing legitimate access "
                       "to steal data or sabotage systems. Mitigation: least-privilege access, User and Entity "
                       "Behavior Analytics (UEBA), and offboarding checklists.",
}

GENERAL_TOPICS = {
    "cvss": "CVSS (Common Vulnerability Scoring System) rates vulnerability severity from 0-10 based on exploitability "
            "and impact metrics. 0-3.9 = Low, 4.0-6.9 = Medium, 7.0-8.9 = High, 9.0-10.0 = Critical.",
    "mitre att&ck": "MITRE ATT&CK is a globally accessible knowledge base of adversary tactics and techniques based "
                    "on real-world observations, used to model, detect, and prevent cyber threats across the attack "
                    "lifecycle (Reconnaissance -> Impact).",
    "zero day": "A zero-day vulnerability is a flaw unknown to the vendor with no available patch, making it "
                "especially dangerous when actively exploited (a 'zero-day exploit').",
    "incident response": "Incident response follows a lifecycle: Preparation -> Detection & Analysis -> Containment "
                          "-> Eradication -> Recovery -> Post-Incident Review (lessons learned).",
    "mfa": "Multi-Factor Authentication (MFA) requires two or more verification factors (password + OTP/biometric), "
           "significantly reducing the risk of account takeover even if a password is compromised.",
    "firewall": "A firewall enforces network traffic policy by allowing/blocking packets based on rules (IP, port, "
                "protocol). Next-gen firewalls (NGFW) add deep packet inspection and application awareness.",
}


def find_threat_match(message: str):
    message_upper = message.upper().replace("-", "_").replace(" ", "_")
    for threat_type, explanation in THREAT_KNOWLEDGE.items():
        if threat_type in message_upper or threat_type.replace("_", " ") in message.upper():
            return threat_type, explanation
    # Friendlier natural-language aliases
    aliases = {
        "SQL INJECTION": "SQL_INJECTION", "SQLI": "SQL_INJECTION",
        "CROSS SITE SCRIPTING": "XSS", "CROSS-SITE SCRIPTING": "XSS",
        "DENIAL OF SERVICE": "DDOS", "DOS ATTACK": "DDOS",
        "RANSOM": "RANSOMWARE", "PHISH": "PHISHING",
        "PORT SCANNING": "PORT_SCAN", "SCANNING": "PORT_SCAN",
        "INSIDER": "INSIDER_THREAT", "BRUTE FORCE": "BRUTE_FORCE", "BRUTEFORCE": "BRUTE_FORCE",
        "VIRUS": "MALWARE", "TROJAN": "MALWARE", "WORM": "MALWARE",
    }
    for alias, threat_type in aliases.items():
        if alias in message.upper():
            return threat_type, THREAT_KNOWLEDGE[threat_type]
    return None, None


def find_general_topic_match(message: str):
    message_lower = message.lower()
    for topic, explanation in GENERAL_TOPICS.items():
        if topic in message_lower:
            return topic, explanation
    return None, None
