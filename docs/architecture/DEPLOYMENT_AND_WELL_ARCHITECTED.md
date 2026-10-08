# Deployment and AWS Well-Architected mapping

How `svc-of-atm-directory` runs on AWS, and which file implements each
Well-Architected concern. Claims here point at code; anything not listed is
not done yet.

## Runtime shape

```
API gateway / Istio ingress ──▶ atm-directory-service pods (EKS, namespace open-finance, 3..20, HPA)
                                   └─ JDBC (read-only) ─▶ Aurora PostgreSQL Serverless v2 reader endpoint
Flyway (migrate init container, atm_directory_migrate) / db/import/import-atms.sh (atm_directory_import) ─▶ Aurora writer endpoint
```

The endpoint is public open data: no token, `X-FAPI-Interaction-ID` required,
`ETag` + `Cache-Control: no-cache`: caches revalidate every reuse with
`If-None-Match` and get a body-less `304` when nothing changed. `Links.Self` is a
relative link built from the validated query, never from the request URL.

| Artifact | Path |
|---|---|
| Image | `Dockerfile` (layered Spring Boot jar, JRE 23, uid 10001) |
| Kubernetes | `deploy/helm/atm-directory-service` (`values.yaml` prod-shaped, `values-dev.yaml` with the sample seed) |
| AWS | `deploy/terraform` (Aurora, KMS, Secrets Manager, IRSA, alarms; platform `microservice-base` module) |
| Runtime config | `src/main/resources/application.yml` (all environment values from env) |
| Data | `src/main/resources/db/migration`, `db/seed`, `db/import/import-atms.sh` |
| CI proof | `.github/workflows/required-gates.yml`, `.github/workflows/deployability.yml` |

## Well-Architected pillars

| Pillar | What is in place | Where |
|---|---|---|
| Operational excellence | Startup/liveness/readiness groups (readiness includes `db`) on management port 8081; Prometheus metrics tagged `service=svc-of-atm-directory`; interaction id echoed in responses and errors; import prints inserted/updated/unchanged counts; IaC for every AWS resource; data path rehearsed in CI | `application.yml`, `AtmDirectoryController`, `import-atms.sh`, `verify-migration.sh`, `deploy/terraform` |
| Security | Public endpoint, no token (as in the monolith), rate-limited at the gateway (mesh PR #11), reachable only from the ingress gateway namespace (NetworkPolicy) inside the Istio mesh; pods non-root, read-only root filesystem, all capabilities dropped, RuntimeDefault seccomp; three DB roles with separate Secrets Manager credentials (runtime `atm_directory_app` SELECT only, schema owner `atm_directory_migrate` only in the migrate init container, operator `atm_directory_import` SELECT/INSERT/UPDATE on `atm`), synced by External Secrets (`aws-secrets-manager`), never in config; append-only `atm_history` (trigger) records every imported insert and update with login role, application and time; KMS-encrypted storage, snapshots, logs and secret; `rds.force_ssl`; IRSA least privilege; app connections are read-only (`readOnlyMode=always`); schema CHECK constraints on coordinates, currency, services; no personal data stored | `networkpolicy.yaml`, `deployment.yaml`, `externalsecret.yaml`, `main.tf`, `application.yml`, `V1__create_atm_table.sql`, `V2__create_atm_history.sql`, `V3__grant_least_privilege.sql`, `db/bootstrap/bootstrap-roles.sql` |
| Reliability | Aurora Multi-AZ with a reader for failover (`aurora_instance_count` >= 2), PITR, deletion protection, final snapshot; pods spread across zones, PDB, zero-unavailable rolling updates, graceful shutdown; store outages return `503` + `Retry-After` instead of `500`; imports are atomic and idempotent | `main.tf`, `deployment.yaml`, `pdb.yaml`, `AtmDirectoryExceptionHandler`, `import-atms.sh` |
| Performance efficiency | Radius search answered by a GiST index on `point(longitude, latitude)` with an exact great-circle filter in the use case; HTTP revalidation (`ETag`, `If-None-Match` -> `304`, `Cache-Control: no-cache`); stateless pods scaled by HPA up to 20; reads on the Aurora reader endpoint; virtual threads | `V1__create_atm_table.sql`, `GeoBoundingBox`, `AtmDirectoryController`, `hpa.yaml`, `outputs.tf` |
| Cost optimization | Serverless v2 floor 0.5 ACU and ceiling 4 ACU by default (read-mostly data); small pods (250m/512Mi) and a pool of 5 connections each; dev runs one Aurora instance and 2-4 pods; no Redis (HTTP caching instead, ADR-0001); log retention 30 days outside prod | `variables.tf`, `values.yaml`, `values-dev.yaml`, `environments/dev.tfvars.example` |
| Sustainability | Scale-down to the minimum footprint off-peak (HPA, Serverless ACUs); client and gateway caching avoids recomputing unchanged responses; layered image keeps rebuilds small | `hpa.yaml`, `AtmDirectoryController`, `Dockerfile` |

## Known gaps

- The three DB roles are created by a DBA bootstrap step (`db/bootstrap/bootstrap-roles.sql`, runbook), not by Terraform, so Terraform never holds a password.
- The pod annotation `traffic.sidecar.istio.io/excludeOutboundPorts: "5432"` lets the migrate init container reach Aurora before the Istio sidecar starts; it needs platform mesh sign-off.
- No OTLP tracing exporter yet (the platform collector is `otel-collector.observability.svc.cluster.local:4317`).
- `microservice-base` is referenced at `ref=main`; pin a tag once the modules repo publishes releases.
- `FLYWAY_URL` must be set to the writer whenever `DB_URL` is the reader; nothing enforces this.
- No load test yet; HPA targets and ACU limits are starting values.
- The real ATM network source and import schedule are not agreed yet.
