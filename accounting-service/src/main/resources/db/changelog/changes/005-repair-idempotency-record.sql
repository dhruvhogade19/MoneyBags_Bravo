--liquibase formatted sql

--changeset moneybags:accounting-005 splitStatements:false
DECLARE
    PROCEDURE add_column_if_missing(p_column_name VARCHAR2, p_definition VARCHAR2) IS
        column_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO column_count
          FROM USER_TAB_COLUMNS
         WHERE TABLE_NAME = 'IDEMPOTENCY_RECORD'
           AND COLUMN_NAME = p_column_name;
        IF column_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE IDEMPOTENCY_RECORD ADD (' || p_column_name || ' ' || p_definition || ')';
        END IF;
    END;
BEGIN
    -- The shared table predates Accounting's scoped idempotency contract.
    -- Add only absent fields so existing idempotency records are preserved.
    add_column_if_missing('RECORD_ID', 'VARCHAR2(36)');
    add_column_if_missing('SCOPE', 'VARCHAR2(100)');
    add_column_if_missing('KEY_HASH', 'VARCHAR2(64)');
    add_column_if_missing('REQUEST_HASH', 'VARCHAR2(64)');
    add_column_if_missing('RESOURCE_ID', 'VARCHAR2(100)');
    add_column_if_missing('HTTP_STATUS', 'NUMBER(3)');
    add_column_if_missing('RESPONSE_BODY', 'CLOB');
    add_column_if_missing('CREATED_AT', 'TIMESTAMP(6) WITH TIME ZONE');

    EXECUTE IMMEDIATE 'UPDATE IDEMPOTENCY_RECORD SET RECORD_ID = RAWTOHEX(SYS_GUID()) WHERE RECORD_ID IS NULL';
    EXECUTE IMMEDIATE 'UPDATE IDEMPOTENCY_RECORD SET SCOPE = ''LEGACY'' WHERE SCOPE IS NULL';
    EXECUTE IMMEDIATE 'UPDATE IDEMPOTENCY_RECORD SET KEY_HASH = RAWTOHEX(SYS_GUID()) WHERE KEY_HASH IS NULL';
    EXECUTE IMMEDIATE 'UPDATE IDEMPOTENCY_RECORD SET REQUEST_HASH = ''LEGACY'' WHERE REQUEST_HASH IS NULL';
    EXECUTE IMMEDIATE 'UPDATE IDEMPOTENCY_RECORD SET HTTP_STATUS = 200 WHERE HTTP_STATUS IS NULL';
    EXECUTE IMMEDIATE 'UPDATE IDEMPOTENCY_RECORD SET RESPONSE_BODY = TO_CLOB(''{}'') WHERE RESPONSE_BODY IS NULL';
    EXECUTE IMMEDIATE 'UPDATE IDEMPOTENCY_RECORD SET CREATED_AT = SYSTIMESTAMP WHERE CREATED_AT IS NULL';
END;
/
