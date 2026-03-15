# ATM Directory Service

ATM open data microservice.

- Runtime: Java 23 + Gradle
- Architecture: Hexagonal (Ports & Adapters)

## Phase-2 Wave 1 Implementation

- Contract-aligned endpoint: `GET /open-finance/v1/atms`
- Hexagonal slice:
  - `domain`: `Atm`
  - `application`: `ListAtmsUseCase` + geo-radius filter service
  - `infrastructure`: seeded read adapter + REST controller
- Runtime behavior:
  - required `X-FAPI-Interaction-ID`
  - optional `Authorization`
  - geo query support (`lat`, `long`, `radius`)
  - ETag support (`If-None-Match` -> `304`)
  - `X-OF-Cache` response header
- Observability baseline:
  - `X-Trace-Id` correlation
  - request timer metric
  - structured completion logs

## Validation

```bash
gradle clean test jacocoTestReport jacocoTestCoverageVerification --no-daemon
```

Latest local line coverage: `98.39%`.
