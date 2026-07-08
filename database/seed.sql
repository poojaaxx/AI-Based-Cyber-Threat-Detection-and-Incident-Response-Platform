-- =====================================================================
-- Seed Data for CyberGuard Platform
-- Default password for all seeded users is: Password@123
-- BCrypt hash generated with strength 10
-- =====================================================================
USE cyberguard_db;

INSERT INTO roles (name, description) VALUES
('ROLE_ADMIN', 'Full system access including user management and configuration'),
('ROLE_ANALYST', 'Security analyst - manage threats and incidents'),
('ROLE_USER', 'Standard user with read-only dashboard access');

-- password hash below corresponds to plaintext: Password@123
-- (real BCrypt hash generated via the running application's PasswordEncoder, not hand-written)
INSERT INTO users (full_name, username, email, password_hash, department, status) VALUES
('System Administrator', 'admin', 'admin@cyberguard.local', '$2a$10$sUW8YX9iCfDR0vq/GtZVteRql1KRFArlskgtXqKvyT6eEGfvVLTZa', 'IT Security', 'ACTIVE'),
('Alice Analyst', 'alice.analyst', 'alice@cyberguard.local', '$2a$10$sUW8YX9iCfDR0vq/GtZVteRql1KRFArlskgtXqKvyT6eEGfvVLTZa', 'SOC Team', 'ACTIVE'),
('Bob User', 'bob.user', 'bob@cyberguard.local', '$2a$10$sUW8YX9iCfDR0vq/GtZVteRql1KRFArlskgtXqKvyT6eEGfvVLTZa', 'Finance', 'ACTIVE');

INSERT INTO user_roles (user_id, role_id) VALUES
(1, 1), (2, 2), (3, 3);

INSERT INTO notification_preferences (user_id, email_alerts, critical_alerts, dashboard_alerts, weekly_summary) VALUES
(1, TRUE, TRUE, TRUE, TRUE),
(2, TRUE, TRUE, TRUE, FALSE),
(3, TRUE, TRUE, TRUE, FALSE);

INSERT INTO mitre_attack_techniques (technique_id, name, tactic, description) VALUES
('T1190', 'Exploit Public-Facing Application', 'Initial Access', 'Adversaries exploit weaknesses in internet-facing systems.'),
('T1110', 'Brute Force', 'Credential Access', 'Adversaries use brute force techniques to gain access to accounts.'),
('T1595', 'Active Scanning', 'Reconnaissance', 'Adversaries scan victim infrastructure for vulnerabilities.'),
('T1566', 'Phishing', 'Initial Access', 'Adversaries send phishing messages to gain access.'),
('T1486', 'Data Encrypted for Impact', 'Impact', 'Adversaries encrypt data on target systems (ransomware).'),
('T1046', 'Network Service Discovery', 'Discovery', 'Adversaries attempt to get a listing of services running on hosts (port scanning).'),
('T1059', 'Command and Scripting Interpreter', 'Execution', 'Adversaries abuse command interpreters to execute commands (SQLi/XSS payload delivery).'),
('T1078', 'Valid Accounts', 'Defense Evasion', 'Adversaries use compromised credentials, often linked to insider threats.'),
('T1498', 'Network Denial of Service', 'Impact', 'Adversaries flood a target network/service with traffic to degrade or deny availability (DDoS).'),
('T1105', 'Ingress Tool Transfer', 'Command and Control', 'Adversaries transfer malicious tools/payloads onto a compromised host from an external system.');

INSERT INTO cve_records (cve_id, title, description, severity, cvss_score, published_date, affected_products, reference_url) VALUES
('CVE-2024-3400', 'PAN-OS Command Injection', 'Command injection vulnerability in GlobalProtect gateway allowing unauthenticated RCE.', 'CRITICAL', 10.0, '2024-04-12', 'PAN-OS 10.2, 11.0, 11.1', 'https://nvd.nist.gov/vuln/detail/CVE-2024-3400'),
('CVE-2023-44487', 'HTTP/2 Rapid Reset', 'HTTP/2 protocol allows DDoS via rapid stream reset.', 'HIGH', 7.5, '2023-10-10', 'HTTP/2 servers', 'https://nvd.nist.gov/vuln/detail/CVE-2023-44487'),
('CVE-2021-44228', 'Log4Shell', 'Apache Log4j2 JNDI features do not protect against attacker controlled LDAP servers, allowing RCE.', 'CRITICAL', 10.0, '2021-12-10', 'Apache Log4j 2.0-2.14.1', 'https://nvd.nist.gov/vuln/detail/CVE-2021-44228'),
('CVE-2017-0144', 'EternalBlue SMB RCE', 'SMBv1 remote code execution vulnerability exploited by WannaCry ransomware.', 'CRITICAL', 8.1, '2017-03-14', 'Windows SMBv1', 'https://nvd.nist.gov/vuln/detail/CVE-2017-0144');

INSERT INTO ioc_records (ioc_type, ioc_value, threat_type, confidence_score, source, first_seen, last_seen, is_active) VALUES
('IP', '185.220.101.45', 'PORT_SCAN', 92.50, 'AbuseIPDB', NOW() - INTERVAL 5 DAY, NOW(), TRUE),
('IP', '45.155.205.233', 'BRUTE_FORCE', 88.00, 'AlienVault OTX', NOW() - INTERVAL 3 DAY, NOW(), TRUE),
('DOMAIN', 'malicious-update-service.com', 'PHISHING', 95.00, 'PhishTank', NOW() - INTERVAL 10 DAY, NOW(), TRUE),
('FILE_HASH', 'e99a18c428cb38d5f260853678922e03', 'RANSOMWARE', 99.00, 'VirusTotal', NOW() - INTERVAL 1 DAY, NOW(), TRUE);

INSERT INTO blocked_ips (ip_address, reason, blocked_by, active) VALUES
('185.220.101.45', 'Repeated port scanning activity', 1, TRUE);

INSERT INTO system_logs (log_level, source, message, ip_address) VALUES
('WARN', 'FIREWALL', 'Multiple connection attempts detected from external IP', '185.220.101.45'),
('INFO', 'AUTH_SERVICE', 'User admin logged in successfully', '10.0.0.5'),
('CRITICAL', 'IDS', 'Ransomware signature match detected on host FIN-WS-02', '10.0.2.15');

INSERT INTO notifications (user_id, title, message, type, is_read) VALUES
(1, 'Critical Threat Detected', 'Ransomware activity detected on FIN-WS-02. Immediate action recommended.', 'CRITICAL', FALSE),
(1, 'New Incident Assigned', 'Incident INC-2026-000001 has been created and requires triage.', 'INCIDENT', FALSE),
(2, 'Threat Mitigated', 'Brute force attempt from 45.155.205.233 was automatically blocked.', 'THREAT', TRUE);
