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
| Depends on | its own PostgreSQL at runtime; the mesh ingress gateway rate limit on `/open-finance/v1/atms` (platform mesh PR #11, commit `5e756f0`), which is the only abuse control on this anonymous route: the service validates and bounds `lat`, `long` and `radius` but does not throttle, and the gateway answers `429` with `x-fbx-rate-limited: true` and no `Retry-After` (clients retry with back-off) |

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
   | `atm_directory_import` | `<env>/atm-directory-service/db-import` (`import_db_secret_name`) | operator running the import | `USAGE`, `SELECT`/`INSERT`/`UPDATE` on `atm` (no `DELETE`), `TEMPORARY` |

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
   second run must print `0 | 0 | <n>` unchanged. Each pod serves an in-process
   snapshot reloaded every 30 s (`ATM_DIRECTORY_SNAPSHOT_REFRESH`), so wait one
   interval, then `GET /open-finance/v1/atms` returns the imported count in
   `Meta.TotalRecords`. `atm.directory.snapshot.age` above 120 s on any pod means
   its refresh is failing (it serves the old copy for up to 10 minutes, then `503`).

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

## 3. Go-live (one way)

The monolith's `AtmDataController` (`open-finance-context`) served three
in-memory sample ATMs; it never held the real network. There is nothing to
fall back to, so go-live is one way: no shadow traffic, no weighted split, no
route back to the monolith.

### Dependencies (all must be in place before step 2)

| Dependency | Why |
|---|---|
| Platform mesh PR #11 (rate limit in commit `5e756f0`) | gateway route `/open-finance/v1/atms` to `atm-directory-service.open-finance.svc.cluster.local:8080` and the ingress rate limit (100-token bucket, a shared 50 req/s per gateway pod for all callers, `429` + `x-fbx-rate-limited: true`, no `Retry-After`), the only abuse control on this anonymous route |
| Platform mesh NetworkPolicy for `open-finance` | the mesh repository owns NetworkPolicy (platform cicd-templates `1dd1138` turns chart policies off by default). The chart's own policy is opt-in: set `networkPolicy.enabled=true` and `networkPolicy.databaseCidrs` (Aurora subnets) only where the mesh repository does not cover the namespace; the policy then needs mesh sign-off |
| ConfigMap `rds-ca-bundle` (key `global-bundle.pem`) in `open-finance` | published by the platform trust-manager (mesh commit `5e756f0`); the chart mounts it at `/etc/fintechbankx/rds-ca` in the migrate init container and the service container, and both JDBC URLs (Terraform outputs `jdbc_url`, `reader_jdbc_url`) use `sslmode=verify-full&sslrootcert=/etc/fintechbankx/rds-ca/global-bundle.pem`; without it the pods never start |
| `ClusterSecretStore` `aws-secrets-manager` | External Secrets Operator syncs `db-app` (service) and `db-migration` (init container); without it the pods never start |
| DBA bootstrap and Terraform (section 2) | roles, grants, secrets, Aurora |
| A signed-off network file | first `--full` import (section 2) |

### Steps

| Step | Action | Check before going on |
|---|---|---|
| 1 | Deploy the chart, run the first import with `--full`, compare `Meta.TotalRecords` with the file's row count | parity check below passes against the service directly (port-forward) |
| 2 | Merge and apply mesh PR #11: the gateway route switches to the service in one step, together with the rate limit | the parity check passes through the gateway; rollback triggers stay clear for 30 minutes |
| 3 | Schedule the import (daily or on network change, `--full` when the operations source signs off the whole network) | each run prints its counts; `atm_history` shows the import role |
| 4 | Remove `services/openfinance-atm-directory-service` and the `atmdata` slice from the monolith after one release without rollback | no traffic on the old route |

### Parity check: response shape and headers only

The data differs on purpose (real network instead of three samples), so the
check compares structure, not values: status `200`; JSON with `Data.ATM[]`
items carrying `AtmId`, `Name`, `Status`, `Latitude`, `Longitude`, `Address`,
`City`, `Country`, `Accessibility`, `Services`, `Currency`, `UpdatedAt`;
`Links.Self` relative; `Meta.TotalRecords` equal to the item count; headers
`X-FAPI-Interaction-ID` (echoed), `ETag`, `Cache-Control`, `X-OF-Cache`; a
repeated request with `If-None-Match` gives `304`; missing interaction id or
`lat` without `long` gives `400`.

Responses are **not** identical to the monolith's:

| Aspect | Monolith | This service |
|---|---|---|
| Data | three hard-coded samples | the imported network; `Withdrawn` ATMs are not listed |
| `Cache-Control` | `max-age=60, public` | `no-cache` (revalidate with the `ETag`) |
| `ETag` value | hash over the response including `Links.Self` | hash over the ATM rows only (values differ, so clients revalidate once) |
| `X-OF-Cache` | `HIT`/`MISS` from an in-process cache on `200` | `MISS` on `200`, `HIT` on `304` |
| `429` | none | from the gateway (mesh PR #11) |
| `503` + `Retry-After: 5` | none | when the database is unavailable |

### Rollback triggers (any one, measured at the gateway)

| Trigger | Threshold |
|---|---|
| 5xx rate on `/open-finance/v1/atms` | above 1 % of requests for 5 minutes |
| p99 latency | above 500 ms for 10 minutes |
| Ready pods | below 2 for 5 minutes |
| Directory content | `Meta.TotalRecords` differs from the signed-off file (minus withdrawn rows) after an import |
| `429` share | above 5 % of requests for 15 minutes (rate limit too tight: fix in the mesh, do not roll back the service) |

### Rollback

There is no route back to the monolith. Roll back to the previous good state:

- **Bad release:** `helm rollback atm-directory-service <previous revision> -n open-finance`.
  Migrations are additive, so the previous image runs on the current schema.
- **Bad data:** re-import the previous signed-off file with `--full`
  (`atm_history` shows what the bad import changed).

## 4. Acceptance checklist

- [x] Service builds and tests standalone (`ci/test` runs `./gradlew check` with PostgreSQL integration tests)
- [x] Own schema and Flyway migration; Hibernate validates the entity at startup
- [x] Seed off by default, on only in dev/CI
- [x] Import is idempotent and atomic, rehearsed in CI (`deploy/data-migration-rehearsal`, `scripts/migration/verify-migration.sh`)
- [x] Container image, Helm chart, Terraform in CI (`Deployability` workflow)
- [ ] Real network CSV source and import schedule agreed with ATM operations
- [ ] Gateway route and rate limit switched in one step (platform mesh PR #11)
- [ ] Monolith launcher shell removed
