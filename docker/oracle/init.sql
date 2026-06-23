-- Runs inside the XEPDB1 pluggable database after Oracle XE starts.
-- Creates the application schema user and grants required privileges.

ALTER SESSION SET CONTAINER = XEPDB1;

CREATE USER hapifhir IDENTIFIED BY "BiGw5TvDCd28CbuZAe3U"
    DEFAULT TABLESPACE USERS
    TEMPORARY TABLESPACE TEMP
    QUOTA UNLIMITED ON USERS;

GRANT CONNECT, RESOURCE TO hapifhir;
GRANT CREATE SESSION TO hapifhir;
GRANT UNLIMITED TABLESPACE TO hapifhir;
