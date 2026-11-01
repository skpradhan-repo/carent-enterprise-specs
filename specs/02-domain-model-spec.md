# 02 — Domain Model Specification (`care-claims-service`)

## 1. Ubiquitous language
| Term | Meaning |
|------|---------|
| Claim | Aggregate representing one submitted medical service charge |
| ClaimReference | Business identifier, format `CLM-YYYYMMDD-XXXXXX` |
| Adjudication | Process that decides plan payment vs member responsibility |
| Coverage rate | Plan-allowed percentage for a benefit category (0.00–1.00) |
| Patient co-pay | Amount the member owes after adjudication (for this MVP = billed − adjudicated) |
| Accumulator | YTD total of adjudicated amounts for a member (simple version) |

## 2. Value objects (immutable, prefer Java `record`)

### Money
- Wrapper or disciplined use of `BigDecimal`
- Scale 2, `RoundingMode.HALF_EVEN`
- Must never be negative for billed amounts; adjudicated/co-pay may be zero

### ClaimReference
- Generated on submission: `CLM-` + `yyyyMMdd` + `-` + 6-digit zero-padded sequence or random numeric
- Example: `CLM-20261031-891023`

### MemberId / ProviderId
- Strings with prefixes `MEM-` / `PRV-` and minimum length 5 after prefix validation in application layer
- Stored as plain strings in domain; format validated at boundary

### BenefitCategory (enum)
```
IN_NETWORK_PRIMARY_CARE
OUT_OF_NETWORK_SPECIALIST
PREVENTIVE_SCREENING
EXCLUDED_COSMETIC
GENERAL_MEDICAL          // default fallback rate
```

### ClaimStatus (enum)
```
PENDING
UNDER_REVIEW
APPROVED
DENIED
REVERSED
```

## 3. Aggregate root: Claim

| Field | Type | Notes |
|-------|------|-------|
| id | Long (optional until persisted) | Technical PK |
| claimReference | ClaimReference | Business key, unique |
| memberId | String | |
| providerId | String | |
| serviceDate | LocalDate | |
| billedAmount | BigDecimal | > 0, scale 2 |
| benefitCategory | BenefitCategory | Determines coverage rate |
| adjudicatedAmount | BigDecimal | Set by adjudication |
| patientCoPay | BigDecimal | Set by adjudication |
| status | ClaimStatus | |
| denialReason | String (optional) | e.g. TIMELY_FILING_EXCEEDED |
| createdAt | Instant | |
| updatedAt | Instant | |

### Invariants
1. `billedAmount` > 0 and scale ≤ 2 (normalised to 2).
2. `serviceDate` ≤ today. If `serviceDate` < today − 365 days → status must become `DENIED` with reason `TIMELY_FILING_EXCEEDED`.
3. `adjudicatedAmount + patientCoPay == billedAmount` (within scale-2 equality) after successful adjudication.
4. Status transitions only as defined in section 5.
5. Once `REVERSED`, no transition back to `APPROVED` without a new claim.

## 4. Domain services

### AdjudicationEngine
Pure domain service. Inputs: `Claim` (with category + billed amount), optional member accumulator context.  
Outputs: updated claim amounts + status + optional accumulator delta.

Coverage rate table (MVP — hard-coded in domain or loaded via port later):

| BenefitCategory | Rate |
|-----------------|------|
| IN_NETWORK_PRIMARY_CARE | 0.80 |
| OUT_OF_NETWORK_SPECIALIST | 0.50 |
| PREVENTIVE_SCREENING | 1.00 |
| EXCLUDED_COSMETIC | 0.00 |
| GENERAL_MEDICAL | 0.70 |

Formula (HALF_EVEN, scale 2):

```
adjudicatedAmount = billedAmount × rate
patientCoPay      = billedAmount − adjudicatedAmount
```

### ClaimFactory / ClaimReferenceGenerator
Creates a new Claim in `PENDING` with a fresh ClaimReference.

## 5. State machine

```
PENDING ──────► UNDER_REVIEW
   │                 │
   ├────► APPROVED ◄─┤
   │         │
   │         └──► REVERSED
   └────► DENIED
```

- Automatic path for MVP: submit → validate → adjudicate → `APPROVED` or `DENIED` (timely filing / excluded).
- Duplicate detection (same memberId + providerId + serviceDate + billedAmount within 24 h) → `UNDER_REVIEW`.

## 6. Domain exceptions (examples)
- `InvalidClaimException` — validation failures (amount, date, format)
- `ClaimNotFoundException`
- `IllegalClaimStateException` — forbidden transition

These are mapped to HTTP in the web adapter only.
