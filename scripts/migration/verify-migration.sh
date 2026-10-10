#!/usr/bin/env bash
# Rehearses the svc-of-atm-directory data path on a scratch PostgreSQL:
# applies the Flyway migrations (db/migration) in version order, imports
# db/import/example-atms.csv, then applies the dev/CI seed (db/seed) and asserts
# it left the imported rows untouched, re-imports (must change nothing), imports
# a changed row, re-applies the seed (must not revert it), and checks that files
# with a reserved SAMPLE- id or an invalid row are rejected atomically.
# There is no monolith data to backfill (ADR-0001).
#
# Runs db/bootstrap/bootstrap-roles.sql, then migrates and seeds as
# atm_directory_migrate, imports as atm_directory_import and checks what
# atm_directory_app may do, as in a real environment. Needs psql and an admin
# role that can create databases and roles, reached over TCP via the usual PG*
# env vars (PGHOST, PGPORT, PGUSER, PGPASSWORD). The three roles are dropped
# again at the end unless another database still references them.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
db="atm_directory_rehearsal"
schema="sc_of_atm_directory"
resources="$root/src/main/resources"
: "${PGHOST:?set PGHOST: the roles log in with a password over TCP}"

psql_q() { psql -X -q -v ON_ERROR_STOP=1 "$@"; }
pw_migrate="$(od -An -N16 -tx1 /dev/urandom | tr -d ' \n')"
pw_app="$(od -An -N16 -tx1 /dev/urandom | tr -d ' \n')"
pw_import="$(od -An -N16 -tx1 /dev/urandom | tr -d ' \n')"
# as_role <role> <psql args...>: connect to the rehearsal database as that role.
as_role() {
  local role="$1" pw; shift
  case "$role" in
    atm_directory_migrate) pw="$pw_migrate" ;;
    atm_directory_app) pw="$pw_app" ;;
    atm_directory_import) pw="$pw_import" ;;
  esac
  PGUSER="$role" PGPASSWORD="$pw" PGOPTIONS="-c search_path=$schema" psql_q -d "$db" "$@"
}
# Flyway labels its session atm-directory-flyway (spring.flyway.init-sqls).
in_schema() { PGAPPNAME=atm-directory-flyway as_role atm_directory_migrate "$@"; }
# import_as [options...] <csv>: run the import as atm_directory_import.
import_as() {
  local csv="${*: -1}"
  PGUSER=atm_directory_import PGPASSWORD="$pw_import" "$root/db/import/import-atms.sh" "${@:1:$#-1}" "dbname=$db" "$csv"
}

psql_q -d postgres -c "DROP DATABASE IF EXISTS $db" -c "CREATE DATABASE $db"
echo "--- DBA bootstrap (db/bootstrap/bootstrap-roles.sql)"
psql_q -d "$db" -f "$root/db/bootstrap/bootstrap-roles.sql"
psql_q -d "$db" -c "ALTER ROLE atm_directory_migrate PASSWORD '$pw_migrate'" \
  -c "ALTER ROLE atm_directory_app PASSWORD '$pw_app'" -c "ALTER ROLE atm_directory_import PASSWORD '$pw_import'"
# Flyway (create-schemas) creates the schema as the migrate role.
in_schema -c "CREATE SCHEMA $schema"

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
import_as "$root/db/import/example-atms.csv"
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
import_as "$root/db/import/example-atms.csv"
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
import_as "$work/changed.csv"
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
if import_as "$work/reserved.csv" 2>"$work/reserved.err"; then
  echo "FAIL import accepted a SAMPLE- id" >&2
  exit 1
fi
grep -q "reserved SAMPLE- prefix" "$work/reserved.err" || { cat "$work/reserved.err" >&2; exit 1; }
check "reserved-id import rolled back" "$sample_rows" "$after_seed"

echo "--- import an invalid file (must fail and change nothing)"
if import_as "$root/db/import/test/invalid-atms.csv" 2>/dev/null; then
  echo "FAIL invalid import was accepted" >&2
  exit 1
fi
check "invalid import rolled back as a whole" \
  "SELECT status || ':v' || version || ':' || (SELECT count(*) FROM $schema.atm WHERE atm_id = 'ATM-900') FROM $schema.atm WHERE atm_id = 'ATM-102'" \
  "InService:v0:0"

echo "--- full import (signed-off file is the whole network)"
grep -v '^ATM-102,' "$root/db/import/example-atms.csv" > "$work/full-without-102.csv"
before_full="$(psql -X -At -d "$db" -c "$imported_rows")"
if import_as --full "$work/full-without-102.csv" 2>"$work/full.err"; then
  echo "FAIL full import withdrew 1 of 4 ATMs (25 %) past the default 10 % guard" >&2
  exit 1
