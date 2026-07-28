# 10. Conclusion

Buraka's architecture is a four-service microservices system — auth-user-service, catalog-service, order-service, review-service — behind a Spring Cloud Gateway, with database-per-service persistence, synchronous REST for queries, and RabbitMQ events for cross-service consistency. The Moamalat hosted-payment-page integration (HMAC-SHA256-signed callbacks) grounds the design in the Libyan market, and JWT + refresh-token security is enforced at the edge.

The design was driven by three decisions, each with an accepted cost:

1. **Microservices over a monolith** — buys independent scaling and fault isolation for the read-heavy catalog vs. write-critical orders; costs operational complexity and eventual consistency.
2. **Event-driven consistency** — keeps user-facing writes fast and absorbs bursts; costs brief windows of stale stock/rating data.
3. **Right-sized infrastructure** — ≥2 replicas, HPA, quorum queues, single-primary databases — meets the 99.5% availability and 10× scalability targets at startup scale without paying for a service mesh or managed HA databases.

The architecture satisfies all functional requirements (FR-AUTH/ CAT/ORD/REV), all six quality attributes at their prioritized depths, and is documented through the required System Context and Component diagrams, optional use-case/sequence/data-flow diagrams, and the complete 4+1 view set.

**Future work:** implement the services per the Development View; validate the Physical View on Docker Desktop Kubernetes; evolve search (dedicated engine) and database HA (managed replication) when scale demands it.
