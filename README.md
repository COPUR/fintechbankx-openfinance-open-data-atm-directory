# ATM Directory Service

Public Open Finance ATM directory service.

- Runtime: Java 23 + Gradle
- Architecture: Hexagonal (Ports & Adapters)
- Domain: Open Data / ATM Directory

## Implemented Slice (Wave 1)

Endpoint:

- `GET /open-finance/v1/atms`

Capabilities:

- Optional geo filtering with `lat`, `long`, `radius`
- Public endpoint (`security: []`) with mandatory `X-FAPI-Interaction-ID`
- ETag support with `If-None-Match` -> `304 Not Modified`
- Standardized error response for invalid requests

## Test Coverage

Includes:

- Domain and application unit tests
- Controller tests
- Integration tests
- OpenAPI contract test
- UAT-style ETag flow test

Coverage gate target: `>=85%` line coverage (JaCoCo)
