#!/usr/bin/env bash
# Rehearses the svc-of-atm-directory data path on a scratch PostgreSQL:
# applies the Flyway migrations (db/migration, then the dev/CI seed in db/seed)
# the way Flyway orders them, imports db/import/example-atms.csv twice (the
# second run must change nothing), re-applies the seed (must not overwrite
# imported rows) and checks that an invalid file is rejected atomically.
# There is no monolith data to backfill (ADR-0001).
#
# Needs psql and a role that can create databases, via the usual PG* env vars
# (PGHOST, PGPORT, PGUSER, PGPASSWORD).
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
db="atm_directory_rehearsal"
schema="sc_of_atm_directory"
resources="$root/src/main/resources"

psql_q() { psql -X -q -v ON_ERROR_STOP=1 "$@"; }
in_schema() { PGOPTIONS="-c search_path=$schema" psql_q -d "$db" "$@"; }

psql_q -d postgres -c "DROP DATABASE IF EXISTS $db" -c "CREATE DATABASE $db"
psql_q -d "$db" -c "CREATE SCHEMA $schema"

# Flyway order: versioned migrations by version, then repeatable ones.
for migration in $(ls "$resources"/db/migration/V*__*.sql | sort -V); do
  echo "--- migrate $(basename "$migration")"
  in_schema -f "$migration"
done
for seed in "$resources"/db/seed/R__*.sql; do
  echo "--- seed $(basename "$seed")"
  in_schema -f "$seed"
done

check() {
  local label="$1" sql="$2" expected="$3" actual
  actual="$(psql -X -At -d "$db" -c "$sql")"
  if [ "$actual" != "$expected" ]; then
    echo "FAIL $label: expected '$expected', got '$actual'" >&2
    exit 1
  fi
  echo "ok   $label"
}

versions="SELECT string_agg(atm_id || ':' || status || ':v' || version, ',' ORDER BY atm_id) FROM $schema.atm"

check "seed loads three sample ATMs" "SELECT count(*) FROM $schema.atm" "3"

for run in 1 2; do
  echo "--- import run $run"
  "$root/db/import/import-atms.sh" "dbname=$db" "$root/db/import/example-atms.csv"
  if [ "$run" = 1 ]; then
    updated_after_first="$(psql -X -At -d "$db" -c "SELECT string_agg(updated_at::text, ',' ORDER BY atm_id) FROM $schema.atm")"
  fi
done

check "changed ATM bumped once, unchanged and new ATMs at version 0" "$versions" \
  "ATM-001:InService:v0,ATM-002:OutOfService:v1,ATM-003:OutOfService:v0,ATM-101:InService:v0,ATM-102:InService:v0"
check "second import changed no timestamps" \
  "SELECT string_agg(updated_at::text, ',' ORDER BY atm_id) FROM $schema.atm" "$updated_after_first"
check "services split into an array" \
  "SELECT array_to_string(services, ',') FROM $schema.atm WHERE atm_id = 'ATM-101'" "CashWithdrawal,CashDeposit,ChequeDeposit"
check "quoted address kept intact" \
  "SELECT address_line FROM $schema.atm WHERE atm_id = 'ATM-101'" "Bay Avenue, Tower B"

echo "--- re-apply seed (dev/CI restart)"
in_schema -f "$resources/db/seed/R__seed_sample_atms.sql"
check "seed never overwrites imported rows" \
  "SELECT status || ':v' || version FROM $schema.atm WHERE atm_id = 'ATM-002'" "OutOfService:v1"

echo "--- import an invalid file (must fail and change nothing)"
if "$root/db/import/import-atms.sh" "dbname=$db" "$root/db/import/test/invalid-atms.csv" 2>/dev/null; then
  echo "FAIL invalid import was accepted" >&2
  exit 1
fi
check "invalid import rolled back as a whole" \
  "SELECT status || ':v' || version || ':' || (SELECT count(*) FROM $schema.atm WHERE atm_id = 'ATM-900') FROM $schema.atm WHERE atm_id = 'ATM-102'" \
  "InService:v0:0"

plan="$(PGOPTIONS="-c enable_seqscan=off" psql -X -At -d "$db" -c "EXPLAIN (COSTS OFF) SELECT atm_id FROM $schema.atm WHERE point(longitude, latitude) <@ box(point(55.0, 24.9), point(55.5, 25.4))")"
if ! grep -q "Index Scan using ix_atm_location" <<<"$plan"; then
  echo "FAIL radius pre-filter does not use the GiST index:" >&2
  echo "$plan" >&2
  exit 1
fi
echo "ok   radius pre-filter uses the GiST index"

psql_q -d postgres -c "DROP DATABASE $db"
echo "Migration, seed and import rehearsal passed."
