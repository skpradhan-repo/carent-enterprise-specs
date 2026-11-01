# 06 — Testing & Quality Gates

## Test pyramid

| Layer | Location | Tools | Must cover |
|-------|----------|-------|------------|
| Domain unit | `*Test` under domain | JUnit 5, AssertJ | Every adjudication example E1–E8 |
| Application unit | application package | Mockito for ports | Orchestration, duplicate check |
| Web slice | `@WebMvcTest` | MockMvc | Validation, mapping, ProblemDetail |
| Persistence slice | `@DataJpaTest` | H2 / Testcontainers | Repository + Flyway |
| Acceptance | `*IT` | `@SpringBootTest` + TestRestTemplate or MockMvc | E1, E3, E5, E7 at HTTP level |
| Architecture | `architecture/*Test` | ArchUnit | Domain purity, layer direction |
| Messaging | `@EmbeddedKafka` or Testcontainers Kafka | | Publish + consume smoke |

## ArchUnit minimum rules (claims module)
1. `..domain..` must not depend on `org.springframework..`
2. `..domain..` must not depend on `jakarta.persistence..` or `org.hibernate..`
3. `..domain..` must not depend on `..adapter..`
4. `..application..` must not depend on `..adapter..`
5. Controllers must not call repositories directly (only inbound ports / application services)

## Commands
```bash
./mvnw clean test          # unit + ArchUnit
./mvnw verify              # + IT
./mvnw -pl care-claims-service test
```

## Quality bar for “cutting edge” demo
- All monetary assertions use string or BigDecimal comparison, never double
- At least one test proves RFC 7807 body shape on 400
- At least one test proves Kafka event published (verify publisher mock or embedded broker)
- README documents how to run infra + services
- No TODOs in domain package
