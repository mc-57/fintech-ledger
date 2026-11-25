# Funds Transfer Service

**Project Status**: 🚧 In Development | Deployed to cloud

## 📖 Overview 
A Java Spring Boot REST API for managing accounts, deposits, and funds transfers, with a focus on transactional correctness, 
concurrency safety, and retry safety.

The application is containerized, automatically tested through GitHub Actions, and deployed to the cloud. 
It handles simulated funds and is not a production banking system.

## 🛠️ Tech Stack
 Category | Technologies |
| --- | --- |
| Backend | Java 17, Spring Boot, Spring Security |
| Database | PostgreSQL 16, Spring Data JPA, Hibernate, Flyway |
| Testing | JUnit 5, Mockito, Testcontainers, MockMvc |
| DevOps | Docker, Docker Compose, GitHub Actions |
| Cloud hosting | Render, Neon|
| Development tools | Maven, Git, Postman |

## ✅ Implemented features

| Area | Current implementation |
| --- | --- |
| Accounts | Create accounts, fetch one/all accounts, deposit funds |
| Transfers | Transfer funds between accounts and retrieve transfer history |
| Transaction safety | Atomic balance updates, transfer recording, and idempotency reservation completion |
| Concurrency | Pessimistic row-level locking to prevent lost updates |
| Transfer idempotency | Atomic key reservation, identical-request replay, and conflicting-payload rejection |
| Idempotency lifecycle | SHA-256 request fingerprinting, 24-hour key retention, and scheduled cleanup |
| Validation and errors | Request validation and structured business error responses |
| Authentication | Stateless HTTP Basic authentication with a single in-memory administrator and BCrypt password hashing |
| Health check | Public `/healthz` endpoint for basic application availability |

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

## 🧪 Testing

Automated coverage includes service unit tests, PostgreSQL integration tests, concurrency integration tests, 
and HTTP security tests.

Concurrency tests verify lost-update prevention for deposits and transfers, identical-request replay, and same-key conflicts 
using real PostgreSQL through Testcontainers. 
GitHub Actions builds and tests the application on pushes to `main` and pull requests targeting `main`.

## ☁️ Cloud Deployment

The application is deployed on Render with a managed PostgreSQL database hosted on Neon.

**Live API:** [Health Check](https://fintech-ledger-8ges.onrender.com/healthz)

Financial endpoints are protected by HTTP Basic authentication. 
The free hosting instance may require a cold start after periods of inactivity.

## 🗺️ Roadmap

### Near-term priorities
- Deposit history and idempotent deposits
- Pagination, filtering, and sorting
- Individual user registration and authentication
- Account ownership and resource-level authorization

### Longer-term improvements
- Durable business-operation identifiers and retry policies
- Double-entry accounting and reconciliation
- Observability and application monitoring
- React + TypeScript frontend
- Automated deployment pipeline

## 📚 Documentation

- [System Architecture](docs/ARCHITECTURE.md)
- [API Reference](docs/API.md)