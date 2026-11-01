# CLAUDE.md / Bob session context — CareSync

You are pair-programming on the CareSync Enterprise platform.

Always load and obey:
1. `PROJECT_RULES.md` at repo root (or this specs pack)
2. The relevant files under `specs/`

## Tech stack reminder
- Java 17+, Spring Boot 3.5.x, Spring Cloud 2025.0.x
- Hexagonal architecture for `care-claims-service`
- PostgreSQL + Flyway, Kafka JSON events
- Money: BigDecimal, HALF_EVEN, scale 2
- Errors: RFC 7807 ProblemDetail

## Build commands
```
./mvnw clean test
./mvnw verify
./mvnw -pl care-claims-service spring-boot:run
```

## TDD workflow (do not skip)
1. Discover from specs
2. Write failing test
3. Implement minimum
4. Refactor
5. Keep ArchUnit green

## First implementation order (suggested)
1. Parent POM + empty modules + ArchUnit skeleton
2. Domain model + AdjudicationEngine + unit tests (spec 02 + 03)
3. Application ports + service
4. In-memory or JPA persistence + Flyway
5. REST adapter matching openapi.yaml
6. Kafka publisher + notification consumer
7. Gateway + auth (can be thinner)

When generating code, prefer small vertical slices that keep `./mvnw test` green after each slice.
