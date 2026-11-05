-- NOTE: This schema currently stores completed transfers only.
-- It does not track IN_PROGRESS requests or reserve keys before processing.
-- TODO: Support atomic key reservation for safe concurrent retries.

CREATE TABLE transfer_idempotency (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    request_fingerprint VARCHAR(64) NOT NULL,

    transfer_id BIGINT NOT NULL REFERENCES transfers(transfer_id),

    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT idempotency_expiration_valid
        CHECK (expires_at > created_at)
);