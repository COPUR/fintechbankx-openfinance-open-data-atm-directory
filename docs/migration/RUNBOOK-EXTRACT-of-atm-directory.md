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
| Depends on | nothing at runtime besides its own PostgreSQL |

## 1. Data ownership split

| Monolith object | Owner after the split | Notes |
|---|---|---|
| `services/openfinance-atm-directory-service` (launcher class only) | this repository | no domain code, no tables |
| ATM table | none existed | nothing to backfill or reconcile |
| Sample ATMs (formerly `InMemoryAtmDirectoryAdapter`) | `src/main/resources/db/seed/R__seed_sample_atms.sql` | dev and CI only (`ATM_DIRECTORY_SEED_ENABLED=true`) |

The monolith must not read `sc_of_atm_directory`; consumers use the HTTP API.

## 2. First load of the real network

1. DBA bootstrap (once per environment, with the RDS-managed admin secret,
   Terraform output `master_user_secret_arn`): create role `atm_directory_app`
   with a generated password, make it the owner of database
   `db_of_atm_directory_<env>`, and store `{"username","password"}` in Secrets
   Manager `<env>/atm-directory-service/db-app` (output `app_db_secret_name`).
2. Deploy the chart (Flyway creates `sc_of_atm_directory.atm` on first start;
   `FLYWAY_URL` must point at the writer when `DB_URL` is the reader).
3. Export the network from the ATM operations source as CSV with the header in
   `db/import/example-atms.csv`, then from a host inside the VPC:
   `PGPASSWORD=... db/import/import-atms.sh "host=<writer> dbname=db_of_atm_directory_<env> user=atm_directory_app sslmode=require" atms.csv`
4. Verify: the script prints rows in file / inserted / updated / unchanged; a
   second run must print `0 | 0 | <n>` unchanged. `GET /open-finance/v1/atms`
   returns the imported count in `Meta.TotalRecords`.

Re-imports are safe at any time: changed rows get `version + 1` and a new
`updated_at`, identical rows are untouched, an invalid row aborts the whole
file. ATMs missing from a file are not deleted; import them with their new status.

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
