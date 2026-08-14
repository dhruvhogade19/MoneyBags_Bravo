--liquibase formatted sql
--changeset moneybags:credit-card-v3
ALTER TABLE CREDIT_CARD_APPLICATION ADD (
    AGE NUMBER(3),
    SALARY NUMBER(19,2)
);

ALTER TABLE CREDIT_CARD_ACCOUNT ADD (
    AGE NUMBER(3),
    SALARY NUMBER(19,2)
);
