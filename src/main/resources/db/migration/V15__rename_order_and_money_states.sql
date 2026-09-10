-- Переименовывает состояния заказа, доставки, расчётов и финансовых операций.
-- Старые миграции не изменяются, поэтому существующая база обновляется безопасно через Flyway.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM payment_transactions
        WHERE type = 'WITHDRAWAL_REVERSAL'
    ) THEN
        RAISE EXCEPTION
            'Нельзя удалить WITHDRAWAL_REVERSAL: в payment_transactions существуют такие операции';
    END IF;
END $$;

ALTER TABLE orders
    DROP CONSTRAINT chk_orders_status;

UPDATE orders
SET status = CASE status
    WHEN 'PROCESSING' THEN 'SELLERSSTARTWORK'
    WHEN 'SHIPPED' THEN 'SELLERSENDWORKANDSEND'
    ELSE status
END;

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_status
        CHECK (status IN (
            'CREATED',
            'CONFIRMED',
            'SELLERSSTARTWORK',
            'SELLERSENDWORKANDSEND',
            'COMPLETED',
            'CANCELLED'
        ));

ALTER TABLE seller_orders
    DROP CONSTRAINT seller_orders_status_check,
    DROP CONSTRAINT chk_seller_orders_settlement_status;

UPDATE seller_orders
SET status = CASE status
    WHEN 'SHIPPED' THEN 'SELLERSENDPRODUCT'
    WHEN 'DELIVERED' THEN 'USERGETPRODUCT'
    ELSE status
END;

UPDATE seller_orders
SET settlement_status = CASE settlement_status
    WHEN 'NOT_ACCRUED' THEN 'NOT_DISTRIBUTE'
    WHEN 'PENDING' THEN 'PENDINGWALLET'
    WHEN 'AVAILABLE' THEN 'MAINWALLET'
    WHEN 'REVERSED' THEN 'RETURNMONEY'
    ELSE settlement_status
END;

ALTER TABLE seller_orders
    ADD CONSTRAINT seller_orders_status_check
        CHECK (status IN (
            'NEW',
            'PROCESSING',
            'SELLERSENDPRODUCT',
            'USERGETPRODUCT',
            'CANCELLED',
            'RETURNED'
        )),
    ADD CONSTRAINT chk_seller_orders_settlement_status
        CHECK (settlement_status IN (
            'NOT_DISTRIBUTE',
            'PENDINGWALLET',
            'MAINWALLET',
            'RETURNMONEY'
        ));

DROP INDEX uk_payment_transaction_settlement_step;

ALTER TABLE payment_transactions
    DROP CONSTRAINT chk_payment_transactions_type,
    DROP CONSTRAINT chk_payment_transactions_status,
    DROP CONSTRAINT chk_payment_transactions_order_link,
    DROP CONSTRAINT chk_payment_transactions_wallet_link;

-- Самое длинное новое название типа не помещается в прежний VARCHAR(30).
ALTER TABLE payment_transactions
    ALTER COLUMN type TYPE VARCHAR(40);

UPDATE payment_transactions
SET type = CASE type
    WHEN 'SELLER_ACCRUAL' THEN 'SELLER_PENDINGWALLET'
    WHEN 'FUNDS_RELEASE' THEN 'SELLER_MAINWALLET'
    WHEN 'WITHDRAWAL' THEN 'SELLER_TRANSFERMONEYFROMMAINWALLET'
    WHEN 'REFUND' THEN 'RETURNMONEY'
    WHEN 'SELLER_ACCRUAL_REVERSAL' THEN 'SELLER_RETURNMONEY'
    WHEN 'PLATFORM_COMMISSION_REVERSAL' THEN 'PLATFORM_RETURMONEY'
    ELSE type
END;

-- CREATED больше не входит в TransactionStatus. Незавершённые записи становятся PENDING.
UPDATE payment_transactions
SET status = 'PENDING'
WHERE status = 'CREATED';

ALTER TABLE payment_transactions
    ADD CONSTRAINT chk_payment_transactions_type
        CHECK (type IN (
            'PAYMENT',
            'SELLER_PENDINGWALLET',
            'PLATFORM_COMMISSION',
            'SELLER_MAINWALLET',
            'SELLER_TRANSFERMONEYFROMMAINWALLET',
            'RETURNMONEY',
            'SELLER_RETURNMONEY',
            'PLATFORM_RETURMONEY'
        )),
    ADD CONSTRAINT chk_payment_transactions_status
        CHECK (status IN (
            'PENDING',
            'COMPLETED',
            'FAILED',
            'REFUNDED',
            'REVERSED'
        )),
    ADD CONSTRAINT chk_payment_transactions_order_link
        CHECK (
            order_id IS NOT NULL
            OR type = 'SELLER_TRANSFERMONEYFROMMAINWALLET'
        ),
    ADD CONSTRAINT chk_payment_transactions_wallet_link
        CHECK (
            type IN ('PAYMENT', 'RETURNMONEY')
            OR wallet_account_id IS NOT NULL
        );

CREATE UNIQUE INDEX uk_payment_transaction_settlement_step
    ON payment_transactions (seller_order_id, wallet_account_id, type)
    WHERE seller_order_id IS NOT NULL
      AND wallet_account_id IS NOT NULL
      AND type IN (
          'SELLER_PENDINGWALLET',
          'PLATFORM_COMMISSION',
          'SELLER_MAINWALLET',
          'SELLER_RETURNMONEY',
          'PLATFORM_RETURMONEY'
      );
