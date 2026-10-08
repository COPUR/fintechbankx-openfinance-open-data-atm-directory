# fintechbankx-openfinance-open-data-atm-directory

Bu repository, FinTechBankX DDD/EDA dönüşümünde **svc-of-atm-directory** servis yetkinliğinin kaynak kodunu, kontratlarını ve operasyonel guardrail'lerini içerir.

## Sorumluluk ve Sahiplik
| Alan | Değer |
|---|---|
| Organizasyon Modeli | Spotify Model (Tribe/Squad) |
| Tribe | Open Finance Tribe |
| Squad | Open Data Squad |
| Repo Kümesi (Capability) | open_finance |
| Service ID | svc-of-atm-directory |
| Bounded Context | atm_directory |
| Wave | 1 |
| Mimari Yaklaşım | DDD + Hexagonal + Event-Driven |

## Sorumluluk Sınırları
- Bu repo kendi bounded context domain modelinin tek yetkili sahibidir.
- Domain kuralları altyapıdan bağımsız tutulur; entegrasyonlar port/adapter katmanında yönetilir.
- API/Event kontratları geriye dönük uyumluluk kontrolleri ile korunur.
- Güvenlik guardrail'leri (mTLS, token doğrulama, idempotency, log hijyeni) CI/CD ile zorlanır.

## Kapsam
### In Scope
- atm_directory bağlamına ait uygulama kodu, testler ve otomasyon.
- Bu servise ait OpenAPI/AsyncAPI veya şema artefaktları.
- Bu servisin çalışma zamanı operasyonları (gözlemlenebilirlik, release, rollback).

### Out of Scope
- Diğer bounded context'lerin iş kuralları ve veri sahipliği.
- Paylaşımlı DB anti-pattern'i; cross-context doğrudan tablo erişimi.
- Platform dışı gizli bilgi/anahtar yönetimi (merkezi policy dışında local hardcode).

## Mühendislik Standartları
- **TDD öncelikli** geliştirme, birim test + entegrasyon testi.
- **Clean Architecture**: Domain katmanı framework bağımsız.
- **12-Factor** ve environment-driven configuration.
- **FAPI odaklı güvenlik** (OIDC/OAuth2, mTLS, DPoP gereksinimleri ilgili servislerde).
- **PII güvenliği**: loglarda maskeleme, secret'ların source/env içine yazılmaması.

## Branching ve Release Akışı
- Uzun ömürlü branch'ler: `main`, `dev`, `staging`, `local`.
- Feature branch kuralı: `codex/<kisa-aciklama>`.
- Release yaklaşımı: PR + required status checks + tag tabanlı sürümleme.

## Run, test and deploy

| What | Command / path |
|---|---|
| Unit and integration tests | `./gradlew check` (PostgreSQL integration tests run when `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD` are set, otherwise they are skipped) |
| Run locally | `DB_USERNAME=<owner> SPRING_DATASOURCE_PASSWORD=... ATM_DIRECTORY_SEED_ENABLED=true ./gradlew bootRun` (PostgreSQL `db_of_atm_directory_local`; locally one owner role may run Flyway and the service) |
| Database roles | `db/bootstrap/bootstrap-roles.sql`: `atm_directory_migrate` (Flyway, init container), `atm_directory_app` (runtime, SELECT only), `atm_directory_import` (import script) |
| Database migrations | `src/main/resources/db/migration` (schema `sc_of_atm_directory`); sample data in `db/seed` (dev/CI only) |
| Load the ATM network | `db/import/import-atms.sh "<conninfo as atm_directory_import>" atms.csv` (format: `db/import/example-atms.csv`) |
| Rehearse migrations and import | `PGHOST=... PGUSER=... PGPASSWORD=... scripts/migration/verify-migration.sh` |
| Container image | `docker build -t atm-directory-service .` |
| Kubernetes | `deploy/helm/atm-directory-service` (namespace `open-finance`) |
| AWS infrastructure | `deploy/terraform` |
| Extraction runbook | [RUNBOOK-EXTRACT-of-atm-directory](docs/migration/RUNBOOK-EXTRACT-of-atm-directory.md) |
| Deployment and Well-Architected mapping | [DEPLOYMENT_AND_WELL_ARCHITECTED](docs/architecture/DEPLOYMENT_AND_WELL_ARCHITECTED.md) |
| Decisions | [ADR-0001 PostgreSQL authority](docs/architecture/decisions/ADR-0001-atm-directory-postgres-authority.md) |

Layout: `domain` (model, query, ports, exception) ← `application` (use case) ← `infrastructure` (web, JPA persistence, Flyway seed config). The service publishes and consumes no events (ADR-0001).

## Dokümantasyon ve Referanslar
- [Enterprise Architecture Hub](https://github.com/COPUR/fintechbankx-governance-architecture-enablement-enterprise-architecture)
- [Secure Microservices Architecture](https://github.com/COPUR/fintechbankx-governance-architecture-enablement-enterprise-architecture/blob/main/docs/architecture/overview/SECURE_MICROSERVICES_ARCHITECTURE.md)
- [Service Data Ownership Matrix](https://github.com/COPUR/fintechbankx-governance-architecture-enablement-enterprise-architecture/blob/main/docs/enterprisearchitecture/implementation-development/SERVICE_DATA_OWNERSHIP_MATRIX.md)
- [Service API Contracts Index](https://github.com/COPUR/fintechbankx-governance-architecture-enablement-enterprise-architecture/blob/main/docs/enterprisearchitecture/implementation-development/SERVICE_API_CONTRACTS_INDEX.md)
- [Transformation Plan](https://github.com/COPUR/fintechbankx-governance-architecture-enablement-enterprise-architecture/blob/main/docs/enterprisearchitecture/implementation-development/MICROSERVICES_TRANSFORMATION_PLAN.md)
- [Capability Map (PUML)](https://github.com/COPUR/fintechbankx-governance-architecture-enablement-enterprise-architecture/blob/main/docs/puml/service-mesh/enterprise-capability-map.puml)
- [Bu Repo Dokümantasyonu](./docs)

## Güvenlik ve Uyumluluk Notları
- Gerçek secret değerleri repo veya `.env` içinde tutulmaz.
- Secret üretim/rotasyon olayları merkezi log/SIEM'e taşınır.
- CI pipeline, anonimlik ve local-path sızıntısı kontrollerini bloklayıcı olarak çalıştırır.

## Katkı
- Katkı süreci için `CONTRIBUTING.md` ve squad runbook'ları izlenmelidir.
- PR'larda mimari kararlar ADR veya backlog referansı ile ilişkilendirilmelidir.

## Cell-Based Architecture

This repository participates in the FinTechBankX cell-based resilience program.

- Plan: \
- Backlog: \

<!-- cell-architecture-start -->
## Cell-Based Architecture

This repository participates in the FinTechBankX cell-based resilience program.

- Plan: docs/architecture/CELL_BASED_ARCHITECTURE_IMPLEMENTATION_PLAN.md
- Backlog: docs/project-management/CELL_ARCHITECTURE_BACKLOG_BOARD.md
<!-- cell-architecture-end -->
