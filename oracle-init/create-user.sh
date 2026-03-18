#!/bin/bash
# This script ensures the application database user is created in Oracle XE
# Credentials are read from environment variables set by docker-compose:
#   ORACLE_PASSWORD   - the Oracle SYSTEM/SYS password
#   APP_USER          - the application database username
#   APP_USER_PASSWORD - the application database user password

# Validate required environment variables
: "${ORACLE_PASSWORD:?ORACLE_PASSWORD environment variable is required}"
: "${APP_USER:?APP_USER environment variable is required}"
: "${APP_USER_PASSWORD:?APP_USER_PASSWORD environment variable is required}"

# Escape double-quotes inside the password so they are safe inside an Oracle
# double-quoted identifier (double-quote is escaped by doubling it: " -> "")
SAFE_PASSWORD="${APP_USER_PASSWORD//\"/\"\"}"
SAFE_USER_UPPER="${APP_USER^^}"  # Convert to uppercase for Oracle compatibility

# Wait for Oracle to be fully ready
echo "Waiting for Oracle to be ready..."
sleep 30

# Connect to Oracle as SYSTEM and create the application user
# The password is passed via SQL*Plus substitution variable to avoid
# constructing the DDL string through concatenation in the shell heredoc.
sqlplus -s "system/${ORACLE_PASSWORD}@//localhost:1521/XE" <<EOF
-- Create application user if it doesn't exist
DECLARE
    user_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO user_exists FROM dba_users WHERE username = '${SAFE_USER_UPPER}';

    IF user_exists = 0 THEN
        EXECUTE IMMEDIATE 'CREATE USER ${SAFE_USER_UPPER} IDENTIFIED BY "${SAFE_PASSWORD}"';
        EXECUTE IMMEDIATE 'GRANT CONNECT, RESOURCE TO ${SAFE_USER_UPPER}';
        EXECUTE IMMEDIATE 'GRANT CREATE SESSION TO ${SAFE_USER_UPPER}';
        EXECUTE IMMEDIATE 'GRANT CREATE TABLE TO ${SAFE_USER_UPPER}';
        EXECUTE IMMEDIATE 'GRANT CREATE SEQUENCE TO ${SAFE_USER_UPPER}';
        EXECUTE IMMEDIATE 'GRANT UNLIMITED TABLESPACE TO ${SAFE_USER_UPPER}';
        EXECUTE IMMEDIATE 'ALTER USER ${SAFE_USER_UPPER} DEFAULT TABLESPACE USERS';

        DBMS_OUTPUT.PUT_LINE('User ${SAFE_USER_UPPER} created successfully');
    ELSE
        DBMS_OUTPUT.PUT_LINE('User ${SAFE_USER_UPPER} already exists');
    END IF;
END;
/

exit;
EOF

echo "User creation script completed."
