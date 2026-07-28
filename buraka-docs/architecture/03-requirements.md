# 3. Requirements

## 3.1 Functional Requirements

### Authentication & User Management

| ID | Requirement | Priority |
| --- | --- | --- |
| FR-AUTH-01 | A visitor can register with username, email, and password | Must |
| FR-AUTH-02 | A registered user can log in and receives a JWT access token (15 min TTL) and refresh token (30 days TTL) | Must |
| FR-AUTH-03 | A user can obtain a new access token using a valid refresh token | Must |
| FR-AUTH-04 | A user can request a password reset; a short-lived code is sent by email | Must |
| FR-AUTH-05 | A user can verify the reset code and set a new password | Must |
| FR-AUTH-06 | A user can view and update their profile (firstName, lastName, profileImg) | Should |

### Catalog

| ID | Requirement | Priority |
| --- | --- | --- |
| FR-CAT-01 | Any visitor can browse products with pagination, filter by category and brand, and search by title | Must |
| FR-CAT-02 | Any visitor can view product details: title, description, images, colors, price, stock quantity, ratingsAverage, ratingsQuantity | Must |
| FR-CAT-03 | Any visitor can browse the category tree (parent/child) | Must |
| FR-CAT-04 | An admin can create, update, and delete products | Must |
| FR-CAT-05 | An admin can create, update, and delete categories (including parent assignment) | Must |
| FR-CAT-06 | An admin can create, update, and delete brands | Must |

### Cart & Orders

| ID | Requirement | Priority |
| --- | --- | --- |
| FR-ORD-01 | A customer can add a product to their cart with a quantity and selected color | Must |
| FR-ORD-02 | A customer can update item quantities and remove items from their cart | Must |
| FR-ORD-03 | A customer can view their cart with the computed totalCartPrice | Must |
| FR-ORD-04 | A customer can check out, creating an order from the current cart contents | Must |
| FR-ORD-05 | A customer can pay an order online via Moamalat; the system records isPaid and paidAt from the gateway callback | Must |
| FR-ORD-06 | A customer can choose cash on delivery (paymentMethodType = COD) | Should |
| FR-ORD-07 | A customer can view their order history and each order's status (isPaid, isDelivered) | Must |

### Reviews

| ID | Requirement | Priority |
| --- | --- | --- |
| FR-REV-01 | A customer can write a review (title, 1–5 rating) only for a product appearing in one of their paid orders | Must |
| FR-REV-02 | Any visitor can view the reviews of a product | Must |
| FR-REV-03 | A customer can update or delete their own review; product ratingsAverage and ratingsQuantity are recalculated | Must |

## 3.2 Non-Functional Requirements

| ID | Requirement | Target |
| --- | --- | --- |
| NFR-SCA-01 | The system supports the baseline load (~10K users, ~1K DAU, ~100 orders/day) and scales to 10× baseline (1,000 orders/day) by adding replicas only, without redesign | 10× headroom |
| NFR-AVL-01 | Core user journeys (browse, cart, checkout, pay) remain available during a single service-instance failure | 99.5% availability |
| NFR-PER-01 | Catalog read endpoints respond within p95 < 300 ms when cached, p95 < 800 ms uncached | p95 targets |
| NFR-PER-02 | Checkout (order creation excluding gateway redirect) completes within p95 < 2 s | p95 target |
| NFR-SEC-01 | Passwords are stored BCrypt-hashed; JWTs are RS256-signed; access tokens expire in 15 min, refresh tokens in 30 days | — |
| NFR-SEC-02 | Every Moamalat payment callback is rejected unless its HMAC-SHA256 signature verifies | 100% verification |
| NFR-SEC-03 | All external traffic uses TLS; the gateway enforces rate limiting and CORS | — |
| NFR-MNT-01 | Each microservice can be deployed independently without redeploying others | — |
| NFR-REL-01 | Payment and stock events are never lost: durable queues, publisher confirms, idempotent consumers | Zero message loss |

## 3.3 Constraints

- The Libyan payment gateway **Moamalat** is the only supported online payment provider (cash on delivery is the offline alternative).
- Clients are Flutter (mobile) and React (web); no other client platforms are in scope.
- This project delivers the architecture document only; no implementation is produced.
