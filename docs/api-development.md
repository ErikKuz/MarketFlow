# API Development

## Current state

MarketFlow exposes both Spring MVC pages and a REST API under `/api/v1`. The REST contracts are split by feature across the YAML files in `openapi/`. MVC and REST controllers call the same application services.

## Contract-first workflow

1. Update the relevant contract in `openapi/` when an endpoint changes.
2. Review paths, schemas, status codes, and error responses against the controller.
3. Keep business rules in application services rather than duplicating them in controllers.
4. Run `./mvnw test` (or `.\mvnw.cmd test` on Windows) before committing.

## URL conventions

- REST endpoints use the `/api/v1` prefix.
- Collection resources use plural nouns, for example `/api/v1/products`.
- Resource identifiers are path parameters, for example `/api/v1/products/{productId}`.
- Filtering and pagination use query parameters.
- Commands that create non-idempotent financial effects require an idempotency key.

## HTTP conventions

| Operation | Expected status |
| --- | --- |
| Successful read | `200 OK` |
| Successful creation | `201 Created` |
| Successful update without response body | `204 No Content` |
| Validation failure | `400 Bad Request` |
| Missing or invalid authentication | `401 Unauthorized` |
| Insufficient permissions | `403 Forbidden` |
| Missing resource | `404 Not Found` |
| State or uniqueness conflict | `409 Conflict` |

## Error contract

All REST errors should use one stable schema containing at least:

```json
{
  "code": "PRODUCT_NOT_FOUND",
  "message": "Product was not found",
  "status": 404,
  "path": "/api/v1/products/42",
  "timestamp": "2026-08-29T12:00:00Z"
}
```

Validation failures may additionally contain field-level errors.

## Implemented areas

The contracts cover authentication, catalogue and seller products, cart, checkout, orders, simulated payments, wallets, and notifications. See the individual files in `openapi/` for current request and response details.
