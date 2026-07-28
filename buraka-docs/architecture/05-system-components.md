# 5. System Components

Each component below is described by its responsibility, the data it owns, its REST interface (representative endpoints), the events it publishes and consumes, and its dependencies. All endpoints are reached through the api-gateway; internal calls are noted explicitly.

## 5.1 API Gateway (`api-gateway`)

- **Responsibility:** single entry point for the Flutter and React clients. Route to services by path prefix, validate JWTs (RS256 public key from auth-user-service), enforce rate limiting and CORS, terminate nothing else — no business logic.
- **Routes:**

| Prefix | Target |
| --- | --- |
| /api/auth/**, /api/users/** | auth-user-service |
| /api/products/**, /api/categories/**, /api/brands/** | catalog-service |
| /api/cart/**, /api/orders/**, /api/payments/** | order-service |
| /api/reviews/** | review-service |

- **Depends on:** all four services (routing), auth-user-service (JWT public key).

## 5.2 Auth/User Service (`auth-user-service`)

- **Responsibility:** registration, login, JWT access/refresh issuance, refresh rotation, password reset flow, profile management. Sends its own transactional email through an internal notification module.
- **Owns:** USER (buraka_auth_db). Refresh tokens stored in redis.
- **REST interface:**

| Endpoint | Description |
| --- | --- |
| POST /api/auth/register | Register (FR-AUTH-01) |
| POST /api/auth/login | Login, returns access + refresh tokens (FR-AUTH-02) |
| POST /api/auth/refresh | New access token from refresh token (FR-AUTH-03) |
| POST /api/auth/forgot-password | Issue reset code, send email (FR-AUTH-04) |
| POST /api/auth/verify-reset-code | Verify code (FR-AUTH-05) |
| POST /api/auth/reset-password | Set new password (FR-AUTH-05) |
| GET /api/users/me | Current profile (FR-AUTH-06) |
| PUT /api/users/me | Update profile (FR-AUTH-06) |

- **Publishes:** UserRegistered, PasswordResetRequested. **Consumes:** its own two events (notification module → SMTP email).
- **Depends on:** buraka_auth_db, redis (refresh tokens), rabbitmq, SMTP provider.

## 5.3 Catalog Service (`catalog-service`)

- **Responsibility:** product catalog read/write, category tree, brands, search/filter/pagination, stock counters (quantity, sold), rating aggregates (ratingsAverage, ratingsQuantity). Read-heavy: hottest cache user in the system.
- **Owns:** PRODUCT, CATEGORY, BRAND (buraka_catalog_db).
- **REST interface:**

| Endpoint | Description |
| --- | --- |
| GET /api/products | List with pagination, category/brand filters, title search (FR-CAT-01) |
| GET /api/products/{slug} | Product detail (FR-CAT-02) |
| GET /api/categories | Category tree (FR-CAT-03) |
| GET /api/brands | Brand list |
| POST/PUT/DELETE /api/products[/{id}] | Admin product management (FR-CAT-04) |
| POST/PUT/DELETE /api/categories[/{id}] | Admin category management (FR-CAT-05) |
| POST/PUT/DELETE /api/brands[/{id}] | Admin brand management (FR-CAT-06) |
| GET /api/products/{ids} (internal) | Batch price/availability snapshot for order-service checkout |

- **Publishes:** none. **Consumes:** OrderPlaced (reserve stock), PaymentConfirmed (confirm decrement, sold += qty), PaymentFailed / OrderCancelled (release reservation), ReviewCreated / ReviewDeleted (recalculate rating aggregates). All consumers idempotent; cache invalidation on stock/rating changes.
- **Depends on:** buraka_catalog_db, redis (cache-aside), rabbitmq.

## 5.4 Order Service (`order-service`)

- **Responsibility:** cart management, checkout, order lifecycle (isPaid/paidAt/isDelivered/deliveredAt), payment coordination with Moamalat, cash-on-delivery. Sends order confirmation email through an internal notification module.
- **Owns:** CART, CART_ITEM, ORDER, ORDER_ITEM (buraka_order_db).
- **REST interface:**

| Endpoint | Description |
| --- | --- |
| GET /api/cart | Current cart with totalCartPrice (FR-ORD-03) |
| POST /api/cart/items | Add item with quantity and selectedColor (FR-ORD-01) |
| PUT /api/cart/items/{id} | Update quantity (FR-ORD-02) |
| DELETE /api/cart/items/{id} | Remove item (FR-ORD-02) |
| POST /api/orders | Checkout: create order from cart, returns paymentUrl for Moamalat (FR-ORD-04/05/06) |
| GET /api/orders | Order history (FR-ORD-07) |
| GET /api/orders/{id} | Order detail (FR-ORD-07) |
| POST /api/payments/moamalat/callback | Moamalat server callback (HMAC-SHA256 verified) — public, unauthenticated, signature-checked |

- **Publishes:** OrderPlaced, PaymentConfirmed, PaymentFailed, OrderCancelled. **Consumes:** PaymentConfirmed (notification module → order confirmation email).
- **Depends on:** buraka_order_db, rabbitmq, catalog-service (checkout snapshot), Moamalat, SMTP provider.

## 5.5 Review Service (`review-service`)

- **Responsibility:** create/read/update/delete reviews; enforce "purchased products only" (FR-REV-01) by checking order history via order-service.
- **Owns:** REVIEW (buraka_review_db).
- **REST interface:**

| Endpoint | Description |
| --- | --- |
| POST /api/reviews | Create review (title, rating 1–5) (FR-REV-01) |
| GET /api/reviews?productId={id} | List product reviews (FR-REV-02) |
| PUT /api/reviews/{id} | Update own review (FR-REV-03) |
| DELETE /api/reviews/{id} | Delete own review (FR-REV-03) |

- **Publishes:** ReviewCreated, ReviewDeleted. **Consumes:** none.
- **Depends on:** buraka_review_db, rabbitmq, order-service (purchase verification: `GET /api/orders?productId={id}&paid=true`, internal).

## 5.6 Infrastructure Components

| Component | Role | Notes |
| --- | --- | --- |
| rabbitmq | Event bus for all events in §5.2–5.5 | Quorum queues, durable messages, publisher confirms (NFR-REL-01) |
| redis | Catalog cache (cache-aside) + refresh-token store | Cache loss is tolerable (rebuild from PostgreSQL) |
| PostgreSQL ×4 | One database per service | buraka_auth_db, buraka_catalog_db, buraka_order_db, buraka_review_db |
| Moamalat | External hosted payment page + callbacks | Design only, not implemented |
| SMTP provider | Transactional email | Password reset, order confirmation |
