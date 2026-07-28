# 8. Quality Attributes

Analysis depth follows the project priority: Scalability (deepest) → Availability → Performance and Security (standard) → Maintainability and Reliability (lighter). Baseline load: ~10K users, ~1K DAU, ~100 orders/day.

## 8.1 Scalability (deepest analysis)

**Goal (NFR-SCA-01):** absorb 10× baseline (10K DAU, 1,000 orders/day) by adding replicas only — no redesign.

**How the architecture achieves it:**

1. **Stateless services.** No session state in any service; JWTs carry identity, refresh tokens live in redis. Any replica can serve any request, so horizontal scaling is purely additive.
2. **Independent scaling per workload.** Catalog reads (browsing) dominate traffic — likely 95%+ of requests at baseline — while order writes are rare (~100/day). Microservices let HPA scale catalog-service to 10 replicas during a promotion while order-service stays at 2. A monolith would have to scale the whole store for a read spike.
3. **HPA policy.** Every Deployment: CPU threshold 70%, min 2, max 10 replicas (see §7.4). At 10× load, capacity headroom = 5× per service replica count plus cache offload — sufficient without architectural change.
4. **Cache offload.** Hot catalog reads are served from redis (target ≥80% hit rate on product detail keys), so the 10× read growth mostly hits redis, not buraka_catalog_db.
5. **Event-driven load absorption.** Checkout bursts (e.g., a promotion launch) enqueue OrderPlaced faster than catalog-service can process; rabbitmq buffers the spike and consumers drain at their own pace. The user-facing write path stays fast because stock updates are async.
6. **Database-per-service.** Write pressure on buraka_order_db never competes with read pressure on buraka_catalog_db. Each PostgreSQL scales independently.

**Scaling ceiling and next steps (beyond 10×):** catalog read replicas behind a load-balancing proxy, product search moved to a dedicated engine (e.g., Elasticsearch), rabbitmq queue partitioning by product ID for stock consumers. These are documented as future evolutions, not current design.

**Trade-off accepted:** cross-service joins are impossible — e.g., "orders with product titles" requires order-service to call catalog-service or denormalize titles into ORDER_ITEM (chosen: denormalize price/title snapshot into ORDER_ITEM at checkout, since titles may change later but historical orders must not).

## 8.2 Availability (second-deepest)

**Goal (NFR-AVL-01):** 99.5% availability; core journeys survive a single instance failure.

**How the architecture achieves it:**

1. **Minimum 2 replicas per service** behind Kubernetes Services — the loss of any one pod leaves capacity (§7.4).
2. **Health management:** readiness probes remove unready pods from load balancing; liveness probes restart wedged ones; rolling updates with maxUnavailable=0 keep capacity during deploys.
3. **rabbitmq ×3 with quorum queues:** the broker survives one node loss without message unavailability (NFR-REL-01).
4. **Graceful degradation:** if order-service is down, customers can still browse the catalog and manage reviews; if redis is down, catalog-service falls back to PostgreSQL (slower, within NFR-PER-01's uncached p95 < 800 ms, but available).
5. **Fault isolation by design:** a review-service outage cannot cascade into checkout — the only synchronous cross-service calls on the checkout path are order→catalog reads, and checkout fails fast with a clear error rather than queueing behind a hung dependency (timeouts + circuit breaker at the caller).

**Residual single points accepted at this scale:** each PostgreSQL is a single primary (managed failover via StatefulSet restart, RTO ~minutes); redis is a single instance (tolerable — cache and refresh tokens are recoverable). Documented as accepted risk with managed-database replication as the production evolution.

## 8.3 Performance

**Goals (NFR-PER-01 and NFR-PER-02):** catalog reads p95 < 300 ms cached / < 800 ms uncached; checkout p95 < 2 s.

- **redis cache-aside** for product lists and details; TTL plus explicit invalidation on stock/rating events (§5.3).
- **Async off critical path:** stock updates, rating recalculation, and email sending never block the user's request — they ride rabbitmq events.
- **Database indexes:** PRODUCT.slug, PRODUCT.categoryId, PRODUCT.brandId, USER.email, USER.username, ORDER.userId, REVIEW.productId; pagination on every list endpoint (no unbounded queries).
- **Gateway efficiency:** connection pooling to services; JWT validation is local (RS256 public key, no per-request call to auth-user-service).

## 8.4 Security

**Goals (NFR-SEC-01, NFR-SEC-02, and NFR-SEC-03).**

- **Authentication:** RS256-signed JWTs; access 15 min, refresh 30 days with rotation; refresh tokens stored in redis (revocable).
- **Passwords:** BCrypt hashes only; reset codes short-lived and single-use.
- **Edge enforcement:** TLS terminated at the NGINX Ingress; the api-gateway validates JWTs, applies rate limiting, and enforces CORS allow-lists.
- **Payment security:** Moamalat callbacks are the only unauthenticated endpoint; every callback is HMAC-SHA256-verified before any state change (100% verification, NFR-SEC-02); the HMAC secret lives in Kubernetes Secrets.
- **Data isolation:** database-per-service means a compromised service credential exposes only that service's data.

## 8.5 Maintainability (lighter)

Single-responsibility services with explicit contracts (§5); independent deployability (NFR-MNT-01); one shared module (`event-contracts`) keeps event schemas in one place; documentation-as-code (this document, Mermaid in git) keeps architecture knowledge diffable and reviewable.

## 8.6 Reliability (lighter)

Outbox pattern for order-service state+event writes; durable quorum queues with publisher confirms; idempotent consumers (event IDs recorded, duplicates dropped); retries with exponential backoff and dead-letter queues for poison messages (NFR-REL-01: zero message loss for payment and stock events).

## 8.7 Attribute Trade-off Summary

| Decision | Helps | Costs |
| --- | --- | --- |
| Microservices over monolith | Scalability, Availability, Maintainability | Operational complexity; eventual consistency |
| Async stock/rating updates | Performance, Scalability | Brief windows of stale counts |
| redis as single instance | Performance, simplicity | Availability risk on cache loss (mitigated: DB fallback) |
| Single-primary PostgreSQL per service | Simplicity at baseline scale | Availability RTO of minutes on DB failure |
