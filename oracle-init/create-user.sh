#!/bin/bash
# This script ensures the application user is created in Oracle XE.
# Credentials are read from environment variables set by docker-compose (via .env).

# Wait for Oracle to be fully ready
echo "Waiting for Oracle to be ready..."
sleep 30

# Use environment variables for credentials (set by docker-compose from .env file)
ORACLE_SYS_PASS="${ORACLE_PASSWORD}"
APP_DB_USER="${APP_USER}"
APP_DB_PASS="${APP_USER_PASSWORD}"

# Validate required environment variables are set
if [ -z "${ORACLE_SYS_PASS}" ] || [ -z "${APP_DB_USER}" ] || [ -z "${APP_DB_PASS}" ]; then
  echo "ERROR: Required environment variables ORACLE_PASSWORD, APP_USER, and APP_USER_PASSWORD must be set."
  exit 1
fi

# Validate APP_DB_USER contains only safe Oracle identifier characters to prevent SQL injection
if ! echo "${APP_DB_USER}" | grep -qE '^[a-zA-Z][a-zA-Z0-9_]{0,29}$'; then
  echo "ERROR: APP_USER must start with a letter and contain only alphanumeric characters or underscores (max 30 chars)."
  exit 1
fi

# Connect to Oracle as SYSTEM and create the application user.
# Variables are expanded by bash before the SQL text is sent to sqlplus (unquoted heredoc delimiter).
# EXECUTE IMMEDIATE uses string concatenation (||) to keep variable values outside
# the Oracle single-quoted string literals, making expansion unambiguous.
sqlplus -s "system/${ORACLE_SYS_PASS}@//localhost:1521/XE" <<EOF
SET SERVEROUTPUT ON
DECLARE
    user_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO user_exists FROM dba_users WHERE username = UPPER('${APP_DB_USER}');

    IF user_exists = 0 THEN
        EXECUTE IMMEDIATE 'CREATE USER ' || '${APP_DB_USER}' || ' IDENTIFIED BY ' || '${APP_DB_PASS}';
        EXECUTE IMMEDIATE 'GRANT CONNECT, RESOURCE TO ' || '${APP_DB_USER}';
        EXECUTE IMMEDIATE 'GRANT CREATE SESSION TO ' || '${APP_DB_USER}';
        EXECUTE IMMEDIATE 'GRANT CREATE TABLE TO ' || '${APP_DB_USER}';
        EXECUTE IMMEDIATE 'GRANT CREATE SEQUENCE TO ' || '${APP_DB_USER}';
        EXECUTE IMMEDIATE 'GRANT UNLIMITED TABLESPACE TO ' || '${APP_DB_USER}';
        EXECUTE IMMEDIATE 'ALTER USER ' || '${APP_DB_USER}' || ' DEFAULT TABLESPACE USERS';

        DBMS_OUTPUT.PUT_LINE('User ${APP_DB_USER} created successfully');
    ELSE
        DBMS_OUTPUT.PUT_LINE('User ${APP_DB_USER} already exists');
    END IF;
END;
/

exit;
EOF

echo "User creation script completed."