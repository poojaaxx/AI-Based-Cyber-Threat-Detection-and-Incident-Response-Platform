-- Phase 3A only. Run with the CyberGuard database selected before backend restart.
-- Idempotent; preserves existing data and does not label historical predictions.
ALTER TABLE threats MODIFY COLUMN confidence_score DECIMAL(5,2) NULL;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='login_attempts' AND column_name='outcome')=0, 'ALTER TABLE login_attempts ADD COLUMN outcome VARCHAR(30) NULL', 'SELECT 1');
PREPARE phase3a FROM @ddl; EXECUTE phase3a; DEALLOCATE PREPARE phase3a;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='threats' AND column_name='detector_type')=0, 'ALTER TABLE threats ADD COLUMN detector_type VARCHAR(20) NULL', 'SELECT 1');
PREPARE phase3a FROM @ddl; EXECUTE phase3a; DEALLOCATE PREPARE phase3a;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='threats' AND column_name='detection_key')=0, 'ALTER TABLE threats ADD COLUMN detection_key VARCHAR(100) NULL', 'SELECT 1');
PREPARE phase3a FROM @ddl; EXECUTE phase3a; DEALLOCATE PREPARE phase3a;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='threats' AND column_name='login_attempt_id')=0, 'ALTER TABLE threats ADD COLUMN login_attempt_id BIGINT NULL', 'SELECT 1');
PREPARE phase3a FROM @ddl; EXECUTE phase3a; DEALLOCATE PREPARE phase3a;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='threats' AND column_name='rule_evidence')=0, 'ALTER TABLE threats ADD COLUMN rule_evidence TEXT NULL', 'SELECT 1');
PREPARE phase3a FROM @ddl; EXECUTE phase3a; DEALLOCATE PREPARE phase3a;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='threats' AND column_name='detection_key' AND non_unique=0)=0, 'CREATE UNIQUE INDEX uq_threat_detection_key ON threats(detection_key)', 'SELECT 1');
PREPARE phase3a FROM @ddl; EXECUTE phase3a; DEALLOCATE PREPARE phase3a;
CREATE TABLE IF NOT EXISTS security_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    login_attempt_id BIGINT NOT NULL UNIQUE,
    source VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    username VARCHAR(255),
    source_ip VARCHAR(255),
    outcome VARCHAR(255) NOT NULL,
    detector_type VARCHAR(255),
    result VARCHAR(255) NOT NULL,
    observed_at DATETIME(6) NOT NULL,
    ingested_at DATETIME(6) NOT NULL,
    detected_at DATETIME(6),
    processing_latency_ms BIGINT,
    threat_id BIGINT,
    incident_id BIGINT,
    evidence TEXT,
    INDEX idx_security_events_observed (observed_at),
    FOREIGN KEY (login_attempt_id) REFERENCES login_attempts(id),
    FOREIGN KEY (threat_id) REFERENCES threats(id),
    FOREIGN KEY (incident_id) REFERENCES incidents(id)
);
