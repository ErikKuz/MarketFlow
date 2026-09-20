ALTER TABLE wallet_accounts
    ADD COLUMN reserved_balance NUMERIC(14, 2) NOT NULL DEFAULT 0;

ALTER TABLE wallet_accounts
    ADD CONSTRAINT chk_wallet_accounts_reserved_balance
        CHECK (reserved_balance >= 0);
