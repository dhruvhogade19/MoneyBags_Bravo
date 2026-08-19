-- Adds the lifecycle column for environments created before expiry-aware
-- idempotency was introduced. Existing records are backfilled by Oracle's
-- SYSTIMESTAMP default; new records receive the explicit service expiry time.
ALTER TABLE IDEMPOTENCY_RECORD
    ADD (EXPIRES_AT TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL);
