# Database

## Overview

PostgreSQL is the source of truth for MarketFlow. Spring Data JPA handles persistence, while Flyway owns schema creation and evolution. The application uses `spring.jpa.hibernate.ddl-auto=validate`, so Hibernate checks mappings but does not create or mutate production tables.

## Core tables

| Table | Responsibility |
| --- | --- |
| `users` | Account identity, password hash, display name, status, and timestamps |
| `roles` | Supported platform roles |
| `user_roles` | Many-to-many user and role assignments |
| `products` | Seller-owned catalogue items, prices, stock, images, and availability |
| `cart_items` | Buyer-specific cart quantities and selection state |
| `orders` | Order header, buyer, lifecycle status, amount, and timestamps |
| `order_items` | Immutable product snapshots associated with an order |
| `seller_orders` | One independently fulfilled part per seller and order |
| `payment_cards` | Simulated card tokens, masked numbers, balances, and activity state |
| `wallet_accounts` | Pending and available virtual balances for sellers and the platform |
| `payment_transactions` | Idempotent payment, accrual, commission, release, withdrawal and refund entries |
| `flyway_schema_history` | Flyway migration history |

## Data-integrity rules

- User email uniqueness is enforced case-insensitively.
- A buyer can have at most one cart row for a specific product.
- Product price must be positive and stock cannot be negative.
- Cart and order-item quantities must be positive.
- Order totals and order-item totals must be positive.
- Foreign keys protect ownership and order relationships.
- A seller has at most one `seller_orders` row per order.
- Payment idempotency keys are unique.
- Conditional stock updates prevent negative inventory.
- Order-item snapshot fields preserve historical product information.

## V12 archive

Migration V12 moves inactive pre-MVP tables to `<main_schema>_pre_mvp` and copies removed order metadata and financial ledger rows there. It does not discard the previous data. The active schema then contains only the MVP tables.

## Monetary values

Monetary columns use PostgreSQL `NUMERIC(12, 2)` or `NUMERIC(14, 2)` and map to Java `BigDecimal`. Floating-point types such as `double` are not used for prices or account balances.

## Migration workflow

1. Create a new file under `src/main/resources/db/migration`.
2. Use the next version number, for example `V7__add_payment_status.sql`.
3. Make the migration forward-only and safe for existing rows.
4. Run the application or tests against a clean database.
5. Never edit a migration that has already been applied to a shared environment.

## Local configuration

Copy `.env.example` to `.env` and provide local credentials:

```env
DB_URL=jdbc:postgresql://localhost:5432/marketflow
DB_USERNAME=postgres
DB_PASSWORD=change_me
```

The real `.env` file is ignored by Git.

## Diagram

The source ERD is stored in [diagrams/database-erd.puml](diagrams/database-erd.puml) and can be rendered with PlantUML.