fi
grep -q "would withdraw 1 of 4 listed ATMs" "$work/full.err" || { cat "$work/full.err" >&2; exit 1; }
check "guarded full import changed nothing" "$imported_rows" "$before_full"
import_as --full --max-withdraw-percent 25 "$work/full-without-102.csv"
check "ATM missing from the full file is withdrawn, the rest unchanged" "$versions" \
  "ATM-002:OutOfService:v2,ATM-003:OutOfService:v0,ATM-101:InService:v0,ATM-102:Withdrawn:v1,SAMPLE-001:InService:v0,SAMPLE-002:InService:v0,SAMPLE-003:OutOfService:v0"
import_as --full --max-withdraw-percent 25 "$work/full-without-102.csv"
check "repeated full import changes nothing" \
  "SELECT version FROM $schema.atm WHERE atm_id = 'ATM-102'" "1"
import_as "$root/db/import/example-atms.csv"
check "a withdrawn ATM that reappears is listed again" \
  "SELECT status || ':v' || version FROM $schema.atm WHERE atm_id = 'ATM-102'" "InService:v2"
check "full mode never touches SAMPLE- rows" "$sample_rows" "$after_seed"

denied() {
  local label="$1" role="$2" sql="$3"
  if as_role "$role" -c "$sql" >/dev/null 2>"$work/denied.err"; then
    echo "FAIL $label: $role was allowed: $sql" >&2
    exit 1
  fi
  grep -Eq "permission denied|append-only" "$work/denied.err" || { cat "$work/denied.err" >&2; exit 1; }
  echo "ok   $label"
}

echo "--- least privilege"
as_role atm_directory_app -At -c "SELECT count(*) FROM atm" >/dev/null && echo "ok   app role can SELECT atm"
denied "app role cannot insert" atm_directory_app "INSERT INTO atm (atm_id, name, status, latitude, longitude, address_line, city, country_code, accessibility, services, currency) VALUES ('X-1','x','InService',1,1,'x','x','AE','x',ARRAY['x'],'AED')"
denied "app role cannot update" atm_directory_app "UPDATE atm SET status = 'x'"
denied "app role cannot delete" atm_directory_app "DELETE FROM atm"
denied "app role cannot read the history" atm_directory_app "SELECT count(*) FROM atm_history"
denied "import role cannot delete" atm_directory_import "DELETE FROM atm"
denied "import role cannot read the history" atm_directory_import "SELECT count(*) FROM atm_history"
denied "import role cannot write the history directly" atm_directory_import "INSERT INTO atm_history (atm_id, operation, new_row, changed_by, application_name, changed_at) VALUES ('x','INSERT','{}','x','x',now())"
denied "history is append-only, even for the owner" atm_directory_migrate "UPDATE atm_history SET changed_by = 'x'"
denied "history cannot be deleted, even by the owner" atm_directory_migrate "DELETE FROM atm_history"
denied "history cannot be truncated, even by the owner" atm_directory_migrate "TRUNCATE atm_history"
check "schema owned by the migrate role" "SELECT nspowner::regrole FROM pg_namespace WHERE nspname = '$schema'" "atm_directory_migrate"
check "runtime and import roles own nothing" \
  "SELECT count(*) FROM pg_class WHERE relowner IN ('atm_directory_app'::regrole, 'atm_directory_import'::regrole)" "0"

echo "--- audit trail"
check "every import insert and update is in the history with role and application" \
  "SELECT count(*) FILTER (WHERE operation = 'INSERT') || ':' || count(*) FILTER (WHERE operation = 'UPDATE') FROM $schema.atm_history WHERE changed_by = 'atm_directory_import' AND application_name = 'atm-directory-import'" "4:4"
check "the update keeps the old and the new row" \
  "SELECT string_agg((old_row->>'status') || '>' || (new_row->>'status'), ',' ORDER BY history_id) FROM $schema.atm_history WHERE operation = 'UPDATE' AND atm_id IN ('ATM-002', 'ATM-102')" "OutOfService>InService,InService>OutOfService,InService>Withdrawn,Withdrawn>InService"
check "seed inserts are attributed to the migration role" \
  "SELECT count(*) || ':' || min(application_name) FROM $schema.atm_history WHERE atm_id LIKE 'SAMPLE-%' AND changed_by = 'atm_directory_migrate'" "3:atm-directory-flyway"

plan="$(PGOPTIONS="-c enable_seqscan=off" psql -X -At -d "$db" -c "EXPLAIN (COSTS OFF) SELECT atm_id FROM $schema.atm WHERE point(longitude, latitude) <@ box(point(55.0, 24.9), point(55.5, 25.4)) AND status <> 'Withdrawn'")"
if ! grep -q "Index Scan using ix_atm_location" <<<"$plan"; then
  echo "FAIL radius pre-filter does not use the GiST index:" >&2
  echo "$plan" >&2
  exit 1
fi
echo "ok   radius pre-filter uses the GiST index"

psql_q -d postgres -c "DROP DATABASE $db"
# Roles are cluster-wide; keep them if another database on this server still references them.
for role in atm_directory_migrate atm_directory_app atm_directory_import; do
  psql -X -q -d postgres -c "DROP ROLE $role" 2>/dev/null || echo "note: role $role kept (still referenced elsewhere)"
done
echo "Migration, seed and import rehearsal passed."
