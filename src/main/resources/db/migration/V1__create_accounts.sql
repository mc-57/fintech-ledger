CREATE TABLE accounts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_name VARCHAR(255) NOT NULL,
    balance NUMERIC(19, 2) NOT NULL DEFAULT 0.00,

    CONSTRAINT accounts_owner_name_not_blank
                      CHECK (length(trim(owner_name)) > 0),

    CONSTRAINT accounts_balance_nonnegative
                      CHECK (balance >= 0)
);

