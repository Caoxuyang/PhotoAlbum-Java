-- This script runs automatically when Oracle Free container starts
-- The user is created automatically by the gvenzl/oracle-free image using
-- the APP_USER and APP_USER_PASSWORD environment variables.
-- This script only grants additional privileges to that pre-created user.

ALTER SESSION SET "_ORACLE_SCRIPT"=true;

-- Grant system privileges to the application user
-- The APP_USER variable is substituted by the calling shell script via SQL*Plus -v flag
GRANT CONNECT TO &&APP_USER;
GRANT RESOURCE TO &&APP_USER;
GRANT DBA TO &&APP_USER;
GRANT CREATE SESSION TO &&APP_USER;
GRANT CREATE TABLE TO &&APP_USER;
GRANT CREATE SEQUENCE TO &&APP_USER;
GRANT CREATE VIEW TO &&APP_USER;
GRANT CREATE PROCEDURE TO &&APP_USER;
GRANT CREATE TRIGGER TO &&APP_USER;
GRANT CREATE TYPE TO &&APP_USER;
GRANT CREATE SYNONYM TO &&APP_USER;
GRANT UNLIMITED TABLESPACE TO &&APP_USER;

-- Grant object privileges needed by Hibernate
GRANT SELECT ANY DICTIONARY TO &&APP_USER;
GRANT CREATE ANY INDEX TO &&APP_USER;
GRANT ALTER ANY INDEX TO &&APP_USER;
GRANT DROP ANY INDEX TO &&APP_USER;

-- Set default and temporary tablespace
ALTER USER &&APP_USER DEFAULT TABLESPACE USERS;
ALTER USER &&APP_USER TEMPORARY TABLESPACE TEMP;

-- Commit the changes
COMMIT;

EXIT;