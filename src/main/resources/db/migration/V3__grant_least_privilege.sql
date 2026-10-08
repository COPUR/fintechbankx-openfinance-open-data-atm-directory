-- Least-privilege grants for the roles created by db/bootstrap/bootstrap-roles.sql.
-- Runs as the schema owner (atm_directory_migrate). Idempotent: if a role was
-- created after this migration ran, re-run this file with psql as the owner.
-- A role that does not exist (local and CI databases) is skipped with a notice.
--
--   atm_directory_app     USAGE on the schema, SELECT on atm. No access to atm_history.
--   atm_directory_import  USAGE on the schema, SELECT/INSERT/UPDATE on atm (SELECT is needed
--                         by INSERT .. ON CONFLICT DO UPDATE .. WHERE and the full-mode withdrawal).
--                         No DELETE; history is written by the SECURITY DEFINER trigger.

DO $$
DECLARE
    s text := current_schema();
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'atm_directory_app') THEN
        EXECUTE format('GRANT USAGE ON SCHEMA %I TO atm_directory_app', s);
        EXECUTE format('GRANT SELECT ON %I.atm TO atm_directory_app', s);
    ELSE
        RAISE NOTICE 'role atm_directory_app does not exist; grants skipped';
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'atm_directory_import') THEN
        EXECUTE format('GRANT USAGE ON SCHEMA %I TO atm_directory_import', s);
        EXECUTE format('GRANT SELECT, INSERT, UPDATE ON %I.atm TO atm_directory_import', s);
    ELSE
        RAISE NOTICE 'role atm_directory_import does not exist; grants skipped';
    END IF;
END
$$;
