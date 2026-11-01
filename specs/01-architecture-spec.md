# 01 — Architecture Specification: CareSync Enterprise

## 1. System purpose
CareSync is an enterprise healthcare platform focused on **member medical claim submission, automated adjudication, and lifecycle notification**. It demonstrates modern Java microservices practices suitable for regulated environments (architecture, testing, and contracts are first-class; full HIPAA controls are out of scope for this demo).

## 2. Microservices topology

| Service | Port | Responsibility |
|---------|------|----------------|
| `care-gateway-service` | 8080 | Spring Cloud Gateway: routing, CORS, optional JWT validation, rate-limit skeleton |
| `care-auth-service` | 8081 | Issues HS256 JWT tokens with roles (`ROLE_MEMBER`, `ROLE_CLAIMS_ADJUDICATOR`, `ROLE_ADMIN`) |
| `care-claims-service` | 8082 | Core domain: claim ingestion, validation, adjudication, persistence, Kafka publish |
| `care-notification-service` | 8083 | Kafka consumer: audit-style notification logging for adjudicated claims |

All services are Spring Boot 3.2+ applications. Parent POM manages versions.

## 3. Technology constraints (non-negotiable)

| Concern | Decision |
|---------|----------|
| Language | Java 17+ (prefer 21 when available). Records for value objects. |
| Framework | Spring Boot 3.5.x, Spring Cloud 2025.0.x |
| Persistence | PostgreSQL 16 (runtime). H2 PostgreSQL mode or Testcontainers for tests. Schema **only** via Flyway. |
| Messaging | Apache Kafka 3.7+ (KRaft). JSON payloads. Key = `claimReference`. |
| Money | `java.math.BigDecimal`, scale 2, `RoundingMode.HALF_EVEN` |
| API style | REST + OpenAPI 3.0 contract-first. Errors = RFC 7807 `ProblemDetail`. |
| Architecture tests | ArchUnit on every module that contains domain code |
| Build | Maven multi-module, Maven Wrapper |

## 4. Hexagonal layering (care-claims-service reference)

```
com.carent.claims/
├── domain/                 # PURE JAVA — no Spring, no JPA, no Kafka
│   ├── model/              # Aggregate, entities, value objects, enums
│   ├── service/            # Domain services (AdjudicationEngine, etc.)
│   └── exception/          # Business exceptions
├── application/
│   ├── port/in/            # Use-case interfaces (SubmitClaimUseCase, …)
│   ├── port/out/           # ClaimRepository, ClaimEventPublisher, …
│   └── service/            # Application services implementing inbound ports
└── adapter/
    ├── in/web/             # Controllers, request/response DTOs, @ControllerAdvice
    └── out/
        ├── persistence/    # JPA entities, Spring Data repos, mappers
        └── messaging/      # KafkaTemplate publisher implementation
```

**Dependency rule:** Adapter → Application → Domain. Domain never depends outward.

Other services may use a lighter structure but must still keep business rules out of controllers.

## 5. Cross-cutting concerns

### 5.1 Observability
- Spring Boot Actuator (`/actuator/health`, `/actuator/info`) on every service
- Structured logging (JSON optional); correlation id header `X-Correlation-Id` propagated when present

### 5.2 Configuration
- Externalise Kafka bootstrap, datasource URL, JWT secret via environment / `application.yml`
- No secrets in source control

### 5.3 Resilience (minimum for demo)
- Gateway: simple retry / timeout configuration skeleton
- Claims service: transactional outbox **not** required for MVP; publish after successful DB commit is acceptable for the demo with documented limitation

## 6. Local infrastructure
Use `infra/docker-compose.yml`:
- PostgreSQL 16 (`carent_claims` / user `carent`)
- Kafka KRaft (no ZooKeeper)
- Kafka UI on port 8090

## 7. Explicit non-goals (MVP)
- Full FHIR / EDI 837 ingestion
- Real provider network contracts or fee schedules beyond simple rate table
- Complete HIPAA audit logging and encryption at rest
- Saga / distributed transaction coordinator
- Multi-tenant isolation beyond `memberId` scoping
