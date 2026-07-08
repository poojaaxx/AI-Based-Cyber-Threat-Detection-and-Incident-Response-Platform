-- =====================================================================
-- AI-Based Cyber Threat Detection and Incident Response Platform
-- MySQL Normalized Schema
-- =====================================================================

CREATE DATABASE IF NOT EXISTS cyberguard_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE cyberguard_db;

SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================================
-- 1. ROLES & USERS
-- =====================================================================
CREATE TABLE roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,          -- ROLE_ADMIN, ROLE_ANALYST, ROLE_USER
    description VARCHAR(255)
);

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL,
    username VARCHAR(60) NOT NULL UNIQUE,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    department VARCHAR(100),
    phone VARCHAR(30),
    status ENUM('ACTIVE','DISABLED','LOCKED') NOT NULL DEFAULT 'ACTIVE',
    last_login_at DATETIME NULL,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

CREATE TABLE refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(512) NOT NULL UNIQUE,
    expiry_date DATETIME NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE notification_preferences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    email_alerts BOOLEAN NOT NULL DEFAULT TRUE,
    critical_alerts BOOLEAN NOT NULL DEFAULT TRUE,
    dashboard_alerts BOOLEAN NOT NULL DEFAULT TRUE,
    weekly_summary BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE api_configurations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    api_key VARCHAR(255) NOT NULL UNIQUE,
    ai_service_url VARCHAR(255),
    rate_limit_per_min INT NOT NULL DEFAULT 60,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- =====================================================================
-- 2. THREAT INTELLIGENCE (CVE / IOC / MITRE ATT&CK)
-- =====================================================================
CREATE TABLE cve_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cve_id VARCHAR(30) NOT NULL UNIQUE,         -- e.g. CVE-2024-12345
    title VARCHAR(255) NOT NULL,
    description TEXT,
    severity ENUM('LOW','MEDIUM','HIGH','CRITICAL') NOT NULL,
    cvss_score DECIMAL(3,1),
    published_date DATE,
    affected_products TEXT,
    reference_url VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE mitre_attack_techniques (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    technique_id VARCHAR(20) NOT NULL UNIQUE,   -- e.g. T1190
    name VARCHAR(255) NOT NULL,
    tactic VARCHAR(100) NOT NULL,               -- e.g. Initial Access
    description TEXT
);

CREATE TABLE ioc_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ioc_type ENUM('IP','DOMAIN','URL','FILE_HASH','EMAIL') NOT NULL,
    ioc_value VARCHAR(500) NOT NULL,
    threat_type VARCHAR(100),
    confidence_score DECIMAL(5,2),
    source VARCHAR(150),
    first_seen DATETIME,
    last_seen DATETIME,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_ioc (ioc_type, ioc_value(255))
);

CREATE TABLE threat_intel_mitre_map (
    cve_record_id BIGINT NOT NULL,
    technique_id BIGINT NOT NULL,
    PRIMARY KEY (cve_record_id, technique_id),
    FOREIGN KEY (cve_record_id) REFERENCES cve_records(id) ON DELETE CASCADE,
    FOREIGN KEY (technique_id) REFERENCES mitre_attack_techniques(id) ON DELETE CASCADE
);

-- =====================================================================
-- 3. THREATS (AI Detection Results)
-- =====================================================================
CREATE TABLE threats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    threat_type ENUM('MALWARE','DDOS','SQL_INJECTION','XSS','BRUTE_FORCE',
                      'PORT_SCAN','PHISHING','RANSOMWARE','INSIDER_THREAT','UNKNOWN') NOT NULL,
    severity ENUM('LOW','MEDIUM','HIGH','CRITICAL') NOT NULL,
    confidence_score DECIMAL(5,2) NOT NULL,
    source_ip VARCHAR(45),
    destination_ip VARCHAR(45),
    source_port INT,
    destination_port INT,
    protocol VARCHAR(20),
    affected_user_id BIGINT NULL,
    recommended_action VARCHAR(500),
    status ENUM('DETECTED','ANALYZING','MITIGATED','FALSE_POSITIVE','IGNORED') NOT NULL DEFAULT 'DETECTED',
    raw_payload TEXT,
    risk_score DECIMAL(5,2),               -- Explainable AI: blended severity+confidence risk score (0-100)
    reasoning TEXT,                        -- Explainable AI: model-derived explanation of the classification
    contributing_factors TEXT,             -- Explainable AI: JSON array of {feature, value, importance, description}
    detected_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cve_record_id BIGINT NULL,
    FOREIGN KEY (affected_user_id) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (cve_record_id) REFERENCES cve_records(id) ON DELETE SET NULL,
    INDEX idx_threats_detected_at (detected_at),
    INDEX idx_threats_type (threat_type),
    INDEX idx_threats_severity (severity)
);

-- =====================================================================
-- 4. INCIDENTS
-- =====================================================================
CREATE TABLE incidents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_number VARCHAR(30) NOT NULL UNIQUE,   -- INC-2026-000123
    title VARCHAR(255) NOT NULL,
    description TEXT,
    severity ENUM('LOW','MEDIUM','HIGH','CRITICAL') NOT NULL,
    status ENUM('OPEN','ASSIGNED','IN_PROGRESS','RESOLVED','CLOSED') NOT NULL DEFAULT 'OPEN',
    threat_id BIGINT NULL,
    reported_by BIGINT NOT NULL,
    assigned_to BIGINT NULL,
    resolution_notes TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    resolved_at DATETIME NULL,
    FOREIGN KEY (threat_id) REFERENCES threats(id) ON DELETE SET NULL,
    FOREIGN KEY (reported_by) REFERENCES users(id),
    FOREIGN KEY (assigned_to) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_incidents_status (status),
    INDEX idx_incidents_severity (severity)
);

