THREAT_TYPES = [
    "MALWARE",
    "DDOS",
    "SQL_INJECTION",
    "XSS",
    "BRUTE_FORCE",
    "PORT_SCAN",
    "PHISHING",
    "RANSOMWARE",
    "INSIDER_THREAT",
    "BENIGN",
]

PROTOCOLS = ["TCP", "UDP", "HTTP", "HTTPS", "SMTP", "SMB", "ICMP", "FTP", "SSH", "RDP"]

# Baseline severity per threat type; final severity is nudged by confidence.
BASE_SEVERITY = {
    "MALWARE": "HIGH",
    "DDOS": "CRITICAL",
    "SQL_INJECTION": "HIGH",
    "XSS": "MEDIUM",
    "BRUTE_FORCE": "MEDIUM",
    "PORT_SCAN": "LOW",
    "PHISHING": "MEDIUM",
    "RANSOMWARE": "CRITICAL",
    "INSIDER_THREAT": "HIGH",
    "BENIGN": "LOW",
}

RECOMMENDED_ACTIONS = {
    "MALWARE": "Isolate the affected host from the network, run a full antivirus/EDR scan, and analyze the file hash against threat intelligence feeds.",
    "DDOS": "Enable upstream rate-limiting / DDoS mitigation, block offending source IPs, and scale out affected services.",
    "SQL_INJECTION": "Block the source IP at the WAF, sanitize/parameterize the vulnerable query, and review database audit logs for data exfiltration.",
    "XSS": "Sanitize and encode all user-supplied output, deploy a Content-Security-Policy header, and patch the vulnerable endpoint.",
    "BRUTE_FORCE": "Lock the targeted account, enforce account lockout/backoff policies, and block the source IP.",
    "PORT_SCAN": "Add the source IP to the watchlist, ensure unused ports are closed, and monitor for follow-up exploitation attempts.",
    "PHISHING": "Quarantine the email/domain, alert affected users, and add the sender/domain to the blocklist.",
    "RANSOMWARE": "Immediately isolate the affected host, disable shared network drives, and restore from the last known-good backup.",
    "INSIDER_THREAT": "Disable the implicated user account, escalate to HR/Legal, and audit recent data access by that account.",
    "BENIGN": "No action required. Continue routine monitoring.",
}

EXPLANATIONS = {
    "MALWARE": "Traffic pattern matches known malware command-and-control beaconing characteristics (irregular payload size, suspicious protocol usage).",
    "DDOS": "Abnormally high packet volume and byte transfer in a short time window indicates a distributed denial-of-service attack.",
    "SQL_INJECTION": "Web traffic characteristics (port 80/443, anomalous request size/timing) are consistent with SQL injection attempts.",
    "XSS": "Short-duration, small-payload HTTP requests consistent with reflected/stored cross-site scripting probes.",
    "BRUTE_FORCE": "Elevated failed login count against an authentication service port indicates a credential brute-forcing attempt.",
    "PORT_SCAN": "Low byte/packet counts across a short duration on non-standard ports indicate reconnaissance/port scanning.",
    "PHISHING": "Mail-protocol traffic with characteristics matching known phishing campaign patterns.",
    "RANSOMWARE": "Very high sustained byte transfer consistent with mass file encryption / lateral SMB propagation.",
    "INSIDER_THREAT": "Unusual internal access pattern with elevated data transfer inconsistent with the user's normal baseline.",
    "BENIGN": "Traffic characteristics fall within normal operating baselines.",
}
