package com.example.marketflow.marketplace;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.sql.Connection;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Запускается только с явно указанным тестовым PostgreSQL и использует отдельные уникальные схемы. */
@EnabledIfEnvironmentVariable(named = "MARKETFLOW_TEST_POSTGRES_URL", matches = "jdbc:postgresql:.*")
class MigrationPostgresTest {
    @Test
    void freshDatabaseAppliesAllMigrationsAndSecondRunIsNoOp() {
        var schema = PostgresTestSupport.schema("migration_fresh_");
        var flyway = flyway(schema, "15");
        assertEquals(15, flyway.migrate().migrationsExecuted);
        flyway.validate();
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }

    @Test
    void virtualMoneyMigrationsCreateWalletsAndBackfillPaidSellerParts() throws Exception {
        var schema = PostgresTestSupport.schema("virtual_money_upgrade_");
        flyway(schema, "12").migrate();

        try (var c = connection(schema); var sql = c.createStatement()) {
            sql.executeUpdate("""
                    INSERT INTO users(id,email,password_hash,display_name) VALUES
                    (1,'buyer-v14@test','hash','Buyer'),
                    (2,'seller-v14@test','hash','Seller');
                    INSERT INTO user_roles(user_id,role_id) VALUES (1,1),(2,1),(2,2);
                    INSERT INTO products(id,seller_id,name,price,quantity) VALUES
                    (1,2,'Product',100,5);
                    INSERT INTO orders(id,buyer_id,status,total_amount,payment_status) VALUES
                    (1,1,'CONFIRMED',100,'PAID');
                    INSERT INTO order_items(
                        order_id,product_id,seller_id,product_name,unit_price,quantity,total_price
                    ) VALUES (1,1,2,'Product',100,1,100);
                    INSERT INTO seller_orders(
                        order_id,seller_id,total_amount,status,created_at
                    ) VALUES (1,2,100,'NEW',CURRENT_TIMESTAMP);
                    INSERT INTO payment_transactions(
                        order_id,user_id,type,amount,status,idempotency_key
                    ) VALUES (1,1,'PAYMENT',100,'COMPLETED','v14-payment');
                    """);
        }

        var upgrade = flyway(schema, "15");
        assertEquals(3, upgrade.migrate().migrationsExecuted);
        upgrade.validate();

        try (var c = connection(schema); var sql = c.createStatement()) {
            try (var result = sql.executeQuery("""
                    SELECT pending_balance, available_balance
                    FROM wallet_accounts
                    WHERE user_id = 2 AND type = 'SELLER'
                    """)) {
                assertTrue(result.next());
                assertEquals(90, result.getBigDecimal(1).intValueExact());
                assertEquals(0, result.getBigDecimal(2).intValueExact());
            }

            try (var result = sql.executeQuery("""
                    SELECT pending_balance, available_balance
                    FROM wallet_accounts
                    WHERE type = 'PLATFORM'
                    """)) {
                assertTrue(result.next());
                assertEquals(10, result.getBigDecimal(1).intValueExact());
                assertEquals(0, result.getBigDecimal(2).intValueExact());
            }

            try (var result = sql.executeQuery("""
                    SELECT commission_rate, commission_amount, seller_amount, settlement_status
                    FROM seller_orders
                    WHERE order_id = 1 AND seller_id = 2
                    """)) {
                assertTrue(result.next());
                assertEquals(0, result.getBigDecimal(1).compareTo(new java.math.BigDecimal("0.1000")));
                assertEquals(10, result.getBigDecimal(2).intValueExact());
                assertEquals(90, result.getBigDecimal(3).intValueExact());
                assertEquals("PENDINGWALLET", result.getString(4));
            }

            try (var result = sql.executeQuery("""
                    SELECT count(*)
                    FROM payment_transactions
                    WHERE type IN ('SELLER_PENDINGWALLET', 'PLATFORM_COMMISSION')
                    """)) {
                assertTrue(result.next());
                assertEquals(2, result.getInt(1));
            }

            sql.executeUpdate("""
                    INSERT INTO payment_transactions(
                        order_id,user_id,wallet_account_id,type,amount,status,
                        idempotency_key,payment_card_id
                    )
                    SELECT NULL,2,id,'SELLER_TRANSFERMONEYFROMMAINWALLET',50,'COMPLETED','v14-withdrawal',NULL
                    FROM wallet_accounts
                    WHERE user_id = 2 AND type = 'SELLER'
                    """);

            sql.executeUpdate("""
                    INSERT INTO payment_transactions(
                        order_id,user_id,related_transaction_id,type,amount,status,idempotency_key
                    )
                    SELECT 1,1,id,'RETURNMONEY',100,'COMPLETED','v14-refund'
                    FROM payment_transactions
                    WHERE idempotency_key = 'v14-payment'
                    """);
        }
    }

