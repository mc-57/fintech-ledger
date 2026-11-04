CREATE TABLE transfers (
    transfer_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    from_account_id BIGINT NOT NULL,
    to_account_id BIGINT NOT NULL,
    transfer_amount NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_transfer_sender
                      FOREIGN KEY (from_account_id) REFERENCES accounts(id),

    CONSTRAINT fk_transfer_recipient
                      FOREIGN KEY (to_account_id) REFERENCES accounts(id),

    CONSTRAINT transfers_amount_positive
                      CHECK (transfer_amount > 0),

    CONSTRAINT transfers_accounts_different
                      CHECK (from_account_id <> to_account_id)
);