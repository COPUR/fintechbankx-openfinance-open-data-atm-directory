#!/usr/bin/env bash
# Loads the bank's ATM network into svc-of-atm-directory's own database.
#
#   db/import/import-atms.sh <conninfo> <atms.csv>
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
# dev/CI seed. ATMs missing from the file are left as they are: decommission an
# ATM by importing it with its new status.
#
# Runs as the import role atm_directory_import (SELECT/INSERT/UPDATE on atm only;
# secret <env>/atm-directory-service/db-import). Every inserted or updated row is
# recorded in atm_history with the login role, application name and time.
# Example conninfo: "host=<aurora-writer> dbname=db_of_atm_directory_dev user=atm_directory_import sslmode=require".
# Passwords come from PGPASSWORD or ~/.pgpass, never from arguments.
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <conninfo> <atms.csv>" >&2
  exit 2
fi

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
SQL
echo "ATM import committed."