    @Test
    void upgradePreservesOrdersAndMovesOnlyOpenIncomeIntoPendingBalance() throws Exception {
        var schema = PostgresTestSupport.schema("migration_upgrade_");
        flyway(schema, "10").migrate();
        try (var connection = connection(schema); var sql = connection.createStatement()) {
            sql.executeUpdate("""
                    INSERT INTO users(id,email,password_hash,display_name) VALUES
                    (1,'buyer@test','hash','Buyer'),(2,'seller@test','hash','Seller'),(3,'owner@test','hash','Owner');
                    INSERT INTO user_roles(user_id,role_id) VALUES (1,1),(2,2),(3,5);
                    INSERT INTO products(id,seller_id,name,price,quantity) VALUES (1,2,'Product',100,8);
                    INSERT INTO orders(id,buyer_id,status,total_amount,payment_status) VALUES
                    (1,1,'CONFIRMED',100,'PAID'), (2,1,'COMPLETED',100,'PAID'), (3,1,'CREATED',100,'NOT_PAID');
                    INSERT INTO order_items(order_id,product_id,seller_id,product_name,unit_price,quantity,total_price)
                    SELECT id,1,2,'Product',100,1,100 FROM orders;
                    INSERT INTO wallet_accounts(user_id,balance) VALUES (2,180),(3,20);
                    INSERT INTO payment_transactions(order_id,user_id,type,amount,status,idempotency_key) VALUES
                    (1,2,'SELLER_PAYOUT',90,'COMPLETED','open-seller'),
                    (1,3,'PLATFORM_COMMISSION',10,'COMPLETED','open-owner'),
                    (2,2,'SELLER_PAYOUT',90,'COMPLETED','closed-seller'),
                    (2,3,'PLATFORM_COMMISSION',10,'COMPLETED','closed-owner');
                    """);
        }
        var upgrade = flyway(schema, "11");
        assertEquals(1, upgrade.migrate().migrationsExecuted);
        upgrade.validate();
        try (var c = connection(schema); var s = c.createStatement()) {
            try (var r = s.executeQuery("SELECT balance, pending_balance FROM wallet_accounts WHERE user_id=2")) {
                assertTrue(r.next());
                assertEquals(90, r.getBigDecimal(1).intValueExact());
                assertEquals(90, r.getBigDecimal(2).intValueExact());
            }
            try (var r = s.executeQuery("SELECT count(*), count(*) FILTER (WHERE status='DELIVERED') FROM seller_orders")) {
                assertTrue(r.next()); assertEquals(3, r.getInt(1)); assertEquals(1, r.getInt(2));
            }
            try (var r = s.executeQuery("SELECT funds_released, return_deadline FROM orders WHERE id=2")) {
                assertTrue(r.next()); assertTrue(r.getBoolean(1)); assertNull(r.getObject(2));
            }
            try (var r = s.executeQuery("SELECT payment_expires_at FROM orders WHERE id=3")) {
                assertTrue(r.next()); assertNotNull(r.getObject(1));
            }
            try (var r = s.executeQuery("SELECT status FROM seller_applications WHERE user_id=2")) {
                assertTrue(r.next()); assertEquals("ACTIVE", r.getString(1));
            }
            assertThrows(java.sql.SQLException.class, () -> s.executeUpdate(
                    "UPDATE wallet_accounts SET pending_balance=-1 WHERE user_id=2"));
        }
    }

