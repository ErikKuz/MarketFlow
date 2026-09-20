# MarketFlow Architecture

## Overview

MarketFlow is currently a modular monolith implemented as a Spring Boot application with MVC + Thymeleaf and REST boundaries. Buyer and seller workflows share one deployable application and one PostgreSQL database while remaining separated at the controller and service levels.

The synchronous business transaction and asynchronous event flow are:

```text
HTTP -> Spring Security -> Controller -> @Transactional Service -> PostgreSQL
                                                       -> outbox_events
outbox_events -> OutboxPublisher -> RabbitMQ events exchange
                                  -> history queue -> order_event_history
                                  -> buyer queue   -> notifications
                                  -> seller queue  -> notifications
                               -> RabbitMQ commands exchange
                                  -> withdrawal queue -> WalletService
                                  -> settlement queue -> OrderWorkflowService
failed consumer message -> dead-letter exchange -> dead-letter queue
```

The architecture is intentionally kept as a monolith while the core marketplace rules are still evolving. Splitting the application into services before the domain boundaries and operational requirements are stable would add deployment and consistency complexity without a demonstrated benefit.

## Application layers

### Web layer

Controllers accept MVC requests, validate form DTOs, invoke application services, and return Thymeleaf views or redirects. Buyer-facing controllers live in the common `controller` package. Seller-specific endpoints are kept in the seller area.

### Application layer

Services implement use cases such as registration, catalogue browsing, cart management, checkout preparation, order creation, and seller product management. Transaction boundaries belong to this layer.

### Persistence layer

Spring Data JPA repositories persist users, roles, products, carts, orders, order-item snapshots, seller fulfillment parts, simulated cards, and payment attempts. Flyway is the only supported mechanism for changing the database schema; Hibernate validates the schema at startup.

### Presentation layer

Thymeleaf templates render the current HTML interface. REST controllers and MVC controllers call the same transactional services, so business rules are not duplicated.

### Messaging layer

Services never publish directly to RabbitMQ. They save `MarketFlowEvent` facts or `MarketFlowCommand` requests to `outbox_events` inside the same PostgreSQL transaction as the business change. `OutboxPublisher` routes events to `marketflow.events.exchange` and commands to `marketflow.commands.exchange`, waits for a correlated publisher confirm, marks acknowledged rows `PUBLISHED`, and retries failures with a delay. Event consumers are idempotent by `eventId`; money consumers additionally lock the target transaction or seller-order part and treat an already completed state as a safe duplicate. Retry exhaustion in a listener routes the rejected message to the shared DLQ.

### Cache layer

Spring Cache stores the available product list and individual catalogue products in Redis for 60 seconds. Product changes, successful payments and refunds evict both caches. Cached data is never used as the source of truth for stock or money; all critical checks still read and update PostgreSQL.

## Important domain rules

- Seller operations must verify both seller authority and product ownership.
- Money is represented with `BigDecimal` in Java and fixed-precision `NUMERIC` columns in PostgreSQL.
- Order items store product name, price, seller, quantity, and image snapshots so historical orders do not change when a product is edited.
- Cart and product quantities must be positive and database constraints provide a second validation boundary.
- Schema changes are append-only Flyway migrations; applied migration files are never edited.

## Cross-cutting concerns

- Jakarta Validation protects request boundaries.
- Centralized exception handlers translate known failures into MVC error responses.
- Spring Boot Actuator exposes the application health endpoint.
- Secrets are supplied through environment variables and are never committed.

## Planned evolution

1. Keep the synchronous multi-seller order path stable.
2. Keep RabbitMQ event contracts backward compatible as new consumers appear.
3. Measure catalogue cache hit rate before expanding Redis usage.
4. Add CI and container packaging.

## Diagrams

- [Application architecture](diagrams/architecture.puml)
- [Database ERD](diagrams/database-erd.puml)
