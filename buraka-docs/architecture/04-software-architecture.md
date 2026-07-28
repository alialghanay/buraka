# 4. Software Architecture

## 4.1 Chosen Architecture: Microservices

Buraka is designed as a **microservices** system: four independently deployable backend services, each owning its data, fronted by an API Gateway, integrated through synchronous REST and asynchronous RabbitMQ events.

## 4.2 Why Microservices (and not the alternatives)

The course requires an explicit justification. Three styles were considered:

| Style | Fit for Buraka | Verdict |
| --- | --- | --- |
| Layered monolith | Simplest to build and operate; but one deployable unit couples catalog read traffic to order write traffic, and a failure in any module takes down the whole store | Rejected: cannot independently scale the catalog (read-heavy) from orders (write-heavy), which NFR-SCA-01's 10× headroom demands |
| Modular monolith | Strong module boundaries in one deployable; better than layered but still a single failure domain and single scaling unit | Rejected: same scaling/failure-isolation limits; kept as the fallback if the team were smaller |
| **Microservices** | Independent scaling, independent deployment (NFR-MNT-01), fault isolation (NFR-AVL-01), and per-service data ownership | **Chosen** |

The known costs are accepted deliberately: distributed data requires eventual consistency (no cross-service transactions), operational complexity rises (4 services + broker + cache), and some queries need service collaboration. At the stated scale (~1K DAU) these costs are manageable, and the course goal of demonstrating decomposition, communication design, and quality-attribute engineering is best served by this style.

## 4.3 System Decomposition

Services are split **by business capability**, one per bounded context of the ERD:

| Service | Business capability | Data owned | Database |
| --- | --- | --- | --- |
| auth-user-service | Identity: registration, login, token issuance, password reset, profiles | USER | buraka_auth_db |
| catalog-service | Catalog: products, category tree, brands, search/listing, stock counters, rating aggregates | PRODUCT, CATEGORY, BRAND | buraka_catalog_db |
| order-service | Commerce: cart, checkout, orders, payment coordination with Moamalat | CART, CART_ITEM, ORDER, ORDER_ITEM | buraka_order_db |
| review-service | Feedback: reviews and ratings | REVIEW | buraka_review_db |

Two supporting components are not business services: the **api-gateway** (edge routing and security) and the infrastructure (**rabbitmq**, **redis**, four PostgreSQL instances).

## 4.4 Communication Between Components

**Synchronous (REST over HTTPS):** used for queries and for commands that need an immediate answer.

- Clients → api-gateway → services (route prefixes: `/api/auth/**`, `/api/users/**`, `/api/products/**`, `/api/categories/**`, `/api/brands/**`, `/api/cart/**`, `/api/orders/**`, `/api/payments/**`, `/api/reviews/**`).
- order-service → catalog-service: `GET /api/products/{ids}` at checkout to snapshot price and availability (needs an immediate, consistent answer — cannot be async).

**Asynchronous (RabbitMQ events):** used for cross-service state propagation where eventual consistency is acceptable.

| Event | Published by | Consumed by | Effect |
| --- | --- | --- | --- |
| OrderPlaced | order-service | catalog-service | Reserve stock for ordered items |
| PaymentConfirmed | order-service | catalog-service, order-service notification module | Confirm stock decrement (sold += qty); send order confirmation email |
| PaymentFailed | order-service | catalog-service | Release reserved stock |
| OrderCancelled | order-service | catalog-service | Release reserved stock |
| ReviewCreated / ReviewDeleted | review-service | catalog-service | Recalculate ratingsAverage / ratingsQuantity |
| UserRegistered / PasswordResetRequested | auth-user-service | auth-user-service notification module | Send welcome / reset-code email |

Rules that keep this sane: only the owning service writes its database; events carry IDs and minimal payloads, never entities; every consumer is idempotent (NFR-REL-01).

## 4.5 Data Ownership and Consistency

**Database per service** (PostgreSQL ×4): no service can reach another service's tables. Cross-service consistency is eventual, achieved through the events above. The checkout flow is the critical consistency path and is designed as a reservation saga: reserve stock on OrderPlaced → confirm on PaymentConfirmed → release on PaymentFailed/OrderCancelled. order-service publishes events transactionally with its state changes using the outbox pattern (documented, not implemented).

## 4.6 Cross-Cutting Decisions

- **Security:** JWT (RS256) issued by auth-user-service; the api-gateway validates tokens on every request; services trust the gateway-verified identity headers. Passwords BCrypt-hashed. Moamalat callbacks verified by HMAC-SHA256 (NFR-SEC-02).
- **Caching:** redis caches hot catalog reads (product lists, product details) and stores refresh tokens. Cache-aside with TTL; rating/stock updates invalidate affected keys after consuming events.
- **Notifications:** no separate service — auth-user-service and order-service each host a small notification module that consumes their events and sends email via SMTP.
- **Payment:** Moamalat hosted payment page; order-service generates the HMAC-SHA256 secure hash, redirects the customer, and processes the signed callback. Design only — marked not implemented.
