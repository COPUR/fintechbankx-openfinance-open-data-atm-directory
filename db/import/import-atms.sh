#!/usr/bin/env bash
# Loads the bank's ATM network into svc-of-atm-directory's own database.
#
#   db/import/import-atms.sh [--full [--max-withdraw-percent N]] <conninfo> <atms.csv>
#
# CSV header (see db/import/example-atms.csv):
#   atm_id,name,status,latitude,longitude,address_line,city,country_code,accessibility,services,currency
# services is a '|'-separated list (CashWithdrawal|CashDeposit).
#
# Idempotent upsert in one transaction: new ATMs are inserted with version 0;
# an existing ATM is updated, with version + 1 and updated_at = now(), only
# when one of its imported fields changed. Re-running the same file changes
# nothing, so the API ETag stays stable. Any invalid row (coordinates outside
# the globe, empty services, bad currency, duplicate atm_id) aborts the whole
# import, and so does an atm_id with the SAMPLE- prefix reserved for the
# dev/CI seed.
#
# Delta mode (default): ATMs missing from the file are left as they are.
# Full mode (--full): the file is the complete, signed-off network. Every listed
# ATM missing from it gets status Withdrawn (version + 1, updated_at = now()), and
# the API stops listing it; an ATM that reappears in a later file is listed again.
# SAMPLE- rows are never touched. As a guard against a truncated export, the
# whole import is rolled back if it would withdraw more than N percent of the
# listed ATMs (--max-withdraw-percent, default 10).
#
# Runs as the import role atm_directory_import (SELECT/INSERT/UPDATE on atm only;
# secret <env>/atm-directory-service/db-import). Every inserted or updated row is
# recorded in atm_history with the login role, application name and time.
# Example conninfo: "host=<aurora-writer> dbname=db_of_atm_directory_dev user=atm_directory_import sslmode=require".
# Passwords come from PGPASSWORD or ~/.pgpass, never from arguments.
set -euo pipefail

usage() { echo "usage: $0 [--full [--max-withdraw-percent N]] <conninfo> <atms.csv>" >&2; exit 2; }
mode=delta
max_withdraw_percent=10
while [ "$#" -gt 0 ]; do
  case "$1" in
    --full) mode=full; shift ;;
    --max-withdraw-percent) [ "$#" -ge 2 ] || usage; max_withdraw_percent="$2"; shift 2 ;;
    --) shift; break ;;
    -*) usage ;;
    *) break ;;
  esac
done
[ "$#" -eq 2 ] || usage
case "$max_withdraw_percent" in
  ''|*[!0-9]*) echo "--max-withdraw-percent must be an integer from 0 to 100" >&2; exit 2 ;;
esac
[ "$max_withdraw_percent" -le 100 ] || { echo "--max-withdraw-percent must be an integer from 0 to 100" >&2; exit 2; }

# Recorded in sc_of_atm_directory.atm_history.application_name for every row this run changes.
export PGAPPNAME="${PGAPPNAME:-atm-directory-import}"

conninfo="$1"
csv="$2"
schema="sc_of_atm_directory"

if [ ! -r "$csv" ]; then
  echo "cannot read CSV file '$csv'" >&2
  exit 2
fi
case "$csv" in
  *"'"*) echo "CSV path must not contain a single quote" >&2; exit 2 ;;
esac
expected_header="atm_id,name,status,latitude,longitude,address_line,city,country_code,accessibility,services,currency"
actual_header="$(head -n 1 "$csv" | tr -d '\r')"
if [ "$actual_header" != "$expected_header" ]; then
  echo "unexpected CSV header:" >&2
  echo "  got:      $actual_header" >&2
  echo "  expected: $expected_header" >&2
  exit 2
fi

# Full mode SQL, built before the main heredoc so its dollar quotes stay literal.
full_mode_sql=""
if [ "$mode" = full ]; then
  full_mode_sql="$(sed -e "s/@SCHEMA@/$schema/g" -e "s/@MAX@/$max_withdraw_percent/g" <<'FULL'

-- Full mode: withdraw every listed, non-sample ATM that is missing from the file.
CREATE TEMP TABLE atm_withdrawn (atm_id text) ON COMMIT DROP;
WITH withdrawn AS (
    UPDATE @SCHEMA@.atm a
    SET status = 'Withdrawn', updated_at = now(), version = a.version + 1
    WHERE a.status <> 'Withdrawn'
      AND a.atm_id NOT LIKE 'SAMPLE-%'
      AND NOT EXISTS (SELECT 1 FROM atm_import i WHERE btrim(i.atm_id) = a.atm_id)
    RETURNING a.atm_id
)
INSERT INTO atm_withdrawn SELECT atm_id FROM withdrawn;

