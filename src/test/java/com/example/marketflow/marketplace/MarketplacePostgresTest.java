package com.example.marketflow.marketplace;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.example.marketflow.security.MarketFlowPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import com.example.marketflow.AccountType;
import com.example.marketflow.Order.*;
import com.example.marketflow.Repository.*;
import com.example.marketflow.User.*;
import com.example.marketflow.cart.CartItemEntity;
import com.example.marketflow.payment.*;
import com.example.marketflow.payment_cards.PaymentCardEntity;
import com.example.marketflow.products.ProductEntity;
import com.example.marketflow.service.*;
import com.example.marketflow.userRoles.*;

/** Real service transactions, Flyway schema and PostgreSQL constraints; no enclosing test transaction. */
@SpringBootTest(properties = {"marketflow.jobs.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true", "spring.jpa.open-in-view=false"})
@Import(MarketplacePostgresTest.TimeConfiguration.class)
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "MARKETFLOW_TEST_POSTGRES_URL", matches = "jdbc:postgresql:.*")
class MarketplacePostgresTest {
    private static final String SCHEMA = PostgresTestSupport.schema("workflow_");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> PostgresTestSupport.url(SCHEMA));
        p.add("spring.datasource.username", PostgresTestSupport::username);
        p.add("spring.datasource.password", PostgresTestSupport::password);
        p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        p.add("spring.flyway.schemas", () -> SCHEMA);
    }

    @Autowired UserRepository users;
    @Autowired userRoleRepository roles;
    @Autowired ProductRepository products;
    @Autowired CartItemRepository cart;
    @Autowired OrderRepository orders;
    @Autowired PaymentCardRepository cards;
    @Autowired PaymentTransactionRepository transactions;
    @Autowired WalletAccountRepository wallets;
    @Autowired SellerOrderRepository parts;
    @Autowired SellerApplicationRepository applications;
    @Autowired OrderService orderService;
    @Autowired PaymentService payments;
    @Autowired OrderWorkflowService workflow;
    @Autowired OrderMaintenanceService maintenance;
    @Autowired ReturnService returns;
    @Autowired FinanceService finance;
    @Autowired AdministrationService administration;
    @Autowired AuthService auth;
    @Autowired CatalogService catalog;
    @Autowired AnalyticsService analytics;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired MockMvc mvc;
    private long owner;

    @BeforeEach
    void prepare() {
        clock.time = Instant.parse("2026-09-06T10:00:00Z");
        administration.initializePlatform("test-owner@example.test", "test-password-1234");
        owner = users.findByEmailIgnoreCase("test-owner@example.test").orElseThrow().getId();
        administration.changeCommission(owner, money("0.10"));
    }

    @Test
    void paymentDeliverySettlementAndWithdrawalArePersistedAndIdempotent() {
        var f = orderFixture();
        pay(f);
        moneyEquals("90.00", finance.wallet(f.seller).pending());
        moneyEquals("0.00", finance.wallet(f.seller).available());
        assertThrows(MarketplaceException.class, () -> finance.requestWithdrawal(f.seller, f.sellerCard, money("1.00"), "too-early"));
        deliver(f);
        assertEquals(OrderStatus.COMPLETED, orders.findById(f.order).orElseThrow().getStatus());
        clock.time = clock.time.plus(Duration.ofDays(8));
        maintenance.settleOne(f.order);
        maintenance.settleOne(f.order);
        moneyEquals("90.00", finance.wallet(f.seller).available());
        moneyEquals("0.00", finance.wallet(f.seller).pending());
        var request = finance.requestWithdrawal(f.seller, f.sellerCard, money("90.00"), "withdraw-once");
        assertEquals(request.id(), finance.requestWithdrawal(f.seller, f.sellerCard, money("90.00"), "withdraw-once").id());
        finance.decideWithdrawal(owner, request.id(), true, "Approved");
        finance.decideWithdrawal(owner, request.id(), true, "Repeated request");
        moneyEquals("90.00", cards.findById(f.sellerCard).orElseThrow().getBalance());
        moneyEquals("0.00", finance.wallet(f.seller).reservedForWithdrawal());
        assertEquals(1, finance.history(f.seller, 0, 100).content().stream()
                .filter(t -> t.type() == TransactionType.WITHDRAWAL).count());
    }

    @Test
    void pendingReturnPreventsSettlementAndApprovedReturnReversesFundsAndStockOnce() {
        var f = orderFixture();
        pay(f); deliver(f);
        var request = returns.request(f.buyer, f.order, "Damaged item");
        clock.time = clock.time.plus(Duration.ofDays(8));
        maintenance.settleOne(f.order);
        moneyEquals("90.00", finance.wallet(f.seller).pending());
        returns.decide(owner, request.id(), true, true, "Received returned item");
        returns.decide(owner, request.id(), true, true, "Repeated request");
        assertEquals(PaymentStatus.REFUNDED, orders.findById(f.order).orElseThrow().getPaymentStatus());
        assertEquals(OrderStatus.COMPLETED, orders.findById(f.order).orElseThrow().getStatus());
        assertEquals(5, products.findById(f.product).orElseThrow().getQuantity());
        moneyEquals("1000.00", cards.findById(f.buyerCard).orElseThrow().getBalance());
        moneyEquals("0.00", finance.wallet(f.seller).pending());
        assertEquals(1, transactions.findAllByOrderId(f.order).stream()
                .filter(t -> t.getType() == TransactionType.REFUND).count());
        assertEquals(FulfillmentStatus.RETURNED, parts.findById(f.part).orElseThrow().getStatus());
    }

    @Test
    void expiryRestoresStockOnceAndRejectsLatePayment() {
        var f = orderFixture();
        clock.time = clock.time.plus(Duration.ofMinutes(31));
        assertThrows(com.example.marketflow.exception.InvalidOrderStateException.class, () -> pay(f));
        maintenance.expireOne(f.order); maintenance.expireOne(f.order);
        assertEquals(OrderStatus.CANCELLED, orders.findById(f.order).orElseThrow().getStatus());
        assertEquals(5, products.findById(f.product).orElseThrow().getQuantity());
        moneyEquals("1000.00", cards.findById(f.buyerCard).orElseThrow().getBalance());
        assertEquals(1, workflow.buyerHistory(f.buyer, f.order).stream()
                .filter(e -> e.action().equals("ORDER_CANCELLED")).count());
    }

    @Test
    void concurrentDuplicatePaymentsAreChargedOnceWhileMaintenanceRuns() throws Exception {
        var f = orderFixture();
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Long> first = executor.submit(() -> { ready.countDown(); go.await(); return pay(f); });
            Future<Long> second = executor.submit(() -> { ready.countDown(); go.await(); maintenance.expireOne(f.order); return pay(f); });
            assertTrue(ready.await(5, TimeUnit.SECONDS)); go.countDown();
            assertEquals(f.order, first.get(15, TimeUnit.SECONDS));
            assertEquals(f.order, second.get(15, TimeUnit.SECONDS));
        }
        assertEquals(OrderStatus.CONFIRMED, orders.findById(f.order).orElseThrow().getStatus());
        moneyEquals("900.00", cards.findById(f.buyerCard).orElseThrow().getBalance());
        moneyEquals("90.00", finance.wallet(f.seller).pending());
        assertEquals(3, transactions.findAllByOrderId(f.order).size());
        assertEquals(4, products.findById(f.product).orElseThrow().getQuantity());
    }

    @Test
    void twoSellersFulfillIndependentlyAndCannotChangeEachOthersParts() {
        var f = orderFixture();
        long seller2 = createUser((short) 1, (short) 2);
        wallets.saveAndFlush(new WalletAccountEntity(seller2));
        var extra = product(seller2, "Second item", "20.00");
        // Prepare one two-seller order through the public creation path.
        orderService.cancelOrder(f.order, f.buyer);
        cart.saveAndFlush(new CartItemEntity(f.buyer, f.product));
        cart.saveAndFlush(new CartItemEntity(f.buyer, extra.getId()));
        long orderId = orderService.createOrder(f.buyer);
        payments.payOrder(orderId, f.buyer, new PayOrderRequest(f.buyerCard, "multi-" + orderId));
        var split = parts.findAllByOrderIdOrderBySellerId(orderId);
        assertEquals(2, split.size());
        var first = split.stream().filter(p -> p.getSellerId().equals(f.seller)).findFirst().orElseThrow();
        var second = split.stream().filter(p -> p.getSellerId().equals(seller2)).findFirst().orElseThrow();
        assertThrows(MarketplaceException.class, () -> workflow.sellerTransition(seller2, first.getId(), FulfillmentStatus.ACCEPTED));
        ship(f.seller, first.getId());
        workflow.confirmDelivery(f.buyer, orderId, first.getId());
        assertEquals(OrderStatus.PROCESSING, orders.findById(orderId).orElseThrow().getStatus());
        ship(seller2, second.getId());
        workflow.confirmDelivery(f.buyer, orderId, second.getId());
        assertEquals(OrderStatus.COMPLETED, orders.findById(orderId).orElseThrow().getStatus());
    }

    @Test
    void sellerApprovalCatalogAndReportsUseRealDatabase() {
        var request = new RegisterRequest();
        request.setAccountType(AccountType.SELLER); request.setEmail(UUID.randomUUID() + "@test.example");
        request.setPassword("password123"); request.setDisplay_name("Seller");
        auth.register(request);
        long seller = users.findByEmailIgnoreCase(request.getEmail()).orElseThrow().getId();
        assertFalse(roles.existsById(new UserRoleId(seller, (short) 2)));
        assertFalse(wallets.existsByUserId(seller));
        var application = applications.findByUserId(seller).orElseThrow();
        administration.decideSeller(owner, application.getId(), true, "Approved");
        assertTrue(roles.existsById(new UserRoleId(seller, (short) 2)));
        assertTrue(wallets.existsByUserId(seller));
        var visible = product(seller, "Visible keyboard", "10.00");
        var hidden = product(seller, "Hidden keyboard", "20.00");
        administration.hideProduct(owner, hidden.getId(), true, "Moderation");
        var result = catalog.search("keyboard", money("1"), money("100"), seller, "priceAsc", 0, 10);
        assertEquals(1, result.totalElements());
        assertEquals(visible.getId(), result.content().getFirst().id());
        var report = analytics.report(owner, Instant.EPOCH, Instant.parse("2100-01-01T00:00:00Z"));
        assertNotNull(report.netCommission());
        administration.blockSeller(owner, application.getId(), true, "Blocked");
        assertEquals(0, catalog.search(null, null, null, seller, "newest", 0, 10).totalElements());
        assertThrows(MarketplaceException.class, () -> finance.wallet(seller));
    }

    @Test
    void failedStockReservationRollsBackWholeOrder() {
        long buyer = createUser((short) 1), seller = createUser((short) 2);
        var good = product(seller, "Available", "10.00");
        var absent = product(seller, "No stock", "20.00");
        absent.setQuantity(0); products.saveAndFlush(absent);
        cart.saveAndFlush(new CartItemEntity(buyer, good.getId()));
        cart.saveAndFlush(new CartItemEntity(buyer, absent.getId()));
        assertThrows(com.example.marketflow.exception.NotEnoughProductQuantityException.class, () -> orderService.createOrder(buyer));
        assertEquals(5, products.findById(good.getId()).orElseThrow().getQuantity());
        assertEquals(2, cart.findAllByBuyerIdAndSelectedTrue(buyer).size());
        assertEquals(0, workflow.buyerOrders(buyer, 0, 10).totalElements());
    }

    @Test
    void missingPayoutWalletRollsBackCardDebitLedgerAndPaymentState() {
        var f = orderFixture();
        wallets.deleteById(wallets.findByUserId(f.seller).orElseThrow().getId());
        assertThrows(com.example.marketflow.exception.WalletAccountNotFoundException.class, () -> pay(f));
        moneyEquals("1000.00", cards.findById(f.buyerCard).orElseThrow().getBalance());
        assertEquals(PaymentStatus.NOT_PAID, orders.findById(f.order).orElseThrow().getPaymentStatus());
        assertEquals(OrderStatus.CREATED, orders.findById(f.order).orElseThrow().getStatus());
        assertTrue(transactions.findAllByOrderId(f.order).isEmpty());
    }

    @Test
    void commissionChangeDoesNotAlterRateAlreadySavedWithOrder() {
        var first = orderFixture();
        administration.changeCommission(owner, money("0.20"));
        var second = orderFixture();
        pay(first); pay(second);
        moneyEquals("90.00", finance.wallet(first.seller).pending());
        moneyEquals("80.00", finance.wallet(second.seller).pending());
    }

    @Test
    void workspacePagesRenderAndApiEnforcesRolesCsrfAndLiveAccountStatus() throws Exception {
        var f = orderFixture(); pay(f); deliver(f);
        var buyer = principal(f.buyer);
        var seller = principal(f.seller);
        var ownerPrincipal = principal(owner);
        for (var path : new String[]{"/workspace", "/account/orders", "/account/orders/" + f.order + "/workflow",
                "/workspace/finance", "/workspace/seller-application"}) {
            mvc.perform(get(path).with(user(buyer))).andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("text/html"));
        }
        for (var path : new String[]{"/workspace/seller/orders", "/workspace/seller/orders/" + f.part, "/workspace/finance"}) {
            mvc.perform(get(path).with(user(seller))).andExpect(status().isOk());
        }
        for (var path : new String[]{"/workspace/owner", "/workspace/moderation", "/workspace/analytics"}) {
            mvc.perform(get(path).with(user(ownerPrincipal))).andExpect(status().isOk());
        }
        mvc.perform(get("/catalog")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/orders").with(user(buyer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(f.order));
        mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/owner/users").with(user(buyer))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/orders/{id}/returns", f.order).with(user(buyer))
                .contentType("application/json").content("{\"reason\":\"Damaged\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/orders/{id}/returns", f.order).with(user(buyer)).with(csrf())
                .contentType("application/json").content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/account/orders/{id}/returns", f.order).with(user(buyer)).with(csrf())
                .param("reason", "Damaged item")).andExpect(status().is3xxRedirection());
        mvc.perform(put("/api/v1/owner/commission").with(user(ownerPrincipal)).with(csrf())
                .contentType("application/json").content("{\"rate\":0.15}"))
                .andExpect(status().isNoContent());
        moneyEquals("0.15", administration.commission(owner));
        administration.blockUser(owner, f.buyer, true, "Blocked");
        mvc.perform(get("/api/v1/orders").with(user(buyer))).andExpect(status().isUnauthorized());
    }

    private MarketFlowPrincipal principal(long id) {
        return new MarketFlowPrincipal(users.findById(id).orElseThrow(), roles.findRoleNamesByUserId(id).stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
    }

    private Fixture orderFixture() {
        long buyer = createUser((short) 1), seller = createUser((short) 1, (short) 2);
        wallets.saveAndFlush(new WalletAccountEntity(seller));
        var product = product(seller, "Keyboard", "100.00");
        cart.saveAndFlush(new CartItemEntity(buyer, product.getId()));
        long order = orderService.createOrder(buyer);
        var buyerCard = cards.saveAndFlush(new PaymentCardEntity(buyer, UUID.randomUUID().toString(), "**** 4242", money("1000.00")));
        var sellerCard = cards.saveAndFlush(new PaymentCardEntity(seller, UUID.randomUUID().toString(), "**** 1111", money("0.00")));
        return new Fixture(buyer, seller, product.getId(), order,
                parts.findAllByOrderIdOrderBySellerId(order).getFirst().getId(), buyerCard.getId(), sellerCard.getId());
    }

    private long createUser(short... assignedRoles) {
        var user = users.saveAndFlush(new UserEntity(UUID.randomUUID() + "@example.test", "test-hash", "Test User"));
        for (short role : assignedRoles) roles.saveAndFlush(new UserRolesEntity(user.getId(), role));
        return user.getId();
    }

    private ProductEntity product(long seller, String name, String price) {
        return products.saveAndFlush(new ProductEntity(seller, name, "Description", money(price), 5, "image.jpg"));
    }
    private long pay(Fixture f) {
        return payments.payOrder(f.order, f.buyer, new PayOrderRequest(f.buyerCard, "pay-" + f.order));
    }
    private void deliver(Fixture f) { ship(f.seller, f.part); workflow.confirmDelivery(f.buyer, f.order, f.part); }
    private void ship(long seller, long part) {
        workflow.sellerTransition(seller, part, FulfillmentStatus.ACCEPTED);
        workflow.sellerTransition(seller, part, FulfillmentStatus.PACKING);
        workflow.sellerTransition(seller, part, FulfillmentStatus.SHIPPED);
    }
    private static BigDecimal money(String v) { return new BigDecimal(v); }
    private static void moneyEquals(String expected, BigDecimal actual) { assertEquals(0, money(expected).compareTo(actual)); }
    private record Fixture(long buyer, long seller, long product, long order, long part, long buyerCard, long sellerCard) {}

    @TestConfiguration
    static class TimeConfiguration {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
    }
    static class MutableClock extends Clock {
        volatile Instant time = Instant.parse("2026-09-06T10:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(time, zone); }
        @Override public Instant instant() { return time; }
    }
}
