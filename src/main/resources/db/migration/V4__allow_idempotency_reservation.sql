-- TRF-07: Allow an idempotency key to be reserved
-- before its corresponding transfer is created.

ALTER TABLE transfer_idempotency
ALTER COLUMN transfer_id DROP NOT NULL;