-- Append-only change history of the imported ATM directory (additive; V1 unchanged).
-- One row per inserted or updated atm row: old and new row, the login role
-- (session_user), application_name and time. Public reference data, no personal data.
--
-- The trigger function is SECURITY DEFINER (owned by the schema owner), so the
-- import role writes history without any privilege on atm_history. Inside such a
-- function current_user is the owner, so the login role is taken from session_user.

CREATE TABLE atm_history (
    history_id        BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    atm_id            VARCHAR(64)  NOT NULL,
    operation         VARCHAR(6)   NOT NULL,
    old_row           JSONB,
    new_row           JSONB        NOT NULL,
    changed_by        TEXT         NOT NULL,
    application_name  TEXT         NOT NULL,
    changed_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_atm_history_operation CHECK (operation IN ('INSERT', 'UPDATE')),
    CONSTRAINT ck_atm_history_old_row CHECK ((operation = 'INSERT') = (old_row IS NULL))
);

CREATE INDEX ix_atm_history_atm ON atm_history (atm_id, changed_at);

CREATE FUNCTION atm_record_history() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS
$$
BEGIN
    EXECUTE format('INSERT INTO %I.atm_history (atm_id, operation, old_row, new_row, changed_by, application_name, changed_at)'
                   ' VALUES ($1, $2, $3, $4, $5, $6, $7)', TG_TABLE_SCHEMA)
        USING NEW.atm_id, TG_OP,
              CASE WHEN TG_OP = 'UPDATE' THEN to_jsonb(OLD) END, to_jsonb(NEW),
              session_user::text, coalesce(nullif(current_setting('application_name', true), ''), 'unknown'),
              clock_timestamp();
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_atm_history
    AFTER INSERT OR UPDATE ON atm
    FOR EACH ROW EXECUTE FUNCTION atm_record_history();

CREATE FUNCTION atm_history_reject_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'atm_history is append-only';
END;
$$;

CREATE TRIGGER trg_atm_history_append_only
    BEFORE UPDATE OR DELETE ON atm_history
    FOR EACH ROW EXECUTE FUNCTION atm_history_reject_change();

CREATE TRIGGER trg_atm_history_no_truncate
    BEFORE TRUNCATE ON atm_history
    FOR EACH STATEMENT EXECUTE FUNCTION atm_history_reject_change();

REVOKE ALL ON FUNCTION atm_record_history() FROM PUBLIC;
REVOKE ALL ON FUNCTION atm_history_reject_change() FROM PUBLIC;

COMMENT ON TABLE atm_history IS
    'Append-only audit trail of atm inserts and updates (who: session_user, via: application_name, when). Public data, no personal data.';
