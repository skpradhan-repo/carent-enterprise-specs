# 05 — Maven Multi-Module Structure

## Parent
```
carent-enterprise/                    (packaging pom)
├── pom.xml                           # dependencyManagement, pluginManagement
├── care-gateway-service/
├── care-auth-service/
├── care-claims-service/
├── care-notification-service/
├── specs/                            # this specification set (optional copy)
└── infra/
    └── docker-compose.yml
```

## Parent POM guidance
- Java 17 (or 21)
- Spring Boot 3.2.5+ (or latest 3.2.x / 3.3.x stable)
- Spring Cloud 2025.0.x BOM
- ArchUnit, AssertJ, Testcontainers (optional), Spring Kafka
- `spring-boot-maven-plugin` only on executable modules
- Failsafe for `*IT.java`

## care-claims-service packages (canonical)
```
com.carent.claims
com.carent.claims.domain.model
com.carent.claims.domain.service
com.carent.claims.domain.exception
com.carent.claims.application.port.in
com.carent.claims.application.port.out
com.carent.claims.application.service
com.carent.claims.adapter.in.web
com.carent.claims.adapter.in.web.dto
com.carent.claims.adapter.out.persistence
com.carent.claims.adapter.out.messaging
com.carent.claims.config
```

## Flyway (claims service)
```
src/main/resources/db/migration/
  V1__create_claim_table.sql
```

Suggested columns: id, claim_reference (unique), member_id, provider_id, service_date, billed_amount, benefit_category, adjudicated_amount, patient_co_pay, status, denial_reason, created_at, updated_at.

## Gateway routes (sketch)
- `/api/v1/claims/**` → `lb://care-claims-service` or `http://localhost:8082`
- `/api/v1/auth/**` → auth service

## Auth service (minimum)
- `POST /api/v1/auth/token` with client credentials or simple username/password demo → JWT
- Claims: `sub`, `roles` array

## Notification service
- Single `@KafkaListener` on `claim-lifecycle-events`
- No public HTTP API required beyond actuator
