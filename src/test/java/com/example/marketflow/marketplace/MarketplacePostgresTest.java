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

/** Проверяет настоящие транзакции сервисов, схему Flyway и ограничения PostgreSQL без общей тестовой транзакции. */
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true", "spring.jpa.open-in-view=false"})
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
    @Autowired OrderService orderService;
    @Autowired PaymentService payments;
    @Autowired OrderWorkflowService workflow;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @Test
    void successfulPaymentAccruesAndDeliveryReleasesSellerAndPlatformFunds() {
        var f = orderFixture();
        var platformBefore = wallets.findByType(WalletType.PLATFORM).orElseThrow();
        BigDecimal platformPendingBefore = platformBefore.getPendingBalance();
        BigDecimal platformAvailableBefore = platformBefore.getAvailableBalance();
        assertEquals(5, products.findById(f.product).orElseThrow().getQuantity());
        assertTrue(cart.findAllByBuyerIdAndSelectedTrue(f.buyer).isEmpty());
        pay(f);
        assertEquals(4, products.findById(f.product).orElseThrow().getQuantity());
        moneyEquals("900.00", cards.findById(f.buyerCard).orElseThrow().getBalance());
        assertEquals(3, transactions.findAllByOrderId(f.order).size());
        moneyEquals("90.00", wallets.findByUserId(f.seller).orElseThrow().getPendingBalance());
        moneyEquals(
                platformPendingBefore.add(money("10.00")).toPlainString(),
                wallets.findByType(WalletType.PLATFORM).orElseThrow().getPendingBalance()
        );
        deliver(f);
        workflow.confirmDelivery(f.buyer, f.order, f.part);
        assertEquals(OrderStatus.COMPLETED, orders.findById(f.order).orElseThrow().getStatus());
        assertNotNull(orders.findById(f.order).orElseThrow().getDeliveredAt());
        moneyEquals("0.00", wallets.findByUserId(f.seller).orElseThrow().getPendingBalance());
        moneyEquals("90.00", wallets.findByUserId(f.seller).orElseThrow().getAvailableBalance());
        moneyEquals(platformPendingBefore.toPlainString(),
                wallets.findByType(WalletType.PLATFORM).orElseThrow().getPendingBalance());
        moneyEquals(platformAvailableBefore.add(money("10.00")).toPlainString(),
                wallets.findByType(WalletType.PLATFORM).orElseThrow().getAvailableBalance());
        assertEquals(5, transactions.findAllByOrderId(f.order).size());
        assertEquals(f.order, pay(f));
        moneyEquals("900.00", cards.findById(f.buyerCard).orElseThrow().getBalance());
    }

    @Test
    void concurrentDuplicatePaymentsChargeOnce() throws Exception {
        var f = orderFixture();
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Long> first = executor.submit(() -> { ready.countDown(); go.await(); return pay(f); });
            Future<Long> second = executor.submit(() -> { ready.countDown(); go.await(); return pay(f); });
            assertTrue(ready.await(5, TimeUnit.SECONDS)); go.countDown();
            assertEquals(f.order, first.get(15, TimeUnit.SECONDS));
            assertEquals(f.order, second.get(15, TimeUnit.SECONDS));
        }
        moneyEquals("900.00", cards.findById(f.buyerCard).orElseThrow().getBalance());
        assertEquals(3, transactions.findAllByOrderId(f.order).size());
        assertEquals(4, products.findById(f.product).orElseThrow().getQuantity());
    }

    @Test
    void twoSellersFulfillIndependentlyAndOnlyAllDeliveredCompletesOrder() throws Exception {
        var f = orderFixture();
        long seller2 = createUser((short) 1, (short) 2);
        var extra = product(seller2, "Second item", "20.00");
        BigDecimal platformPendingBefore = wallets.findByType(WalletType.PLATFORM)
                .orElseThrow().getPendingBalance();
        BigDecimal platformAvailableBefore = wallets.findByType(WalletType.PLATFORM)
                .orElseThrow().getAvailableBalance();
        orderService.cancelOrder(f.order, f.buyer);
        cart.saveAndFlush(new CartItemEntity(f.buyer, f.product));
        cart.saveAndFlush(new CartItemEntity(f.buyer, extra.getId()));
        long orderId = orderService.createOrder(f.buyer);
        payments.payOrder(orderId, f.buyer, new PayOrderRequest(f.buyerCard, "multi-" + orderId));
        var split = parts.findAllByOrderIdOrderBySellerId(orderId);
        assertEquals(2, split.size());
        var first = split.stream().filter(p -> p.getSellerId().equals(f.seller)).findFirst().orElseThrow();
        var second = split.stream().filter(p -> p.getSellerId().equals(seller2)).findFirst().orElseThrow();
        moneyEquals("880.00", cards.findById(f.buyerCard).orElseThrow().getBalance());
        assertEquals(4, products.findById(f.product).orElseThrow().getQuantity());
        assertEquals(4, products.findById(extra.getId()).orElseThrow().getQuantity());
        moneyEquals("90.00", wallets.findByUserId(f.seller).orElseThrow().getPendingBalance());
        moneyEquals("18.00", wallets.findByUserId(seller2).orElseThrow().getPendingBalance());
        moneyEquals(platformPendingBefore.add(money("12.00")).toPlainString(),
                wallets.findByType(WalletType.PLATFORM).orElseThrow().getPendingBalance());
        assertEquals(OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS.PENDINGWALLET, first.getSettlementStatus());
        assertEquals(OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS.PENDINGWALLET, second.getSettlementStatus());
        assertEquals(5, transactions.findAllByOrderId(orderId).size());
        assertThrows(MarketplaceException.class, () -> workflow.sellerTransition(
                seller2, first.getId(), OBSERFFORSENDBYSELLERPRODUCTSTATUS.PROCESSING));
        assertThrows(MarketplaceException.class, () -> workflow.sellerDetails(seller2, first.getId()));
        assertEquals(1, workflow.sellerDetails(f.seller, first.getId()).items().size());
        ship(f.seller, first.getId());
        workflow.confirmDelivery(f.buyer, orderId, first.getId());
        assertEquals(OrderStatus.SELLERSSTARTWORK, orders.findById(orderId).orElseThrow().getStatus());
        assertEquals(OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS.MAINWALLET,
                parts.findById(first.getId()).orElseThrow().getSettlementStatus());
        moneyEquals("0.00", wallets.findByUserId(f.seller).orElseThrow().getPendingBalance());
        moneyEquals("90.00", wallets.findByUserId(f.seller).orElseThrow().getAvailableBalance());
        ship(seller2, second.getId());
        assertEquals(OrderStatus.SELLERSENDWORKANDSEND, orders.findById(orderId).orElseThrow().getStatus());
        workflow.confirmDelivery(f.buyer, orderId, second.getId());
        assertEquals(OrderStatus.COMPLETED, orders.findById(orderId).orElseThrow().getStatus());
        assertEquals(OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS.MAINWALLET,
                parts.findById(second.getId()).orElseThrow().getSettlementStatus());
        moneyEquals("0.00", wallets.findByUserId(seller2).orElseThrow().getPendingBalance());
        moneyEquals("18.00", wallets.findByUserId(seller2).orElseThrow().getAvailableBalance());
        moneyEquals(platformPendingBefore.toPlainString(),
                wallets.findByType(WalletType.PLATFORM).orElseThrow().getPendingBalance());
        moneyEquals(platformAvailableBefore.add(money("12.00")).toPlainString(),
                wallets.findByType(WalletType.PLATFORM).orElseThrow().getAvailableBalance());
        assertEquals(9, transactions.findAllByOrderId(orderId).size());

        String withdrawalJson = """
                {
                  "cardId": %d,
                  "amount": 40.00,
                  "idempotencyKey": "withdraw-rest-%d"
                }
                """.formatted(f.sellerCard, orderId);
        mvc.perform(post("/api/v1/wallet/withdraw")
                        .with(user(principal(f.buyer))).with(csrf())
                        .contentType("application/json").content(withdrawalJson))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/wallet/withdraw")
                        .with(user(principal(f.seller))).with(csrf())
                        .contentType("application/json").content(withdrawalJson))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/wallet/withdraw")
                        .with(user(principal(f.seller))).with(csrf())
                        .contentType("application/json").content(withdrawalJson))
                .andExpect(status().isNoContent());
        moneyEquals("50.00", wallets.findByUserId(f.seller).orElseThrow().getAvailableBalance());
        moneyEquals("40.00", cards.findById(f.sellerCard).orElseThrow().getBalance());
        assertEquals(TransactionType.SELLER_TRANSFERMONEYFROMMAINWALLET,
                transactions.findByIdempotencyKey("withdraw-rest-" + orderId).orElseThrow().getType());
    }

    @Test
    void unpaidCancellationIsIdempotentAndNeverTouchesStockOrCard() {
        var f = orderFixture();
        assertThrows(com.example.marketflow.exception.InvalidOrderStateException.class,
                () -> workflow.sellerTransition(
                        f.seller, f.part, OBSERFFORSENDBYSELLERPRODUCTSTATUS.PROCESSING));
        orderService.cancelOrder(f.order, f.buyer);
        orderService.cancelOrder(f.order, f.buyer);
        assertThrows(com.example.marketflow.exception.InvalidOrderStateException.class, () -> pay(f));
        assertEquals(5, products.findById(f.product).orElseThrow().getQuantity());
        assertEquals(OBSERFFORSENDBYSELLERPRODUCTSTATUS.CANCELLED,
                parts.findById(f.part).orElseThrow().getStatus());
        moneyEquals("1000.00", cards.findById(f.buyerCard).orElseThrow().getBalance());
        assertTrue(transactions.findAllByOrderId(f.order).isEmpty());
    }

    @Test
    void paymentUsesSnapshotPriceAndPaidCancellationRefundsBeforeFulfillment() {
        var f = orderFixture();
        BigDecimal platformPendingBefore = wallets.findByType(WalletType.PLATFORM)
                .orElseThrow().getPendingBalance();
        jdbc.update("UPDATE products SET price=999 WHERE id=?", f.product);
        assertThrows(com.example.marketflow.exception.OrderNotFoundException.class,
                () -> payments.payOrder(f.order, f.seller, new PayOrderRequest(f.sellerCard, "wrong")));
        assertThrows(com.example.marketflow.exception.PaymentCardNotFoundException.class,
                () -> payments.payOrder(f.order, f.buyer, new PayOrderRequest(f.sellerCard, "wrong-card")));
        pay(f);
        moneyEquals("900.00", cards.findById(f.buyerCard).orElseThrow().getBalance());
        orderService.cancelOrder(f.order, f.buyer);
        moneyEquals("1000.00", cards.findById(f.buyerCard).orElseThrow().getBalance());
        moneyEquals("0.00", wallets.findByUserId(f.seller).orElseThrow().getPendingBalance());
        moneyEquals(platformPendingBefore.toPlainString(),
                wallets.findByType(WalletType.PLATFORM).orElseThrow().getPendingBalance());
        assertEquals(5, products.findById(f.product).orElseThrow().getQuantity());
        assertEquals(PaymentStatus.REFUNDED, orders.findById(f.order).orElseThrow().getPaymentStatus());
        assertEquals(OrderStatus.CANCELLED, orders.findById(f.order).orElseThrow().getStatus());
        assertEquals(OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS.RETURNMONEY,
                parts.findById(f.part).orElseThrow().getSettlementStatus());
        assertThrows(com.example.marketflow.exception.InvalidOrderStateException.class,
                () -> workflow.sellerTransition(
                        f.seller, f.part, OBSERFFORSENDBYSELLERPRODUCTSTATUS.SELLERSENDPRODUCT));
    }

    @Test
    void insufficientBalancePersistsFailedAttemptAndNewKeyCanRetry() {
        var f = orderFixture();
        jdbc.update("UPDATE payment_cards SET balance=0 WHERE id=?", f.buyerCard);
        assertThrows(com.example.marketflow.exception.InsufficientFundsException.class, () -> pay(f));
        assertEquals(PaymentStatus.FAILED, orders.findById(f.order).orElseThrow().getPaymentStatus());
        assertEquals(TransactionStatus.FAILED, transactions.findAllByOrderId(f.order).getFirst().getStatus());
        assertEquals(5, products.findById(f.product).orElseThrow().getQuantity());
        jdbc.update("UPDATE payment_cards SET balance=1000 WHERE id=?", f.buyerCard);
        assertThrows(com.example.marketflow.exception.InsufficientFundsException.class, () -> pay(f));
        assertEquals(f.order, payments.payOrder(f.order, f.buyer, new PayOrderRequest(f.buyerCard, "retry-" + f.order)));
        assertEquals(4, transactions.findAllByOrderId(f.order).size());
        moneyEquals("900.00", cards.findById(f.buyerCard).orElseThrow().getBalance());
    }

    @Test
    void failedCreationPreservesCartAndDoesNotCreatePartialOrder() {
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
    void stockShortageAtPaymentRollsBackEarlierItemDebitAndAllPaymentChanges() {
        var f = orderFixture();
        orderService.cancelOrder(f.order, f.buyer);
        var extra = product(f.seller, "Second", "20.00");
        cart.saveAndFlush(new CartItemEntity(f.buyer, f.product));
        cart.saveAndFlush(new CartItemEntity(f.buyer, extra.getId()));
        long orderId = orderService.createOrder(f.buyer);
        jdbc.update("UPDATE products SET quantity=0 WHERE id=?", extra.getId());
        assertThrows(com.example.marketflow.exception.InsufficientStockException.class,
                () -> payments.payOrder(orderId, f.buyer, new PayOrderRequest(f.buyerCard, "stock-" + orderId)));
        assertEquals(5, products.findById(f.product).orElseThrow().getQuantity());
        moneyEquals("1000.00", cards.findById(f.buyerCard).orElseThrow().getBalance());
        assertEquals(PaymentStatus.NOT_PAID, orders.findById(orderId).orElseThrow().getPaymentStatus());
        assertEquals(OrderStatus.CREATED, orders.findById(orderId).orElseThrow().getStatus());
        assertTrue(transactions.findAllByOrderId(orderId).isEmpty());
    }

    @Test
    void concurrentOrdersCannotBuyTheSameLastItem() throws Exception {
        var f = orderFixture();
        cart.saveAndFlush(new CartItemEntity(f.buyer, f.product));
        long other = orderService.createOrder(f.buyer);
        jdbc.update("UPDATE products SET quantity=1 WHERE id=?", f.product);
        var go = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = pool.submit(() -> { go.await(); return attemptPay(f.order, f); });
            var b = pool.submit(() -> { go.await(); return attemptPay(other, f); });
            go.countDown();
            assertEquals(1, a.get(15, TimeUnit.SECONDS) + b.get(15, TimeUnit.SECONDS));
        }
        assertEquals(0, products.findById(f.product).orElseThrow().getQuantity());
        moneyEquals("900.00", cards.findById(f.buyerCard).orElseThrow().getBalance());
    }
    private int attemptPay(long order, Fixture f) {
        try { payments.payOrder(order, f.buyer, new PayOrderRequest(f.buyerCard, "race-" + order)); return 1; }
        catch (com.example.marketflow.exception.InsufficientStockException expected) { return 0; }
    }

    @Test
    void sellerRegistrationGrantsAccessWithoutApplication() {
        var request = new RegisterRequest();
        request.setAccountType(AccountType.SELLER); request.setEmail(UUID.randomUUID() + "@test.example");
        request.setPassword("password123"); request.setDisplay_name("Seller");
        auth.register(request);
        long seller = users.findByEmailIgnoreCase(request.getEmail()).orElseThrow().getId();
        assertTrue(roles.existsById(new UserRoleId(seller, (short) 1)));
        assertTrue(roles.existsById(new UserRoleId(seller, (short) 2)));
    }

    @Test
    void htmlPagesRenderAndRestLifecycleEnforcesRolesOwnershipAndCsrf() throws Exception {
        var f = orderFixture();
        var buyer = principal(f.buyer); var seller = principal(f.seller);
        mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/seller/orders").with(user(buyer))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/orders/{id}/payment", f.order).with(user(buyer))
                .contentType("application/json").content("{\"cardId\":" + f.buyerCard + ",\"idempotencyKey\":\"http-key\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/orders/{id}/payment", f.order).with(user(buyer)).with(csrf())
                .contentType("application/json").content("{\"cardId\":" + f.buyerCard + ",\"idempotencyKey\":\"http-key-" + f.order + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/seller/orders/{id}/process", f.part).with(user(seller)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/seller/orders/{id}/ship", f.part).with(user(seller)).with(csrf()))
                .andExpect(status().isNoContent());
        for (var path : new String[]{"/account/orders", "/account/orders/" + f.order,
                "/account/orders/" + f.order + "/workflow", "/account/orders/" + f.order + "/payment"}) {
            mvc.perform(get(path).with(user(buyer))).andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("text/html"));
        }
        for (var path : new String[]{"/workspace/seller/orders", "/workspace/seller/orders/" + f.part}) {
            mvc.perform(get(path).with(user(seller))).andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/orders/{id}/fulfillments", f.order).with(user(principal(createUser((short) 1)))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/orders/{id}/fulfillments/{partId}/receive", f.order, f.part).with(user(buyer)).with(csrf()))
                .andExpect(status().isNoContent());
        assertEquals(OrderStatus.COMPLETED, orders.findById(f.order).orElseThrow().getStatus());
        mvc.perform(get("/api/v1/orders").with(user(buyer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(f.order));
        jdbc.update("UPDATE users SET status='BLOCKED' WHERE id=?", f.buyer);
        mvc.perform(get("/api/v1/orders").with(user(buyer))).andExpect(status().isUnauthorized());
    }
    private MarketFlowPrincipal principal(long id) {
        return new MarketFlowPrincipal(users.findById(id).orElseThrow(), roles.findRoleNamesByUserId(id).stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
    }

    private Fixture orderFixture() {
        long buyer = createUser((short) 1), seller = createUser((short) 1, (short) 2);
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
        boolean seller = false;
        for (short role : assignedRoles) {
            roles.saveAndFlush(new UserRolesEntity(user.getId(), role));
            seller = seller || role == 2;
        }
        if (seller) {
            wallets.saveAndFlush(WalletAccountEntity.seller(user.getId()));
        }
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
        workflow.sellerTransition(seller, part, OBSERFFORSENDBYSELLERPRODUCTSTATUS.PROCESSING);
        workflow.sellerTransition(seller, part, OBSERFFORSENDBYSELLERPRODUCTSTATUS.SELLERSENDPRODUCT);
    }
    private static BigDecimal money(String v) { return new BigDecimal(v); }
    private static void moneyEquals(String expected, BigDecimal actual) { assertEquals(0, money(expected).compareTo(actual)); }
    private record Fixture(long buyer, long seller, long product, long order, long part, long buyerCard, long sellerCard) {}

}
