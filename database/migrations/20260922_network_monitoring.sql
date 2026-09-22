-- Phase 3B. Select the application database first. Idempotent; existing rows are preserved.
ALTER TABLE security_events MODIFY COLUMN login_attempt_id BIGINT NULL;
ALTER TABLE network_events MODIFY COLUMN source_ip VARCHAR(45) NULL;
ALTER TABLE network_events MODIFY COLUMN destination_ip VARCHAR(45) NULL;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='network_events' AND column_name='local_ip')=0, 'ALTER TABLE network_events ADD COLUMN local_ip VARCHAR(255) NULL', 'SELECT 1');
PREPARE phase3b FROM @ddl; EXECUTE phase3b; DEALLOCATE PREPARE phase3b;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='network_events' AND column_name='local_port')=0, 'ALTER TABLE network_events ADD COLUMN local_port INT NULL', 'SELECT 1');
PREPARE phase3b FROM @ddl; EXECUTE phase3b; DEALLOCATE PREPARE phase3b;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='network_events' AND column_name='remote_ip')=0, 'ALTER TABLE network_events ADD COLUMN remote_ip VARCHAR(255) NULL', 'SELECT 1');
PREPARE phase3b FROM @ddl; EXECUTE phase3b; DEALLOCATE PREPARE phase3b;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='network_events' AND column_name='remote_port')=0, 'ALTER TABLE network_events ADD COLUMN remote_port INT NULL', 'SELECT 1');
PREPARE phase3b FROM @ddl; EXECUTE phase3b; DEALLOCATE PREPARE phase3b;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='network_events' AND column_name='tcp_state')=0, 'ALTER TABLE network_events ADD COLUMN tcp_state VARCHAR(255) NULL', 'SELECT 1');
PREPARE phase3b FROM @ddl; EXECUTE phase3b; DEALLOCATE PREPARE phase3b;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='network_events' AND column_name='process_id')=0, 'ALTER TABLE network_events ADD COLUMN process_id BIGINT NULL', 'SELECT 1');
PREPARE phase3b FROM @ddl; EXECUTE phase3b; DEALLOCATE PREPARE phase3b;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='network_events' AND column_name='process_name')=0, 'ALTER TABLE network_events ADD COLUMN process_name VARCHAR(255) NULL', 'SELECT 1');
PREPARE phase3b FROM @ddl; EXECUTE phase3b; DEALLOCATE PREPARE phase3b;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='network_events' AND column_name='connection_created_at')=0, 'ALTER TABLE network_events ADD COLUMN connection_created_at DATETIME(6) NULL', 'SELECT 1');
PREPARE phase3b FROM @ddl; EXECUTE phase3b; DEALLOCATE PREPARE phase3b;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='network_events' AND column_name='observed_at')=0, 'ALTER TABLE network_events ADD COLUMN observed_at DATETIME(6) NULL', 'SELECT 1');
PREPARE phase3b FROM @ddl; EXECUTE phase3b; DEALLOCATE PREPARE phase3b;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='network_events' AND column_name='ingested_at')=0, 'ALTER TABLE network_events ADD COLUMN ingested_at DATETIME(6) NULL', 'SELECT 1');
PREPARE phase3b FROM @ddl; EXECUTE phase3b; DEALLOCATE PREPARE phase3b;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='network_events' AND column_name='collector_id')=0, 'ALTER TABLE network_events ADD COLUMN collector_id VARCHAR(60) NULL', 'SELECT 1');
PREPARE phase3b FROM @ddl; EXECUTE phase3b; DEALLOCATE PREPARE phase3b;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='network_events' AND column_name='session_id')=0, 'ALTER TABLE network_events ADD COLUMN session_id VARCHAR(36) NULL', 'SELECT 1');
PREPARE phase3b FROM @ddl; EXECUTE phase3b; DEALLOCATE PREPARE phase3b;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='network_events' AND column_name='sequence_number')=0, 'ALTER TABLE network_events ADD COLUMN sequence_number BIGINT NULL', 'SELECT 1');
PREPARE phase3b FROM @ddl; EXECUTE phase3b; DEALLOCATE PREPARE phase3b;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='network_events' AND column_name='connection_id')=0, 'ALTER TABLE network_events ADD COLUMN connection_id VARCHAR(36) NULL', 'SELECT 1');
PREPARE phase3b FROM @ddl; EXECUTE phase3b; DEALLOCATE PREPARE phase3b;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='network_events' AND column_name='event_type')=0, 'ALTER TABLE network_events ADD COLUMN event_type VARCHAR(255) NULL', 'SELECT 1');
PREPARE phase3b FROM @ddl; EXECUTE phase3b; DEALLOCATE PREPARE phase3b;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='network_events' AND column_name='rule_id')=0, 'ALTER TABLE network_events ADD COLUMN rule_id VARCHAR(255) NULL', 'SELECT 1');
PREPARE phase3b FROM @ddl; EXECUTE phase3b; DEALLOCATE PREPARE phase3b;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='security_events' AND column_name='network_event_id')=0, 'ALTER TABLE security_events ADD COLUMN network_event_id BIGINT NULL', 'SELECT 1');
PREPARE phase3b FROM @ddl; EXECUTE phase3b; DEALLOCATE PREPARE phase3b;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='network_events' AND index_name='uq_network_delivery')=0, 'CREATE UNIQUE INDEX uq_network_delivery ON network_events(collector_id,session_id,sequence_number)', 'SELECT 1');
PREPARE phase3b FROM @ddl; EXECUTE phase3b; DEALLOCATE PREPARE phase3b;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='security_events' AND index_name='uq_security_network')=0, 'CREATE UNIQUE INDEX uq_security_network ON security_events(network_event_id)', 'SELECT 1');
PREPARE phase3b FROM @ddl; EXECUTE phase3b; DEALLOCATE PREPARE phase3b;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name='security_events' AND column_name='network_event_id' AND referenced_table_name='network_events')=0, 'ALTER TABLE security_events ADD CONSTRAINT fk_security_network FOREIGN KEY (network_event_id) REFERENCES network_events(id)', 'SELECT 1');
PREPARE phase3b FROM @ddl; EXECUTE phase3b; DEALLOCATE PREPARE phase3b;
CREATE TABLE IF NOT EXISTS collector_state (
 collector_id VARCHAR(60) PRIMARY KEY, session_id VARCHAR(36),
 session_started_at DATETIME(6), last_heartbeat_at DATETIME(6),
 last_successful_sample_at DATETIME(6), sample_error VARCHAR(255), delivery_error VARCHAR(255), status VARCHAR(255),
 queued_events BIGINT NOT NULL DEFAULT 0, dropped_events BIGINT NOT NULL DEFAULT 0,
 received_events BIGINT NOT NULL DEFAULT 0, gap_count BIGINT NOT NULL DEFAULT 0
);

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='collector_state' AND column_name='delivery_error')=0, 'ALTER TABLE collector_state ADD COLUMN delivery_error VARCHAR(255) NULL', 'SELECT 1');
PREPARE phase3b FROM @ddl; EXECUTE phase3b; DEALLOCATE PREPARE phase3b;
