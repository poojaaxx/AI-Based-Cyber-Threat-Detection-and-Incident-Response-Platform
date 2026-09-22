-- Run against the application's selected MySQL database before starting the backend.
-- Safe to rerun, including after Hibernate update has added locked_until.
SET @lock_column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'locked_until'
);
SET @lock_column_ddl = IF(@lock_column_exists = 0,
    'ALTER TABLE users ADD COLUMN locked_until DATETIME NULL', 'SELECT 1');
PREPARE lock_column_statement FROM @lock_column_ddl;
EXECUTE lock_column_statement;
DEALLOCATE PREPARE lock_column_statement;
ALTER TABLE incidents MODIFY COLUMN reported_by BIGINT NULL;
