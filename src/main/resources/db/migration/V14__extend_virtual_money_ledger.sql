-- Persistence model for virtual seller accruals, platform commission,
-- wallet withdrawals and refunds. No external bank is involved.

-- Keep the financial result of every seller part directly beside its lifecycle.
ALTER TABLE seller_orders
    ADD COLUMN commission_rate NUMERIC(5, 4) NOT NULL DEFAULT 0.1000,
    ADD COLUMN commission_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    ADD COLUMN seller_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    ADD COLUMN settlement_status VARCHAR(20) NOT NULL DEFAULT 'NOT_ACCRUED';

ALTER TABLE seller_orders
    ADD CONSTRAINT chk_seller_orders_commission_rate
        CHECK (commission_rate >= 0 AND commission_rate < 1),
    ADD CONSTRAINT chk_seller_orders_commission_amount
        CHECK (commission_amount >= 0),
    ADD CONSTRAINT chk_seller_orders_seller_amount
        CHECK (seller_amount >= 0),
    ADD CONSTRAINT chk_seller_orders_financial_total
        CHECK (commission_amount + seller_amount <= total_amount),
    ADD CONSTRAINT chk_seller_orders_settlement_status
        CHECK (settlement_status IN ('NOT_ACCRUED', 'PENDING', 'AVAILABLE', 'REVERSED'));

CREATE INDEX idx_seller_orders_settlement
    ON seller_orders (settlement_status, seller_id);

-- Reconstruct a financial snapshot for paid historical seller parts.
UPDATE seller_orders seller_order
SET commission_amount = ROUND(seller_order.total_amount * seller_order.commission_rate, 2),
    seller_amount = seller_order.total_amount
        - ROUND(seller_order.total_amount * seller_order.commission_rate, 2),
    settlement_status = CASE
        WHEN orders.payment_status = 'REFUNDED' THEN 'REVERSED'
        WHEN seller_order.status = 'DELIVERED' THEN 'AVAILABLE'
        ELSE 'PENDING'
    END
FROM orders
WHERE orders.id = seller_order.order_id
  AND orders.payment_status IN ('PAID', 'REFUNDED');