DO $$
DECLARE withdrawn bigint; listed_before bigint;
BEGIN
    SELECT count(*) INTO withdrawn FROM atm_withdrawn;
    SELECT count(*) + withdrawn INTO listed_before
    FROM @SCHEMA@.atm WHERE status <> 'Withdrawn' AND atm_id NOT LIKE 'SAMPLE-%';
    IF withdrawn * 100 > @MAX@ * listed_before THEN
        RAISE EXCEPTION 'full import would withdraw % of % listed ATMs, more than @MAX@ percent; nothing was changed (check the export, or raise --max-withdraw-percent)',
            withdrawn, listed_before;
    END IF;
END
$$;

SELECT count(*) AS withdrawn FROM atm_withdrawn;
FULL
)"
fi

psql -X -q -v ON_ERROR_STOP=1 --single-transaction "$conninfo" <<SQL
CREATE TEMP TABLE atm_import (
    atm_id        text,
    name          text,
    status        text,
    latitude      double precision,
    longitude     double precision,
    address_line  text,
    city          text,
    country_code  text,
    accessibility text,
    services      text,
    currency      text
) ON COMMIT DROP;

\copy atm_import FROM '$csv' WITH (FORMAT csv, HEADER true)

DO \$\$
DECLARE duplicate text; reserved text;
BEGIN
    SELECT string_agg(atm_id, ', ') INTO duplicate
    FROM (SELECT atm_id FROM atm_import GROUP BY atm_id HAVING count(*) > 1) d;
    IF duplicate IS NOT NULL THEN
        RAISE EXCEPTION 'duplicate atm_id in import file: %', duplicate;
    END IF;
    -- SAMPLE- ids belong to the dev/CI seed (db/seed); the real network never uses them.
    SELECT string_agg(atm_id, ', ') INTO reserved FROM atm_import WHERE upper(btrim(atm_id)) LIKE 'SAMPLE-%';
    IF reserved IS NOT NULL THEN
        RAISE EXCEPTION 'atm_id uses the reserved SAMPLE- prefix of the dev/CI seed: %', reserved;
    END IF;
END
\$\$;

WITH upserted AS (
    INSERT INTO $schema.atm AS a (atm_id, name, status, latitude, longitude, address_line, city,
                                  country_code, accessibility, services, currency, updated_at, version)
    SELECT btrim(atm_id), btrim(name), btrim(status), latitude, longitude, btrim(address_line), btrim(city),
           upper(btrim(country_code)), btrim(accessibility),
           array_remove(string_to_array(btrim(services), '|'), ''), upper(btrim(currency)), now(), 0
    FROM atm_import
    ON CONFLICT (atm_id) DO UPDATE SET
        name = EXCLUDED.name,
        status = EXCLUDED.status,
        latitude = EXCLUDED.latitude,
        longitude = EXCLUDED.longitude,
        address_line = EXCLUDED.address_line,
        city = EXCLUDED.city,
        country_code = EXCLUDED.country_code,
        accessibility = EXCLUDED.accessibility,
        services = EXCLUDED.services,
        currency = EXCLUDED.currency,
        updated_at = now(),
        version = a.version + 1
    WHERE (a.name, a.status, a.latitude, a.longitude, a.address_line, a.city,
           a.country_code, a.accessibility, a.services, a.currency)
          IS DISTINCT FROM
          (EXCLUDED.name, EXCLUDED.status, EXCLUDED.latitude, EXCLUDED.longitude, EXCLUDED.address_line,
           EXCLUDED.city, EXCLUDED.country_code, EXCLUDED.accessibility, EXCLUDED.services, EXCLUDED.currency)
    RETURNING (xmax = 0) AS inserted
)
SELECT (SELECT count(*) FROM atm_import) AS rows_in_file,
       count(*) FILTER (WHERE inserted) AS inserted,
       count(*) FILTER (WHERE NOT inserted) AS updated,
       (SELECT count(*) FROM atm_import) - count(*) AS unchanged
FROM upserted;
$full_mode_sql
SQL
echo "ATM import committed ($mode)."
