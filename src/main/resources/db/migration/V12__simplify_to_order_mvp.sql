-- MVP: cart -> order -> mock payment -> independent seller shipments -> receipt.
-- V1-V11 stay immutable. Archive removed features instead of losing their data.
-- current_schema keeps isolated test databases/schemas independent of public.
DO $$
DECLARE
    source_schema text := current_schema();
    archive_schema text := source_schema || '_pre_mvp';
    table_name text;
BEGIN
    EXECUTE format('CREATE SCHEMA %I', archive_schema);
    EXECUTE format('CREATE TABLE %I.order_metadata AS SELECT id, payment_expires_at, commission_rate, return_deadline, funds_released FROM %I.orders',
        archive_schema, source_schema);
    EXECUTE format('CREATE TABLE %I.payment_transactions AS TABLE %I.payment_transactions', archive_schema, source_schema);
    EXECUTE format('CREATE TABLE %I.product_visibility AS SELECT id, hidden FROM %I.products', archive_schema, source_schema);

    -- Direct seller registration replaces the application process for active applicants.
    INSERT INTO user_roles(user_id, role_id)
    SELECT a.user_id, r.id FROM seller_applications a
    JOIN users u ON u.id = a.user_id CROSS JOIN roles r
    WHERE a.status IN ('PENDING', 'ACTIVE') AND u.status = 'ACTIVE' AND r.name = 'SELLER'
    ON CONFLICT DO NOTHING;

    FOREACH table_name IN ARRAY ARRAY['seller_applications', 'return_requests',
            'withdrawal_requests', 'platform_settings', 'wallet_accounts', 'audit_events']
    LOOP
        EXECUTE format('ALTER TABLE %I.%I SET SCHEMA %I', source_schema, table_name, archive_schema);
    END LOOP;
END $$;

-- V11 reserved these items at creation. Release them ONCE during this migration.
-- From V12 onward stock is decremented atomically only by a successful payment.
UPDATE products p SET quantity = p.quantity + reserved.quantity
FROM (
    SELECT i.product_id, SUM(i.quantity)::integer AS quantity
    FROM order_items i JOIN orders o ON o.id = i.order_id
    WHERE o.status = 'CREATED' AND o.payment_status IN ('NOT_PAID', 'FAILED')
    GROUP BY i.product_id
) reserved WHERE p.id = reserved.product_id;

ALTER TABLE orders DROP COLUMN payment_expires_at;
ALTER TABLE orders DROP COLUMN commission_rate;
ALTER TABLE orders DROP COLUMN return_deadline;
ALTER TABLE orders DROP COLUMN funds_released;

-- Do not expose previously moderated products automatically.
UPDATE products SET active = FALSE WHERE hidden = TRUE;
ALTER TABLE products DROP COLUMN hidden;

DELETE FROM payment_transactions WHERE type <> 'PAYMENT';
ALTER TABLE payment_transactions DROP COLUMN pending;
ALTER TABLE payment_transactions DROP CONSTRAINT chk_transaction_order;
ALTER TABLE payment_transactions ALTER COLUMN order_id SET NOT NULL;
ALTER TABLE payment_transactions DROP CONSTRAINT chk_payment_transactions_type;
ALTER TABLE payment_transactions ADD CONSTRAINT chk_payment_transactions_type CHECK (type = 'PAYMENT');

ALTER TABLE seller_orders DROP CONSTRAINT seller_orders_status_check;
UPDATE seller_orders SET status = 'PROCESSING' WHERE status IN ('ACCEPTED', 'PACKING');
ALTER TABLE seller_orders ADD CONSTRAINT seller_orders_status_check
    CHECK (status IN ('NEW','PROCESSING','SHIPPED','DELIVERED','CANCELLED','RETURNED'));

ALTER TABLE orders DROP CONSTRAINT chk_orders_status;
ALTER TABLE orders ADD CONSTRAINT chk_orders_status
    CHECK (status IN ('CREATED','CONFIRMED','PROCESSING','SHIPPED','COMPLETED','CANCELLED'));
UPDATE orders o SET status = 'SHIPPED'
WHERE o.status IN ('CONFIRMED', 'PROCESSING') AND o.payment_status = 'PAID'
AND EXISTS (SELECT 1 FROM seller_orders s WHERE s.order_id = o.id)
AND NOT EXISTS (SELECT 1 FROM seller_orders s WHERE s.order_id = o.id AND s.status NOT IN ('SHIPPED','DELIVERED'));
