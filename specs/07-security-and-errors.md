# 07 — Security & Error Catalogue

## Authentication (demo level)
- `care-auth-service` issues JWT (HS256) with claims:
  - `sub` — subject id
  - `roles` — array of `ROLE_MEMBER` | `ROLE_CLAIMS_ADJUDICATOR` | `ROLE_ADMIN`
- Gateway may validate JWT on `/api/v1/claims/**` (configurable; can be disabled for local happy-path demos)
- Secret via env `JWT_SECRET` (min 32 chars)

## Authorisation (optional stretch)
- Submit claim: `ROLE_MEMBER` or `ROLE_CLAIMS_ADJUDICATOR`
- Reverse claim: `ROLE_CLAIMS_ADJUDICATOR` or `ROLE_ADMIN`

## RFC 7807 Problem Details
All 4xx/5xx from REST adapters return:

```json
{
  "type": "about:blank",
  "title": "Validation Failed",
  "status": 400,
  "detail": "billedAmount must be greater than or equal to 5.00",
  "instance": "/api/v1/claims"
}
```

### Catalogue

| Situation | HTTP | title (example) |
|-----------|------|-----------------|
| Bean validation / min amount | 400 | Validation Failed |
| Future service date | 400 | Validation Failed |
| Claim not found | 404 | Claim Not Found |
| Illegal state transition | 409 | Conflict |
| Unhandled | 500 | Internal Server Error |

Domain exceptions are translated only in `@ControllerAdvice`.

## Input validation
- OpenAPI + `jakarta.validation` annotations on request DTOs
- Benefit category must be a valid enum value
