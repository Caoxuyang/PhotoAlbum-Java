#!/bin/bash
# This script runs automatically when the Oracle container starts.
# It creates the application user and grants the necessary privileges.
# Credentials are read from environment variables set by docker-compose (via .env).

# Validate required environment variables are set
if [ -z "${ORACLE_PASSWORD}" ] || [ -z "${APP_USER}" ] || [ -z "${APP_USER_PASSWORD}" ]; then
  echo "ERROR: Required environment variables ORACLE_PASSWORD, APP_USER, and APP_USER_PASSWORD must be set."
  exit 1
fi

# Validate APP_USER contains only safe Oracle identifier characters to prevent SQL injection
if ! echo "${APP_USER}" | grep -qE '^[a-zA-Z][a-zA-Z0-9_]{0,29}$'; then
  echo "ERROR: APP_USER must start with a letter and contain only alphanumeric characters or underscores (max 30 chars)."
  exit 1
fi

# Connect to Oracle as SYSTEM and create the application user with required privileges.
# Variables are expanded by bash before SQL is sent to sqlplus (unquoted heredoc delimiter).
# EXECUTE IMMEDIATE uses string concatenation (||) so variable values stay outside
# Oracle single-quoted literals, avoiding any SQL injection risk.
sqlplus -s "system/${ORACLE_PASSWORD}@//localhost:1521/FREEPDB1" <<EOF
ALTER SESSION SET "_ORACLE_SCRIPT"=true;

DECLARE
    user_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO user_exists FROM dba_users WHERE username = UPPER('${APP_USER}');

    IF user_exists = 0 THEN
        EXECUTE IMMEDIATE 'CREATE USER ' || '${APP_USER}' || ' IDENTIFIED BY ' || '${APP_USER_PASSWORD}';
        EXECUTE IMMEDIATE 'GRANT CONNECT TO ' || '${APP_USER}';
        EXECUTE IMMEDIATE 'GRANT RESOURCE TO ' || '${APP_USER}';
        EXECUTE IMMEDIATE 'GRANT DBA TO ' || '${APP_USER}';
        EXECUTE IMMEDIATE 'GRANT CREATE SESSION TO ' || '${APP_USER}';
        EXECUTE IMMEDIATE 'GRANT CREATE TABLE TO ' || '${APP_USER}';
        EXECUTE IMMEDIATE 'GRANT CREATE SEQUENCE TO ' || '${APP_USER}';
        EXECUTE IMMEDIATE 'GRANT CREATE VIEW TO ' || '${APP_USER}';
        EXECUTE IMMEDIATE 'GRANT CREATE PROCEDURE TO ' || '${APP_USER}';
        EXECUTE IMMEDIATE 'GRANT CREATE TRIGGER TO ' || '${APP_USER}';
        EXECUTE IMMEDIATE 'GRANT CREATE TYPE TO ' || '${APP_USER}';
        EXECUTE IMMEDIATE 'GRANT CREATE SYNONYM TO ' || '${APP_USER}';
        EXECUTE IMMEDIATE 'GRANT UNLIMITED TABLESPACE TO ' || '${APP_USER}';
        EXECUTE IMMEDIATE 'GRANT SELECT ANY DICTIONARY TO ' || '${APP_USER}';
        EXECUTE IMMEDIATE 'GRANT CREATE ANY INDEX TO ' || '${APP_USER}';
        EXECUTE IMMEDIATE 'GRANT ALTER ANY INDEX TO ' || '${APP_USER}';
        EXECUTE IMMEDIATE 'GRANT DROP ANY INDEX TO ' || '${APP_USER}';
        EXECUTE IMMEDIATE 'ALTER USER ' || '${APP_USER}' || ' DEFAULT TABLESPACE USERS';
        EXECUTE IMMEDIATE 'ALTER USER ' || '${APP_USER}' || ' TEMPORARY TABLESPACE TEMP';
        DBMS_OUTPUT.PUT_LINE('User ${APP_USER} created successfully');
    ELSE
        DBMS_OUTPUT.PUT_LINE('User ${APP_USER} already exists');
    END IF;
END;
/

COMMIT;
EXIT;
EOF
