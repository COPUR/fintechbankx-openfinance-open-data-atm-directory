# Migration Granularity Notes

- Repository: `fintechbankx-openfinance-atm-directory-service`
- Source monorepo: `enterprise-loan-management-system`
- Sync date: `2026-03-15`
- Sync branch: `chore/granular-source-sync-20260313`

## Applied Rules

- dir: `services/openfinance-atm-directory-service` -> `.`
- file: `api/openapi/atm-directory-service.yaml` -> `api/openapi/atm-directory-service.yaml`
- dir: `infra/terraform/services/atm-directory-service` -> `infra/terraform/atm-directory-service`
- file: `docs/architecture/open-finance/capabilities/hld/open-finance-capability-overview.md` -> `docs/hld/open-finance-capability-overview.md`
- file: `docs/architecture/open-finance/capabilities/test-suites/open-data-test-suite.md` -> `docs/test-suites/open-data-test-suite.md`

## Notes

- This is an extraction seed for bounded-context split migration.
- Follow-up refactoring may be needed to remove residual cross-context coupling.
- Build artifacts and local machine files are excluded by policy.
- 2026-10-08: seed turned into a deployable service. PostgreSQL (`sc_of_atm_directory.atm`, Flyway V1) replaces the in-memory adapter; the monolith held no ATM data, so there is no backfill. Sample ATMs moved to `db/seed` (dev/CI only); the network is loaded with `db/import/import-atms.sh`. `infrastructure/` and `infra/terraform/atm-directory-service` were replaced by `Dockerfile`, `deploy/helm/atm-directory-service` and `deploy/terraform` (see `docs/migration/RUNBOOK-EXTRACT-of-atm-directory.md`).
