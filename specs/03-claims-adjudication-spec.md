# 03 — Claims Adjudication Specification (Example Mapping)

**User story**  
As a claims platform, I want submitted medical claims to be validated and automatically adjudicated so that plan payment and member responsibility are calculated consistently and auditably.

---

## Rules

### Rule R1 — Minimum billed threshold
Claims with `billedAmount < 5.00` are rejected at the API boundary (HTTP 400).  
Exactly `5.00` is accepted.

### Rule R2 — Timely filing
If `serviceDate` is more than 365 days before today → claim is accepted into the system but adjudicated as `DENIED` with reason `TIMELY_FILING_EXCEEDED`.  
`adjudicatedAmount = 0`, `patientCoPay = billedAmount`.

### Rule R3 — Coverage by benefit category
Apply the rate table from the domain model. Use `RoundingMode.HALF_EVEN` scale 2.

### Rule R4 — Preventive is fully covered
`PREVENTIVE_SCREENING` → rate 1.00 → member co-pay 0.

### Rule R5 — Excluded services
`EXCLUDED_COSMETIC` → rate 0 → plan pays 0, member pays full billed amount, status `DENIED` (or `APPROVED` with zero plan payment — choose **DENIED** with reason `EXCLUDED_SERVICE` for clarity).

### Rule R6 — Duplicate soft-check
If an identical claim (memberId, providerId, serviceDate, billedAmount) exists with `createdAt` within the last 24 hours → new claim status = `UNDER_REVIEW` (do not auto-approve).

### Rule R7 — Happy-path auto-adjudication
Valid, non-duplicate, non-timely-filing, non-excluded claims transition `PENDING` → `APPROVED` (or direct create as `APPROVED`) and publish `CLAIM_ADJUDICATED`.

### Rule R8 — Money rounding (Banker's)
Documented examples must match exactly.

### Rule R9 — Reversal
A previously `APPROVED` claim can be reversed → status `REVERSED`, publish reversal event, reduce member accumulator by previous `adjudicatedAmount` (MVP: accumulator port may be in-memory or DB).

---

## Concrete examples (test oracle)

### Example E1 — In-network primary care
- Category: `IN_NETWORK_PRIMARY_CARE`
- Billed: `150.00`
- Expected: adjudicated `120.00`, co-pay `30.00`, status `APPROVED`

### Example E2 — Out-of-network specialist
- Category: `OUT_OF_NETWORK_SPECIALIST`
- Billed: `300.00`
- Expected: adjudicated `150.00`, co-pay `150.00`, status `APPROVED`

### Example E3 — Preventive
- Category: `PREVENTIVE_SCREENING`
- Billed: `200.00`
- Expected: adjudicated `200.00`, co-pay `0.00`, status `APPROVED`

### Example E4 — Excluded cosmetic
- Category: `EXCLUDED_COSMETIC`
- Billed: `500.00`
- Expected: adjudicated `0.00`, co-pay `500.00`, status `DENIED`, reason `EXCLUDED_SERVICE`

### Example E5 — Below minimum threshold
- Billed: `4.99`
- Expected: HTTP 400, problem detail containing message about minimum `$5.00`

### Example E6 — Exactly minimum
- Billed: `5.00`, category `GENERAL_MEDICAL` (rate 0.70)
- Expected: accepted, adjudicated `3.50`, co-pay `1.50`, status `APPROVED`

### Example E7 — Timely filing exceeded
- serviceDate: today − 400 days
- Billed: `100.00`
- Expected: status `DENIED`, reason `TIMELY_FILING_EXCEEDED`, adjudicated `0`, co-pay `100.00`

### Example E8 — Rounding HALF_EVEN
| Billed | Rate | Expected adjudicated |
|--------|------|----------------------|
| 12.3450 with implicit calc producing .3450 intermediate | — | follow HALF_EVEN examples in original table |
| Use explicit: `billedAmount = 41.25`, rate `0.80` → adjudicated `33.00`, co-pay `8.25` |

### Example E9 — Future service date
- serviceDate: tomorrow
- Expected: HTTP 400 validation error

---

## Counter-examples
- Negative billed amount → 400
- Missing memberId / providerId → 400
- Unknown benefit category (if sent as free string) → 400 or map to GENERAL_MEDICAL only if OpenAPI enum is used (prefer reject)

---

## Acceptance test expectations
Each example E1–E9 should appear as either:
- a pure domain unit test (AdjudicationEngine), or
- an HTTP acceptance test (`*IT`) that asserts status code + body fields + (where relevant) Kafka event side-effect via embedded or test consumer.
