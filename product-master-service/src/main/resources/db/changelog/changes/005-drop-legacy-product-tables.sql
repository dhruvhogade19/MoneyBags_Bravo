--liquibase formatted sql

-- The legacy generic model is intentionally retired after the replacement catalogue is seeded.
-- preConditions make this safe for a new database that never had the old PRODUCT tables.

--changeset moneybags:catalog-005-drop-legacy-interest-rule
--preconditions onFail:MARK_RAN onError:HALT
--precondition-table-exists table:PRODUCT_INTEREST_RULE
DROP TABLE PRODUCT_INTEREST_RULE CASCADE CONSTRAINTS;

--changeset moneybags:catalog-006-drop-legacy-product
--preconditions onFail:MARK_RAN onError:HALT
--precondition-table-exists table:PRODUCT
DROP TABLE PRODUCT CASCADE CONSTRAINTS;
