# 04 — Events & Messaging Specification

## 1. Broker
- Bootstrap: `${SPRING_KAFKA_BOOTSTRAP_SERVERS:localhost:9092}`
- Topic: `claim-lifecycle-events` (create on first publish or via admin in docker-compose)
- Partitions: 3 (local)
- Key: `claimReference` (String)
- Value: JSON (Jackson)
- Producer acks: `all` (demo may use `1`)

## 2. Envelope (all events)

```json
{
  "eventId": "uuid",
  "eventType": "CLAIM_SUBMITTED | CLAIM_ADJUDICATED | CLAIM_REVERSED",
  "timestamp": "2026-10-31T20:15:30Z",
  "payload": { }
}
```

## 3. Payload contracts

### CLAIM_SUBMITTED
Published after claim is persisted in `PENDING` (or immediately before adjudication in same transaction boundary for MVP).

```json
{
  "claimReference": "CLM-20261031-891023",
  "memberId": "MEM-99201",
  "providerId": "PRV-44102",
  "serviceDate": "2026-10-25",
  "billedAmount": "250.00",
  "benefitCategory": "IN_NETWORK_PRIMARY_CARE"
}
```

### CLAIM_ADJUDICATED
Published when status becomes `APPROVED` or `DENIED`.

```json
{
  "claimReference": "CLM-20261031-891023",
  "memberId": "MEM-99201",
  "status": "APPROVED",
  "billedAmount": "250.00",
  "adjudicatedAmount": "200.00",
  "patientCoPay": "50.00",
  "denialReason": null
}
```

### CLAIM_REVERSED
```json
{
  "claimReference": "CLM-20261031-891023",
  "memberId": "MEM-99201",
  "previousAdjudicatedAmount": "200.00",
  "status": "REVERSED"
}
```

Monetary fields in JSON **must be strings** (or numbers with exactly 2 decimal places) — prefer **strings** to preserve precision, consistent with OpenAPI `Money` type.

## 4. care-notification-service behaviour

- Group id: `care-notification-group`
- Topic: `claim-lifecycle-events`
- On `CLAIM_ADJUDICATED` with status `APPROVED` or `DENIED`, write an audit log line:

```
[AUDIT NOTIFICATION] Member {memberId} notified: Claim {claimReference} status updated to {status}. Payable: ${adjudicatedAmount}.
```

- On `CLAIM_REVERSED`, log:

```
[AUDIT NOTIFICATION] Member {memberId}: Claim {claimReference} REVERSED. Prior payable ${previousAdjudicatedAmount} reversed.
```

- Idempotency: at-least-once is acceptable for MVP; consumer should tolerate duplicates (log twice is OK for demo).

## 5. Outbound port
Domain/application must not depend on Kafka APIs.  
Define `ClaimEventPublisher` outbound port; adapter implements with `KafkaTemplate`.