    @Test
    void mvpMigrationArchivesOldFeaturesAndReleasesOnlyUnpaidReservationsOnce() throws Exception {
        var schema = PostgresTestSupport.schema("mvp_upgrade_");
        flyway(schema, "11").migrate();
        try (var c = connection(schema); var sql = c.createStatement()) {
            sql.executeUpdate("""
                INSERT INTO users(id,email,password_hash,display_name) VALUES
                (1,'buyer@test','hash','Buyer'),(2,'seller@test','hash','Seller');
                INSERT INTO user_roles(user_id,role_id) VALUES (1,1),(2,1),(2,2);
                INSERT INTO products(id,seller_id,name,price,quantity,hidden) VALUES (1,2,'Product',100,5,true);
                INSERT INTO orders(id,buyer_id,status,total_amount,payment_status) VALUES
                (1,1,'PROCESSING',100,'PAID'),(2,1,'CREATED',100,'NOT_PAID'),(3,1,'CANCELLED',100,'NOT_PAID');
                INSERT INTO order_items(order_id,product_id,seller_id,product_name,unit_price,quantity,total_price)
                SELECT id,1,2,'Product',100,1,100 FROM orders;
                INSERT INTO seller_orders(order_id,seller_id,total_amount,status,created_at) VALUES
                (1,2,100,'PACKING',now()),(2,2,100,'NEW',now()),(3,2,100,'CANCELLED',now());
                INSERT INTO wallet_accounts(user_id,balance) VALUES (2,90);
                INSERT INTO payment_transactions(order_id,user_id,type,amount,status,idempotency_key) VALUES
                (1,1,'PAYMENT',100,'COMPLETED','payment'),(1,2,'SELLER_PAYOUT',90,'COMPLETED','seller');
                """);
        }
        var upgrade = flyway(schema, "12");
        assertEquals(1, upgrade.migrate().migrationsExecuted);
        upgrade.validate();
        assertEquals(0, upgrade.migrate().migrationsExecuted);
        try (var c = connection(schema); var s = c.createStatement()) {
            try (var r = s.executeQuery("SELECT quantity,active FROM products WHERE id=1")) {
                assertTrue(r.next()); assertEquals(6, r.getInt(1)); assertFalse(r.getBoolean(2));
            }
            try (var r = s.executeQuery("SELECT count(*) FROM payment_transactions")) {
                assertTrue(r.next()); assertEquals(1, r.getInt(1));
            }
            try (var r = s.executeQuery("SELECT count(*) FROM " + schema + "_pre_mvp.payment_transactions")) {
                assertTrue(r.next()); assertEquals(2, r.getInt(1));
            }
            try (var r = s.executeQuery("SELECT count(*) FROM " + schema + "_pre_mvp.order_metadata")) {
                assertTrue(r.next()); assertEquals(3, r.getInt(1));
            }
            try (var r = s.executeQuery("SELECT balance FROM " + schema + "_pre_mvp.wallet_accounts WHERE user_id=2")) {
                assertTrue(r.next()); assertEquals(90, r.getInt(1));
            }
            try (var r = s.executeQuery("SELECT status FROM seller_orders WHERE order_id=1")) {
                assertTrue(r.next()); assertEquals("PROCESSING", r.getString(1));
            }
            try (var r = s.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_schema='" + schema
                    + "' AND table_name IN ('wallet_accounts','audit_events','seller_applications','return_requests','withdrawal_requests','platform_settings')")) {
                assertTrue(r.next()); assertEquals(0, r.getInt(1));
            }
        }
    }

    private Flyway flyway(String schema, String target) {
        return Flyway.configure().dataSource(PostgresTestSupport.url(schema),
                PostgresTestSupport.username(), PostgresTestSupport.password())
                .schemas(schema).target(target).load();
    }

    private Connection connection(String schema) throws Exception {
        return DriverManager.getConnection(PostgresTestSupport.url(schema),
                PostgresTestSupport.username(), PostgresTestSupport.password());
    }
}
