# MarketFlow

[Русская версия](README.ru.md)

MarketFlow is a learning multi-vendor marketplace built with Java and Spring Boot. The current version intentionally focuses on one complete flow:

```text
catalogue → cart → order → simulated payment → seller fulfillment → receipt
```

An order may contain products from multiple sellers. Each seller gets a separate fulfillment part and can only process their own products. The overall order becomes `COMPLETED` after the buyer receives every part.

## Implemented scope

Buyers can register, sign in with a server-side HTTP session, browse products, use the cart, attach a simulated card, create and pay for an order, list their orders, and confirm receipt of each shipment.

Sellers register directly, manage only their own products and stock, see only paid parts containing their products, and move each part through:

```text
NEW → PROCESSING → SELLERSENDPRODUCT → USERGETPRODUCT
```

The overall order follows:

```text
CREATED → CONFIRMED → SELLERSSTARTWORK → SELLERSENDWORKANDSEND → COMPLETED
    └───────────────→ CANCELLED
```

Payment is a learning simulation, not a banking integration. One database transaction verifies card ownership and balance, conditionally debits stock, updates the simulated card balance, accrues seller proceeds and platform commission to pending virtual balances, and records each money movement. Receipt releases pending funds to available balances. Pessimistic locks, atomic SQL and an idempotency key prevent double charges and overselling.

Both MVC + Thymeleaf pages and REST endpoints are implemented. REST behavior is documented by OpenAPI contracts. Spring Security uses `JSESSIONID`, a server-side session, `BUYER` / `SELLER` roles and CSRF protection.

## Deliberately outside this MVP

Seller applications, moderation, analytics, returns after receipt and automatic payment deadlines are not active features. Migration V12 archives their old data in the PostgreSQL schema `<main_schema>_pre_mvp` instead of deleting it. Virtual seller/platform wallets, commission, direct simulated-card withdrawal and refund before fulfillment are active.

## Stack

Java 21, Spring Boot 4, Spring MVC, Thymeleaf, Spring Security, Spring Data JPA, Hibernate, PostgreSQL, Flyway, OpenAPI, Maven, JUnit 5, Mockito and MockMvc.

## Main tables

| Table | Purpose |
| --- | --- |
| `users`, `roles`, `user_roles` | Accounts and roles |
| `products` | Seller products, prices and stock |
| `cart_items` | Buyer carts |
| `orders` | Overall order and state |
| `order_items` | Product and price snapshots |
| `seller_orders` | Independent seller fulfillment parts |
| `payment_cards` | Simulated cards and balances |
| `wallet_accounts` | Pending and available virtual seller/platform balances |
| `payment_transactions` | Payments, accruals, commission, releases, withdrawals and refunds |
| `flyway_schema_history` | Applied migrations |

## Run locally

Create PostgreSQL, copy `.env.example` to `.env`, configure `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`, then run:

```powershell
.\mvnw.cmd spring-boot:run
```

Run the standard test suite with `.\mvnw.cmd test`. Real PostgreSQL tests are enabled only when `MARKETFLOW_TEST_POSTGRES_URL` is set and create isolated schemas.

REST endpoints are under `/api/v1`; contracts are in [openapi](openapi).

Seller withdrawal is available at `POST /api/v1/wallet/withdraw`.

## Next step

The next infrastructure step is RabbitMQ for asynchronous domain events and notifications. Redis may follow later for catalogue caching when it provides measurable value.

