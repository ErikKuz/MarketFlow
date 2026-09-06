package com.example.marketflow.marketplace;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.sql.Connection;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Runs only against an explicitly supplied test PostgreSQL, in unique schemas. */
@EnabledIfEnvironmentVariable(named = "MARKETFLOW_TEST_POSTGRES_URL", matches = "jdbc:postgresql:.*")
class MigrationPostgresTest {
    @Test
    void freshDatabaseAppliesAllMigrationsAndSecondRunIsNoOp() {
        var schema = PostgresTestSupport.schema("migration_fresh_");
        var flyway = flyway(schema, "11");
        assertEquals(11, flyway.migrate().migrationsExecuted);
        flyway.validate();
        assertEquals(0, flyway.migrate().migrationsExecuted);
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
