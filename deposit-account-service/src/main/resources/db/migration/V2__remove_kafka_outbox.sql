--liquibase formatted sql

--changeset moneybags:002-remove-kafka-outbox
DROP TABLE OUTBOX_EVENT CASCADE CONSTRAINTS;