CREATE TABLE incident_comments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    comment TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (incident_id) REFERENCES incidents(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE incident_timeline (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,     -- CREATED, ASSIGNED, STATUS_CHANGED, COMMENTED, RESOLVED, ACTION_TAKEN
    description VARCHAR(500) NOT NULL,
    performed_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (incident_id) REFERENCES incidents(id) ON DELETE CASCADE,
    FOREIGN KEY (performed_by) REFERENCES users(id) ON DELETE SET NULL
);

-- =====================================================================
-- 5. AUTOMATED INCIDENT RESPONSE ACTIONS
-- =====================================================================
CREATE TABLE response_actions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT NULL,
    threat_id BIGINT NULL,
    action_type ENUM('BLOCK_IP','DISABLE_USER','QUARANTINE_THREAT','NOTIFY_ADMIN','GENERATE_INCIDENT') NOT NULL,
    target VARCHAR(255),                 -- IP / username / file hash
    status ENUM('PENDING','SUCCESS','FAILED') NOT NULL DEFAULT 'PENDING',
    triggered_by ENUM('AUTOMATED','MANUAL') NOT NULL DEFAULT 'AUTOMATED',
    performed_by BIGINT NULL,
    details VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (incident_id) REFERENCES incidents(id) ON DELETE CASCADE,
    FOREIGN KEY (threat_id) REFERENCES threats(id) ON DELETE CASCADE,
    FOREIGN KEY (performed_by) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE blocked_ips (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ip_address VARCHAR(45) NOT NULL UNIQUE,
    reason VARCHAR(255),
    blocked_by BIGINT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (blocked_by) REFERENCES users(id) ON DELETE SET NULL
);

-- =====================================================================
-- 6. REAL-TIME MONITORING (Logs)
-- =====================================================================
CREATE TABLE system_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    log_level ENUM('INFO','WARN','ERROR','CRITICAL') NOT NULL,
    source VARCHAR(100) NOT NULL,        -- e.g. AUTH_SERVICE, NETWORK, FIREWALL
    message VARCHAR(1000) NOT NULL,
    ip_address VARCHAR(45),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_syslogs_created (created_at)
);

CREATE TABLE login_attempts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    username_attempted VARCHAR(60),
    ip_address VARCHAR(45),
    success BOOLEAN NOT NULL,
    user_agent VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE network_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_ip VARCHAR(45) NOT NULL,
    destination_ip VARCHAR(45) NOT NULL,
    source_port INT,
    destination_port INT,
    protocol VARCHAR(20),
    bytes_transferred BIGINT,
    packet_count INT,
    flagged BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_netevents_created (created_at)
);

-- =====================================================================
-- 7. NOTIFICATIONS
-- =====================================================================
CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    type ENUM('THREAT','INCIDENT','SYSTEM','CRITICAL') NOT NULL DEFAULT 'SYSTEM',
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    severity ENUM('LOW','MEDIUM','HIGH','CRITICAL') NULL,
    icon VARCHAR(50) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_notifications_user (user_id, is_read)
);

-- =====================================================================
-- 8. AUDIT LOGS
-- =====================================================================
CREATE TABLE audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    action VARCHAR(150) NOT NULL,        -- LOGIN, LOGOUT, CREATE_INCIDENT, BLOCK_IP, etc.
    entity_type VARCHAR(100),
    entity_id BIGINT,
    details VARCHAR(1000),
    ip_address VARCHAR(45),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_audit_created (created_at)
);

-- =====================================================================
-- 9. AI ASSISTANT CHAT HISTORY
-- =====================================================================
CREATE TABLE chat_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) DEFAULT 'New Conversation',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    sender ENUM('USER','ASSISTANT') NOT NULL,
    message TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES chat_sessions(id) ON DELETE CASCADE
);

-- =====================================================================
-- 10. THREAT SIMULATION LAB
-- =====================================================================
-- Contextual fields captured by the Threat Simulation Lab that have no home on
-- the core `threats` table (which represents ALL threats system-wide, not just
-- simulated ones). None of these feed the Random Forest model - they are
-- recorded for analyst context and simulation history only. result_threat_id
-- is nullable + ON DELETE SET NULL so deleting simulation history never
-- touches the underlying Threat record, and vice versa.
CREATE TABLE simulation_runs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    performed_by BIGINT NULL,
    source_ip VARCHAR(45),
    destination_ip VARCHAR(45),
    port INT,
    protocol VARCHAR(20),
    packet_size INT,
    failed_login_attempts INT,
    total_login_attempts INT,
    traffic_type VARCHAR(50),
    country VARCHAR(100),
    user_role VARCHAR(50),
    device_type VARCHAR(50),
    threat_category VARCHAR(50),
    description VARCHAR(1000),
    result_threat_id BIGINT NULL,
    recommendations TEXT,                  -- JSON array of AI-recommended response actions
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (performed_by) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (result_threat_id) REFERENCES threats(id) ON DELETE SET NULL,
    INDEX idx_simulation_runs_created_at (created_at)
);

SET FOREIGN_KEY_CHECKS = 1;
