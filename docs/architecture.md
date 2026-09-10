# MarketFlow Architecture

## Overview

MarketFlow is currently a modular monolith implemented as a Spring Boot application with MVC + Thymeleaf and REST boundaries. Buyer and seller workflows share one deployable application and one PostgreSQL database while remaining separated at the controller and service levels.

The current structure favours a straightforward request flow:

```text
HTTP request -> Spring Security -> MVC/REST Controller -> Service -> Repository -> PostgreSQL
                                                        -> JSON or Thymeleaf
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
2. Publish committed order and delivery events through RabbitMQ.
3. Add Redis only when catalogue caching has measurable benefit.
4. Add CI and container packaging.

## Diagrams

- [Application architecture](diagrams/architecture.puml)
- [Database ERD](diagrams/database-erd.puml)
