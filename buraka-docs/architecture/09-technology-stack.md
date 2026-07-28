# 9. Technology Stack

## 9.1 Stack Overview

| Layer | Technology | Justification |
| --- | --- | --- |
| Mobile | Flutter | Single codebase for Android and iOS; course-specified |
| Web | React | Largest ecosystem and hiring pool; course-specified |
| API Gateway | Spring Cloud Gateway | Native Spring integration; reactive, non-blocking; route predicates + filters for JWT/rate-limit/CORS |
| Backend services | Spring Boot (Java 17) | Team familiarity; mature JPA/RabbitMQ/Redis ecosystem; course-specified |
| Databases | PostgreSQL ×4 (one per service) | Reliable, full-featured relational store matching the ERD; JSONB available if needed; course-specified |
| Cache | Redis | Cache-aside for catalog reads; refresh-token store with TTL; course-specified |
| Messaging | RabbitMQ | Quorum queues, publisher confirms, mature Spring AMQP support; course-specified |
| Payment gateway | Moamalat | The Libyan market's gateway; hosted page + HMAC-SHA256 callback (design only) |
| Email | SMTP provider | Transactional email for password reset and order confirmation |
| Container | Docker | Standard packaging for all services |
| Orchestration | Kubernetes | Deployments + HPA + StatefulSets as described in the Physical View (§7.4) |
| Ingress | NGINX Ingress Controller | TLS termination, routing to api-gateway |

## 9.2 Versions

| Component | Version baseline |
| --- | --- |
| Java | 17 (LTS) |
| Spring Boot | 4.x |
| Spring Cloud Gateway | matching Spring Cloud release train |
| PostgreSQL | 16 |
| Redis | 7 |
| RabbitMQ | 3.13+ (quorum queues) |
| Flutter | 3.x stable |
| React | 18+ |

## 9.3 Deliberate Omissions

- **No service mesh** (Istio/Linkerd): operational cost unjustified at baseline scale; mTLS and retries handled at the gateway/client level.
- **No dedicated search engine**: PostgreSQL `ILIKE` + indexes suffice at ~10K products scale; Elasticsearch documented as a scalability evolution (§8.1).
- **No separate notification service**: notification modules live inside auth-user-service and order-service per the project constraints.
- **No local K8s simulation**: this deliverable is the document only; Docker Desktop Kubernetes is the noted future validation path.
