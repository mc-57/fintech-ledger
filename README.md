# Funds Transfer Service

**Project Status**: 🚧 In Development

## 📖 Overview 
A Java Spring Boot backend for managing accounts,
deposits, and funds transfers, with a focus on 
transactional correctness and concurrency safety.

## 🛠️ Tech Stack
- Java, Spring Boot - REST API, request validation, transactional services
- PostgreSQL - accounts, transfers, idempotency records, row-level locking
- Spring Data JPA/Hibernate - database access and entity mapping
- Flyway - versioned schema migrations
- Maven, Postman, Git - build workflow, manual API verification, version control

## ✅ Implemented features

| Area | Current implementation |
| --- | --- |
| Accounts | Create accounts, fetch one/all accounts, deposit funds |
| Transfers | Transfer funds between accounts and retrieve transfer history |
| Transaction safety | Single transaction covering account balance changes, transfer creation, and idempotency completion |
| Concurrency | Pessimistic row locks on affected accounts, acquired in ascending account-ID order |
| Retry safety | Atomic PostgreSQL idempotency-key reservation using `INSERT ... ON CONFLICT DO NOTHING` |
| Replay behavior | Identical retries return the original transfer; same key with different payload returns `409 Conflict` |
| Fingerprinting | SHA-256 fingerprint of sender ID, recipient ID, and normalized amount |
| Cleanup | 24-hour idempotency retention with hourly scheduled removal of expired records |
| Validation | Positive amounts, at most 17 integer digits and two decimal places, account creation validation |
| Error handling | Structured JSON responses for validation, missing resources, insufficient funds, and idempotency conflicts |

## 🔌 API endpoints

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/accounts` | Create an account |
| `GET` | `/api/accounts` | List accounts |
| `GET` | `/api/accounts/{id}` | Get account details |
| `POST` | `/api/accounts/{id}/deposits` | Deposit money |
| `POST` | `/api/transfers` | Transfer funds; requires `Idempotency-Key` header |
| `GET` | `/api/transfers` | List transfers |
| `GET` | `/api/transfers/{id}` | Get transfer details |

### Example: create a transfer

```http
POST /api/transfers
Content-Type: application/json
Idempotency-Key: demo-transfer-001
```

```json
{
  "fromAccountId": 1,
  "toAccountId": 2,
  "amount": 200.00
}
```

An identical retry with the same key returns the existing transfer rather than deducting the balance again.
A different transfer payload using that key returns `409 Conflict`. 
Existing records remain reserved for 24 hours; expired records are deleted by a scheduled job.

## 🧠 Key Engineering Decisions

**1. Transaction boundaries**. Debit, credit, transfer history, and idempotency reservation completion
share a transaction. A failed operation rolls back the whole unit of work.
**2. Pessimistic locking for balances**. Transfers acquire affected account locks in ascending ID order
to reduce deadlock risk; deposits lock the account being modified. This addresses lost updates under
concurrent operations.
**3. Atomic idempotency-key reservation**. A check-then-insert sequence is vulnerable to a race. 
PostgreSQL's unique constraint and `ON CONFLICT DO NOTHING` coordinate competing inserts, including
across multiple application instances sharing a database.
**4. Replay versus duplication**. An identical retry returns the stored transfer ID. A mismatched payload
produces a conflict. If the initial transaction fails, its reservation rolls back and the key can be reserved again.

## 🧪 Manual verification performed

- Reproduced a lost-update issue with concurrent transfers before adding row locks; verified locking prevented the inconsistent outcome.
- Verified concurrent deposits and simultaneous deposit/transfer operations preserve expected balances.
- Verified two simultaneous requests using the same idempotency key and identical payload returned the same transfer ID with one committed transfer.
- Verified that the same key with a different payload produces an idempotency conflict.
- Verified that an unsuccessful transfer rolls back its idempotency reservation.
- Verified that completed idempotency records reference a non-null `transfer_id`.

> These are **manual tests**. Repeatable automated PostgreSQL integration and concurrency tests are planned.

## 🗺️ Current limitations and roadmap

### Near-term priorities
- Automated unit, PostgreSQL integration, and concurrency tests
- Deposit history and idempotent deposits
- Pagination for list endpoints
- Docker Compose and GitHub Actions CI
- Better lock-timeout and cleanup-job operational handling

### Longer-term improvements
- Authentication and authorization
- Durable business-operation IDs for retries beyond the 24-hour idempotency retention window
- Double-entry accounting, reconciliation, and stronger financial auditability
- Observability and a minimal UI 

**Important design limitation**: After an expired idempotency record is cleaned up, a late client retry with the same key can create a new transfer. 
A durable business operation identifier and client retry policy are not implemented yet.

## 📌 Project status
This repository is actively developed in small, independently reviewed increments. 
The focus is on backend correctness and explaining the design decisions, not claiming 
production readiness before security, tests, and operational safeguards are complete.

