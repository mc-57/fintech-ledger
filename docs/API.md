# 🔌 API Reference

The Funds Transfer Service exposes REST endpoints for managing accounts, deposits, and transfers using simulated funds.

## Base URLs and authentication

- Local: `http://localhost:8080`
- Hosted: `https://fintech-ledger-8ges.onrender.com`

All financial endpoints require HTTP Basic authentication. In Postman, select **Basic Auth** and supply the configured administrator credentials. Use HTTPS for the hosted API. `GET /healthz` is public.

Send JSON request bodies with `Content-Type: application/json`. Examples below use illustrative account IDs and balances.

## Endpoints

| Method | Path | Purpose | Success status |
| --- | --- | --- | --- |
| GET | `/healthz` | Basic application availability (`OK`) | 200 |
| POST | `/api/accounts` | Create an account with zero balance | 201 |
| GET | `/api/accounts` | List accounts | 200 |
| GET | `/api/accounts/{id}` | Retrieve an account | 200 |
| POST | `/api/accounts/{id}/deposits` | Deposit funds and return the updated account | 200 |
| POST | `/api/transfers` | Transfer funds between accounts | 201 |
| GET | `/api/transfers` | List transfers | 200 |
| GET | `/api/transfers/{id}` | Retrieve a transfer | 200 |

List endpoints return JSON arrays ordered by ID, or an empty array when no records exist. Pagination is not yet implemented. Account and transfer creation responses include a `Location` header identifying the resource.

## Request examples

### Create an account — `POST /api/accounts`

```json
{
  "ownerName": "Alice"
}
```

Account response (also used by account retrieval and deposits):

```json
{
  "id": 1,
  "ownerName": "Alice",
  "balance": 0.00
}
```

### Deposit funds — `POST /api/accounts/1/deposits`

```json
{
  "amount": 500.00
}
```

Returns the updated account. Deposits currently have no idempotency protection; repeating a successful request adds funds again.

### Transfer funds — `POST /api/transfers`

Required header: `Idempotency-Key: demo-transfer-001`

```json
{
  "fromAccountId": 1,
  "toAccountId": 2,
  "amount": 100.00
}
```

Transfer response (also used by transfer retrieval):

```json
{
  "id": 1,
  "fromAccountId": 1,
  "toAccountId": 2,
  "amount": 100.00,
  "createdAt": "2026-11-25T00:00:00Z"
}
```

## Validation and retry behavior

- Account owner names must not be blank.
- Deposit and transfer amounts must be positive, with at most 17 integer digits and two decimal places.
- Sender and recipient IDs are required and must identify different existing accounts. The sender must have sufficient funds.
- Transfer idempotency keys must contain 1–128 characters, begin with an ASCII letter or digit, and contain only ASCII letters, digits, underscores or hyphens.
- An identical retry with the same unexpired key returns the original transfer with `201 Created` and does not change balances again. Reusing the key with a different valid payload returns `409 Conflict`.
- Keys expire after 24 hours. Expired keys are rejected until cleanup removes them; a retry after removal may create another transfer.

## Error responses

Handled validation and business errors use the following JSON structure:

```json
{
  "status": 404,
  "error": "ACCOUNT_NOT_FOUND",
  "message": "Account not found: 999"
}
```

| Status | Error code | Reason |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Invalid request fields or validated header values |
| 400 | `MISSING_HEADER` | Required header missing |
| 400 | `INVALID_ACCOUNT_TRANSFER` | Sender and recipient are the same account |
| 401 | — | Missing or invalid authentication |
| 404 | `ACCOUNT_NOT_FOUND` | Account does not exist |
| 404 | `TRANSFER_RECORD_NOT_FOUND` | Transfer does not exist |
| 409 | `INSUFFICIENT_FUNDS` | Sender has insufficient balance |
| 409 | `IDEMPOTENCY_CONFLICT` | Key conflicts with request data or has expired but remains stored |

Authentication failures are handled by Spring Security and are not guaranteed to use the business error JSON format.
