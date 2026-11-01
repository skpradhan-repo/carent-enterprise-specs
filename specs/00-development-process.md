# 00 — Development Process (Spec-Driven + TDD with IBM Bob)

## Goal
Produce a production-shaped, interview-ready multi-service platform where every feature is traceable to a written specification and protected by automated tests and architecture rules.

## Roles
| Role | Responsibility |
|------|----------------|
| **Human (Director)** | Owns business intent, approves specs, accepts/rejects AI plans |
| **IBM Bob (Executor)** | Implements only what the specs + tests demand |
| **Specs** | Shared contract (this folder) |

## Mandatory cycle for every feature

```
1. DISCOVER
   - Read the relevant specs/*.md and openapi.yaml
   - Confirm rules, examples, counter-examples, open questions
   - If a rule is missing → update the spec first (human approval)

2. ACCEPTANCE / RED
   - Write a failing test that encodes one concrete example from the spec
   - Prefer *IT for end-to-end behaviour, *Test for pure domain

3. GREEN
   - Implement the minimum domain → application → adapter code
   - Domain first, pure Java

4. REFACTOR
   - Improve names, extract, keep packages clean
   - Ensure ArchUnit still passes

5. REVIEW / VERIFY
   - ./mvnw test   (unit + ArchUnit)
   - ./mvnw verify (integration / acceptance)
   - Manual smoke via curl or Kafka UI if messaging is involved
```

## Definition of Done (feature)
- [ ] Spec updated or already covers the behaviour
- [ ] At least one acceptance test (`*IT`) or domain test that would have failed before the change
- [ ] ArchUnit green
- [ ] No Spring / JPA imports inside `domain` packages
- [ ] Money handled with `BigDecimal` + `HALF_EVEN`
- [ ] Public API matches OpenAPI (if HTTP)
- [ ] Kafka payload matches event spec (if messaging)
- [ ] RFC 7807 used for error responses
- [ ] README / module notes updated only when structure changes

## Definition of Done (platform milestone)
- [ ] All four services start locally against `infra/docker-compose.yml`
- [ ] `POST /api/v1/claims` → claim stored → `CLAIM_SUBMITTED` + later `CLAIM_ADJUDICATED` events visible in Kafka UI
- [ ] `GET /api/v1/claims/{claimReference}` returns adjudicated result
- [ ] Gateway routes to claims service (optional JWT for demo)
- [ ] Notification service logs audit lines for APPROVED / DENIED
- [ ] `./mvnw verify` green from the parent POM

## What Bob must NOT do
- Invent new REST endpoints not present in `openapi.yaml`
- Put business rules in controllers or JPA entities
- Use `double` for money
- Skip the failing-test-first step for new rules
- Change event schemas without updating `04-events-messaging-spec.md`

## Recommended Bob modes
- **Plan** — module layout, dependency graph, migration of a rule into code
- **Code** — implementation after plan approval
- **Ask** — “explain current adjudication path for preventive claims”
