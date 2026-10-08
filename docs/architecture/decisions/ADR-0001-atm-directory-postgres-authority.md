# ADR-0001: PostgreSQL is the authority for the ATM directory

| Field | Value |
|---|---|
| Status | Proposed (owned by the Open Data Squad; accepted when merged) |
| Date | 2026-10-08 |
| Service | `svc-of-atm-directory` (bounded context `atm_directory`) |
| Reversibility | Reversible: the service only reads the table; switching the out-port adapter or the store does not change the API |

## Context

`GET /open-finance/v1/atms` was served from `InMemoryAtmDirectoryAdapter`, three
hard-coded sample ATMs. The monolith copy (`services/openfinance-atm-directory-service`
in `enterprise-loan-management-system`) is only a launcher class: the monolith
never stored ATM data in a table, so there is nothing to migrate or reconcile.
The service has no write use case today; the network is maintained outside it.

## Decision

1. **PostgreSQL is the directory authority.** The service owns
   `db_of_atm_directory_<env>`, schema `sc_of_atm_directory`, table `atm`
   (Flyway `V1__create_atm_table.sql`). `JpaAtmDirectoryAdapter` implements the
   existing `AtmDirectoryPort`; the JPA entity is separate from the domain record
   and read-only (`@Immutable`, read-only connection pool).
2. **Radius search runs in the database.** The domain computes a
   `GeoBoundingBox`; the adapter answers it from a GiST index on
   `point(longitude, latitude)` and the use case keeps the exact great-circle
   filter. Plain PostgreSQL, no PostGIS dependency.
3. **No backfill.** There is no monolith data. Sample ATMs live in
   `classpath:db/seed` and load only when `ATM_DIRECTORY_SEED_ENABLED=true`
   (dev, CI). The real network is loaded with `db/import/import-atms.sh`, an
   idempotent upsert that bumps `version` and `updated_at` only for changed rows.
4. **No outbox and no events yet.** Without a write or import use case inside the
   service there is no domain fact to publish. A transactional outbox and
   compacted state topics `evt.of.atm.*` are deferred until such a use case
   exists (for example an import endpoint or a status-change command); the
   import script's `version` column is the hook for the future
   `aggregateVersion`.
5. **Read caching over HTTP revalidation, not Redis.** Responses carry a content
   `ETag` (`If-None-Match` gives a body-less `304`) and `Cache-Control: no-cache`:
   gateways, CDNs and clients may store a response but revalidate it before every
   reuse. The monolith sent `max-age=60, public`, which lets a shared cache replay
   one caller's `X-FAPI-Interaction-ID` to others; the first cut of this service
   also built `Links.Self` from the request URL, so a forged `X-Forwarded-Host`
   could poison a cached body. `Links.Self` is relative again, built from the
   validated query as in the monolith. The platform module is called with `cache_engine = "none"`.
6. **Security posture unchanged.** The endpoint stays public and
   unauthenticated (`security: []` in the OpenAPI spec, exempt in the FAPI/DPoP
   guard), still requires `X-FAPI-Interaction-ID`, and is reachable only through
   the ingress gateway (NetworkPolicy, mesh mTLS). No OAuth2 resource server is
   added because no endpoint needs a token.

## Consequences

- The directory survives restarts and scales horizontally; pods are stateless
  and may read from the Aurora reader endpoint.
- Changing the data needs the import script (or a future write use case), run
  with the writer credential; the service itself cannot write.
- Consumers that want change notifications must poll with `If-None-Match`
  until the deferred events exist.

## Revisit when

- A write or import use case is added to the service: add the outbox
  (`outbox_event`, topic CHECK on `evt.of.atm.`) in the same change and
  publish `evt.of.atm.*.v1` state facts keyed by `atmId`.
- Read traffic exceeds what HTTP caching absorbs (watch `http_server_requests_seconds`
  and Aurora ACUs) before considering a shared cache.
