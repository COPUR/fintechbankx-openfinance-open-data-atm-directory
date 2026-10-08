# RUNBOOK-EXTRACT-of-atm-directory

Extraction of the ATM directory from `enterprise-loan-management-system` into
`svc-of-atm-directory` (this repository), following the strangler-fig steps of
`fbx-monolith-extraction`. Decision record: [ADR-0001](../architecture/decisions/ADR-0001-atm-directory-postgres-authority.md).

| Field | Value |
|---|---|
| Context / service | `of` / `svc-of-atm-directory` |
| Slice | Public ATM directory: list and radius search (`GET /open-finance/v1/atms`) |
| Owned data | `db_of_atm_directory_<env>`, schema `sc_of_atm_directory`: `atm` |
| Events | none (outbox and `evt.of.atm.*` deferred, ADR-0001) |
| Depends on | its own PostgreSQL at runtime; the mesh ingress gateway rate limit on `/open-finance/v1/atms` (platform mesh PR #11), which is the only abuse control on this anonymous route: the service validates and bounds `lat`, `long` and `radius` but does not throttle, and the gateway answers `429` with `Retry-After` |

## 1. Data ownership split

| Monolith object | Owner after the split | Notes |
|---|---|---|
| `services/openfinance-atm-directory-service` (launcher class only) | this repository | no domain code, no tables |
| ATM table | none existed | nothing to backfill or reconcile |
| Sample ATMs (formerly `InMemoryAtmDirectoryAdapter`) | `src/main/resources/db/seed/R__seed_sample_atms.sql` | dev and CI only (`ATM_DIRECTORY_SEED_ENABLED=true`) |

The monolith must not read `sc_of_atm_directory`; consumers use the HTTP API.

## 2. First load of the real network

1. DBA bootstrap (once per environment, before the first deploy, with the
   RDS-managed admin secret, Terraform output `master_user_secret_arn`):
   `psql "host=<writer> dbname=db_of_atm_directory_<env> user=atm_admin sslmode=require" -v ON_ERROR_STOP=1 -f db/bootstrap/bootstrap-roles.sql`.
   It creates three LOGIN roles without passwords and lets only the owner role
   create the schema. Set each password with `\password <role>` and store
   `{"username","password"}` in the matching Secrets Manager secret:

   | Role | Secret (Terraform output) | Used by | Privileges |
   |---|---|---|---|
   | `atm_directory_migrate` | `<env>/atm-directory-service/db-migration` (`migration_db_secret_name`) | Flyway in the `migrate` init container | owns `sc_of_atm_directory` |
   | `atm_directory_app` | `<env>/atm-directory-service/db-app` (`app_db_secret_name`) | service container | `USAGE` on the schema, `SELECT` on `atm` |
   | `atm_directory_import` | `<env>/atm-directory-service/db-import` (`import_db_secret_name`) | operator running the import | `USAGE`, `SELECT`/`INSERT`/`UPDATE` on `atm`, `TEMPORARY` |

   Flyway grants the table privileges (`V3__grant_least_privilege.sql`). If a
   role was created after the first deploy, re-run that file with psql as
   `atm_directory_migrate`.
   Rotation: change the password in PostgreSQL and in its secret, then
   `helm upgrade ... --set externalSecret.rotation=<date>`; the changed
   `checksum/secret` rolls the pods onto the new credential.
2. Deploy the chart with `externalSecret.remoteSecretName`,
   `externalSecret.migrationRemoteSecretName`, `config.DB_URL` (reader) and
   `config.FLYWAY_URL` (writer). The `migrate` init container creates or updates
   `sc_of_atm_directory` and exits; the service container starts with Flyway off.
3. Export the network from the ATM operations source as CSV with the header in
   `db/import/example-atms.csv`, then from a host inside the VPC:
   `PGPASSWORD=... db/import/import-atms.sh "host=<writer> dbname=db_of_atm_directory_<env> user=atm_directory_import sslmode=require" atms.csv`
4. Verify: the script prints rows in file / inserted / updated / unchanged; a
   second run must print `0 | 0 | <n>` unchanged. `GET /open-finance/v1/atms`
   returns the imported count in `Meta.TotalRecords`.

Re-imports are safe at any time: changed rows get `version + 1` and a new
`updated_at`, identical rows are untouched, an invalid row aborts the whole
file. By default ATMs missing from a file are left as they are (delta mode).
When the operations source signs off the complete network, import with
`--full`: every listed ATM missing from the file becomes `Withdrawn` and is no
longer returned by the API (it is listed again if it reappears). The run is
rolled back if it would withdraw more than 10 % of the listed ATMs
(`--max-withdraw-percent N` to override after checking the export). ATMs are
never deleted.
Every inserted or updated row is recorded in `sc_of_atm_directory.atm_history`
(old and new row, login role, `application_name`, time); the table is
append-only and readable only by the schema owner.

## 3. Cutover plan

| Step | Action | Rollback |
|---|---|---|
| 1 | Deploy with an empty table, import the network (section 2) | drop `sc_of_atm_directory`; nothing else changed |
| 2 | Gateway routes `/open-finance/v1/atms` to `atm-directory-service.open-finance.svc.cluster.local:8080` | route back; the monolith never served real ATM data, so the fallback is "endpoint unavailable" |
| 3 | Schedule the import (daily or on network change) from the ATM operations source | stop the schedule; data stays as last imported |
| 4 | Remove `services/openfinance-atm-directory-service` from the monolith | restore from git |

## 4. Acceptance checklist

- [x] Service builds and tests standalone (`ci/test` runs `./gradlew check` with PostgreSQL integration tests)
- [x] Own schema and Flyway migration; Hibernate validates the entity at startup
- [x] Seed off by default, on only in dev/CI
- [x] Import is idempotent and atomic, rehearsed in CI (`deploy/data-migration-rehearsal`, `scripts/migration/verify-migration.sh`)
- [x] Container image, Helm chart, Terraform in CI (`Deployability` workflow)
- [ ] Real network CSV source and import schedule agreed with ATM operations
- [ ] Gateway route switched in the platform mesh repository
- [ ] Monolith launcher shell removed
