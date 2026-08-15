--liquibase formatted sql

--changeset moneybags:003-add-employment-snapshot-columns

ALTER TABLE kyc ADD (
    employment_type VARCHAR2(30),
    salary NUMBER(15,2)
);

ALTER TABLE kyc ADD CONSTRAINT chk_kyc_employment_snapshot
    CHECK (
        (employment_type IS NULL AND salary IS NULL)
        OR (employment_type = 'STUDENT' AND salary IS NULL)
        OR (
            employment_type IN ('BUSINESS', 'SALARIED')
            AND salary IS NOT NULL
            AND salary > 0
        )
    );

--rollback ALTER TABLE kyc DROP CONSTRAINT chk_kyc_employment_snapshot;
--rollback ALTER TABLE kyc DROP COLUMN salary;
--rollback ALTER TABLE kyc DROP COLUMN employment_type;
