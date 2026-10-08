#!/usr/bin/env bash
# Rehearses the svc-of-atm-directory data path on a scratch PostgreSQL:
# applies the Flyway migrations (db/migration) in version order, imports
# db/import/example-atms.csv, then applies the dev/CI seed (db/seed) and asserts
# it left the imported rows untouched, re-imports (must change nothing), imports
# a changed row, re-applies the seed (must not revert it), and checks that files
# with a reserved SAMPLE- id or an invalid row are rejected atomically.
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

migrate() {
  for migration in $(ls "$resources"/db/migration/V*__*.sql | sort -V); do
    echo "--- migrate $(basename "$migration")"
    in_schema -f "$migration"
  done
}
seed() {
  for seed in "$resources"/db/seed/R__*.sql; do
    echo "--- seed $(basename "$seed")"
    in_schema -f "$seed"
  done
}

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
imported_rows="SELECT string_agg(a::text, '|' ORDER BY atm_id) FROM $schema.atm a WHERE atm_id NOT LIKE 'SAMPLE-%'"
sample_rows="SELECT string_agg(a::text, '|' ORDER BY atm_id) FROM $schema.atm a WHERE atm_id LIKE 'SAMPLE-%'"

migrate

echo "--- import run 1 (before any seed)"
"$root/db/import/import-atms.sh" "dbname=$db" "$root/db/import/example-atms.csv"
check "first import inserts the file at version 0" "$versions" \
  "ATM-002:OutOfService:v0,ATM-003:OutOfService:v0,ATM-101:InService:v0,ATM-102:InService:v0"
after_import="$(psql -X -At -d "$db" -c "$imported_rows")"

# Import, then seed, then assert: a dev/CI pod start re-applies the seed after an import.
seed
check "seed adds only its SAMPLE- rows" \
  "SELECT string_agg(atm_id, ',' ORDER BY atm_id) FROM $schema.atm WHERE atm_id LIKE 'SAMPLE-%'" "SAMPLE-001,SAMPLE-002,SAMPLE-003"
check "seed leaves every imported row exactly as imported" "$imported_rows" "$after_import"
after_seed="$(psql -X -At -d "$db" -c "$sample_rows")"

echo "--- import run 2 (same file)"
"$root/db/import/import-atms.sh" "dbname=$db" "$root/db/import/example-atms.csv"
check "second import changed nothing" "$imported_rows" "$after_import"
check "import never touches SAMPLE- rows" "$sample_rows" "$after_seed"
check "services split into an array" \
  "SELECT array_to_string(services, ',') FROM $schema.atm WHERE atm_id = 'ATM-101'" "CashWithdrawal,CashDeposit,ChequeDeposit"
check "quoted address kept intact" \
  "SELECT address_line FROM $schema.atm WHERE atm_id = 'ATM-101'" "Bay Avenue, Tower B"

echo "--- import a changed row"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
{ head -n 1 "$root/db/import/example-atms.csv"
  grep '^ATM-002,' "$root/db/import/example-atms.csv" | sed 's/,OutOfService,/,InService,/'
} > "$work/changed.csv"
"$root/db/import/import-atms.sh" "dbname=$db" "$work/changed.csv"
check "changed ATM bumped once" "SELECT status || ':v' || version FROM $schema.atm WHERE atm_id = 'ATM-002'" "InService:v1"
after_change="$(psql -X -At -d "$db" -c "$imported_rows")"

echo "--- re-apply seed (dev/CI restart after an import)"
seed
check "seed never reverts imported rows" "$imported_rows" "$after_change"
check "seed re-run is a no-op on its own rows" "$sample_rows" "$after_seed"

echo "--- import a file that uses a reserved SAMPLE- id (must fail and change nothing)"
{ head -n 1 "$root/db/import/example-atms.csv"
  echo "SAMPLE-002,Marina ATM,OutOfService,25.0800,55.1400,Dubai Marina Walk,Dubai,AE,Wheelchair,CashWithdrawal,AED"
} > "$work/reserved.csv"
if "$root/db/import/import-atms.sh" "dbname=$db" "$work/reserved.csv" 2>"$work/reserved.err"; then
  echo "FAIL import accepted a SAMPLE- id" >&2
  exit 1
fi
grep -q "reserved SAMPLE- prefix" "$work/reserved.err" || { cat "$work/reserved.err" >&2; exit 1; }
check "reserved-id import rolled back" "$sample_rows" "$after_seed"

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
