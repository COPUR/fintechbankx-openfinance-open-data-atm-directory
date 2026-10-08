-- DBA bootstrap for svc-of-atm-directory, once per environment, run with the
-- RDS-managed admin credential (Terraform output master_user_secret_arn) while
-- connected to db_of_atm_directory_<env>:
--
--   psql "host=<writer> dbname=db_of_atm_directory_<env> user=atm_admin sslmode=require" \
--        -v ON_ERROR_STOP=1 -f db/bootstrap/bootstrap-roles.sql
--
-- Creates three LOGIN roles without passwords; the DBA then sets each password
-- (psql \password <role>) and stores {"username","password"} in Secrets Manager:
--
--   role                   secret                                   used by
--   atm_directory_migrate  <env>/atm-directory-service/db-migrate   Flyway only (Helm init container); owns the schema
--   atm_directory_app      <env>/atm-directory-service/db-app       the service pods; SELECT on atm, nothing else
--   atm_directory_import   <env>/atm-directory-service/db-import    db/import/import-atms.sh; SELECT/INSERT/UPDATE on atm
--
-- Table privileges are granted by Flyway (V3__grant_least_privilege.sql), which
-- runs as the schema owner. Run this script before the first deploy; it is
-- idempotent and may be re-run.

DO $$
DECLARE
    r text;
BEGIN
    FOREACH r IN ARRAY ARRAY['atm_directory_migrate', 'atm_directory_app', 'atm_directory_import'] LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) THEN
            EXECUTE format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT', r);
        END IF;
    END LOOP;
END
$$;

DO $$
BEGIN
    EXECUTE format('REVOKE ALL ON DATABASE %I FROM PUBLIC', current_database());
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO atm_directory_migrate, atm_directory_app, atm_directory_import',
                   current_database());
    -- import-atms.sh stages the CSV in a temporary table.
    EXECUTE format('GRANT TEMPORARY ON DATABASE %I TO atm_directory_import', current_database());
    -- Flyway (create-schemas) creates sc_of_atm_directory, so the migrate role owns it.
    EXECUTE format('GRANT CREATE ON DATABASE %I TO atm_directory_migrate', current_database());
END
$$;

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