-- Extend the existing payment journal instead of creating separate tables
-- for every kind of virtual money movement.
ALTER TABLE payment_transactions
    ALTER COLUMN order_id DROP NOT NULL,
    ALTER COLUMN user_id DROP NOT NULL,
    ALTER COLUMN idempotency_key TYPE VARCHAR(150),
    ADD COLUMN seller_order_id BIGINT,
    ADD COLUMN wallet_account_id BIGINT,
    ADD COLUMN related_transaction_id BIGINT,
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE payment_transactions
    ADD CONSTRAINT fk_payment_transactions_seller_order
        FOREIGN KEY (seller_order_id)
        REFERENCES seller_orders (id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT fk_payment_transactions_wallet
        FOREIGN KEY (wallet_account_id)
        REFERENCES wallet_accounts (id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT fk_payment_transactions_related
        FOREIGN KEY (related_transaction_id)
        REFERENCES payment_transactions (id)
        ON DELETE RESTRICT;

ALTER TABLE payment_transactions
    DROP CONSTRAINT chk_payment_transactions_type,
    DROP CONSTRAINT chk_payment_transactions_status;

ALTER TABLE payment_transactions
    ADD CONSTRAINT chk_payment_transactions_type
        CHECK (type IN (
            'PAYMENT',
            'SELLER_ACCRUAL',
            'PLATFORM_COMMISSION',
            'FUNDS_RELEASE',
            'WITHDRAWAL',
            'REFUND',
            'SELLER_ACCRUAL_REVERSAL',
            'PLATFORM_COMMISSION_REVERSAL',
            'WITHDRAWAL_REVERSAL'
        )),
    ADD CONSTRAINT chk_payment_transactions_status
        CHECK (status IN (
            'CREATED',
            'PENDING',
            'COMPLETED',
            'FAILED',
            'REFUNDED',
            'REVERSED'
        )),
    ADD CONSTRAINT chk_payment_transactions_order_link
        CHECK (
            order_id IS NOT NULL
            OR type IN ('WITHDRAWAL', 'WITHDRAWAL_REVERSAL')
        ),
    ADD CONSTRAINT chk_payment_transactions_wallet_link
        CHECK (
            type IN ('PAYMENT', 'REFUND')
            OR wallet_account_id IS NOT NULL
        );

CREATE INDEX idx_payment_transactions_seller_order_id
    ON payment_transactions (seller_order_id);

CREATE INDEX idx_payment_transactions_wallet_account_id
    ON payment_transactions (wallet_account_id, created_at DESC);

CREATE INDEX idx_payment_transactions_related_id
    ON payment_transactions (related_transaction_id);

-- Prevent duplicate accrual, commission, release or reversal for one wallet
-- and one seller part even when the business method is called twice.
CREATE UNIQUE INDEX uk_payment_transaction_settlement_step
    ON payment_transactions (seller_order_id, wallet_account_id, type)
    WHERE seller_order_id IS NOT NULL
      AND wallet_account_id IS NOT NULL
      AND type IN (
          'SELLER_ACCRUAL',
          'PLATFORM_COMMISSION',
          'FUNDS_RELEASE',
          'SELLER_ACCRUAL_REVERSAL',
          'PLATFORM_COMMISSION_REVERSAL'
      );

-- A transaction can be reversed/refunded only once in this simplified model.
CREATE UNIQUE INDEX uk_payment_transaction_related_type
    ON payment_transactions (related_transaction_id, type)
    WHERE related_transaction_id IS NOT NULL;

-- Restore current virtual balances for orders that were already paid before V14.
WITH seller_balances AS (
    SELECT
        seller_id,
        COALESCE(SUM(seller_amount) FILTER (
            WHERE settlement_status = 'PENDING'
        ), 0) AS pending_amount,
        COALESCE(SUM(seller_amount) FILTER (
            WHERE settlement_status = 'AVAILABLE'
        ), 0) AS available_amount
    FROM seller_orders
    GROUP BY seller_id
)
UPDATE wallet_accounts wallet
SET pending_balance = seller_balances.pending_amount,
    available_balance = seller_balances.available_amount,
    updated_at = CURRENT_TIMESTAMP
FROM seller_balances
WHERE wallet.type = 'SELLER'
  AND wallet.user_id = seller_balances.seller_id;

UPDATE wallet_accounts wallet
SET pending_balance = totals.pending_amount,
    available_balance = totals.available_amount,
    updated_at = CURRENT_TIMESTAMP
FROM (
    SELECT
        COALESCE(SUM(commission_amount) FILTER (
            WHERE settlement_status = 'PENDING'
        ), 0) AS pending_amount,
        COALESCE(SUM(commission_amount) FILTER (
            WHERE settlement_status = 'AVAILABLE'
        ), 0) AS available_amount
    FROM seller_orders
) totals
WHERE wallet.type = 'PLATFORM';

-- Add ledger entries for historical paid orders so balances and history agree.
INSERT INTO payment_transactions (
    order_id,
    user_id,
    seller_order_id,
    wallet_account_id,
    type,
    amount,
    status,
    idempotency_key
)
SELECT
    seller_order.order_id,
    seller_order.seller_id,
    seller_order.id,
    wallet.id,
    'SELLER_ACCRUAL',
    seller_order.seller_amount,
    'COMPLETED',
    'v14:seller-accrual:' || seller_order.id
FROM seller_orders seller_order
JOIN wallet_accounts wallet
    ON wallet.type = 'SELLER'
   AND wallet.user_id = seller_order.seller_id
WHERE seller_order.settlement_status IN ('PENDING', 'AVAILABLE')
  AND seller_order.seller_amount > 0;

INSERT INTO payment_transactions (
    order_id,
    user_id,
    seller_order_id,
    wallet_account_id,
    type,
    amount,
    status,
    idempotency_key
)
SELECT
    seller_order.order_id,
    NULL,
    seller_order.id,
    wallet.id,
    'PLATFORM_COMMISSION',
    seller_order.commission_amount,
    'COMPLETED',
    'v14:platform-commission:' || seller_order.id
FROM seller_orders seller_order
CROSS JOIN wallet_accounts wallet
WHERE wallet.type = 'PLATFORM'
  AND seller_order.settlement_status IN ('PENDING', 'AVAILABLE')
  AND seller_order.commission_amount > 0;

INSERT INTO payment_transactions (
    order_id,
    user_id,
    seller_order_id,
    wallet_account_id,
    type,
    amount,
    status,
    idempotency_key
)
SELECT
    seller_order.order_id,
    seller_order.seller_id,
    seller_order.id,
    wallet.id,
    'FUNDS_RELEASE',
    seller_order.seller_amount,
    'COMPLETED',
    'v14:seller-release:' || seller_order.id
FROM seller_orders seller_order
JOIN wallet_accounts wallet
    ON wallet.type = 'SELLER'
   AND wallet.user_id = seller_order.seller_id
WHERE seller_order.settlement_status = 'AVAILABLE'
  AND seller_order.seller_amount > 0;

INSERT INTO payment_transactions (
    order_id,
    user_id,
    seller_order_id,
    wallet_account_id,
    type,
    amount,
    status,
    idempotency_key
)
SELECT
    seller_order.order_id,
    NULL,
    seller_order.id,
    wallet.id,
    'FUNDS_RELEASE',
    seller_order.commission_amount,
    'COMPLETED',
    'v14:platform-release:' || seller_order.id
FROM seller_orders seller_order
CROSS JOIN wallet_accounts wallet
WHERE wallet.type = 'PLATFORM'
  AND seller_order.settlement_status = 'AVAILABLE'
  AND seller_order.commission_amount > 0;
