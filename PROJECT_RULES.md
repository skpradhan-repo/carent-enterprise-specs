# PROJECT_RULES.md — CareSync (load this first in every Bob session)

## Identity
CareSync is a healthcare claims adjudication platform built with **spec-driven development**, **hexagonal architecture**, and **event-driven messaging**.

## Non-negotiables
1. **Specs are the source of truth.** Before writing code, read the relevant file(s) under `specs/`.
2. **Domain is pure Java.** Packages matching `*.domain.*` must have zero imports of Spring, JPA, Kafka, or web libraries.
3. **Money = BigDecimal only.** Scale 2, `RoundingMode.HALF_EVEN`. Never `double` / `float`.
4. **Constructor injection only.** No field `@Autowired`.
5. **TDD cycle:** failing test first for every new rule or endpoint.
6. **ArchUnit must stay green.** Architecture violations fail the build.
7. **Errors = RFC 7807 ProblemDetail** from the web adapter only. Domain throws domain exceptions.
8. **Events are part of the contract.** Publishing/consuming must match `specs/04-events-messaging-spec.md`.

## Workflow (mandatory for new features)
1. Discover — confirm rules & examples in the relevant spec
2. Red — write acceptance or unit test that fails
3. Green — minimal implementation
4. Refactor — keep hexagonal boundaries
5. Verify — `./mvnw test` (unit + ArchUnit) then `./mvnw verify`

## Module map
- `care-gateway-service` — Spring Cloud Gateway (8080)
- `care-auth-service` — JWT issuer (8081)
- `care-claims-service` — core domain + REST + Kafka producer (8082)
- `care-notification-service` — Kafka consumer (8083)

## When unsure
Prefer the more restrictive interpretation of the specs. Ask the human before inventing new endpoints, tables, or event types.
