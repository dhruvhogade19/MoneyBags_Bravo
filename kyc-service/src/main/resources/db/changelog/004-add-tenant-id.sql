--liquibase formatted sql

--changeset moneybags:004-add-tenant-id
ALTER TABLE kyc ADD tenant_id VARCHAR2(64) DEFAULT 'moneybags' NOT NULL;
CREATE INDEX idx_kyc_tenant_status ON kyc(tenant_id, kyc_status);

--rollback DROP INDEX idx_kyc_tenant_status;
--rollback ALTER TABLE kyc DROP COLUMN tenant_id;
