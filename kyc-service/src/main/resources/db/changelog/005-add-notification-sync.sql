--liquibase formatted sql

--changeset moneybags:005-add-notification-sync
ALTER TABLE kyc ADD notification_sync_status VARCHAR2(20) DEFAULT 'NOT_REQUIRED' NOT NULL;
ALTER TABLE kyc ADD notification_retry_count NUMBER(2) DEFAULT 0 NOT NULL;
ALTER TABLE kyc ADD last_notification_attempt_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE kyc ADD last_notification_error CLOB;
ALTER TABLE kyc ADD notification_sent_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE kyc ADD CONSTRAINT chk_kyc_notification_status
    CHECK (notification_sync_status IN ('NOT_REQUIRED', 'PENDING', 'SENT', 'FAILED'));
ALTER TABLE kyc ADD CONSTRAINT chk_kyc_notification_retry CHECK (notification_retry_count BETWEEN 0 AND 5);
CREATE INDEX idx_kyc_notification_retry ON kyc(notification_sync_status, notification_retry_count);

--rollback DROP INDEX idx_kyc_notification_retry;
--rollback ALTER TABLE kyc DROP CONSTRAINT chk_kyc_notification_retry;
--rollback ALTER TABLE kyc DROP CONSTRAINT chk_kyc_notification_status;
--rollback ALTER TABLE kyc DROP COLUMN notification_sent_at;
--rollback ALTER TABLE kyc DROP COLUMN last_notification_error;
--rollback ALTER TABLE kyc DROP COLUMN last_notification_attempt_at;
--rollback ALTER TABLE kyc DROP COLUMN notification_retry_count;
--rollback ALTER TABLE kyc DROP COLUMN notification_sync_status;
