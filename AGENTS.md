# Buraka — Agent Onboarding Guide

This guide is written for AI coding agents that need to work on the Buraka project. It reflects the repository as it currently exists; do not assume features or tooling that are not present.

## Project Overview

Buraka is an academic software-architecture course project for an e-commerce-style application. The domain, documented in `buraka-docs/ERD/README.md`, covers:

- `USER`, `ORDER`, `ORDER_ITEM`
- `CART`, `CART_ITEM`
- `REVIEW`
- `BRAND`, `PRODUCT`, `CATEGORY` (with parent/child categories)

The repository contains:

- The original backend scaffold under `buraka-back/Buraka/`.
- A working **prototype** under `buraka-back/catalog-service/` and `buraka-back/review-service/` with React and Flutter frontends. The prototype is described in detail in the "Prototype" section at the bottom of this file.

## Repository Layout

```
buraka/
├── buraka-back/
│   ├── Buraka/              # Original Gradle-based Spring Boot scaffold
│   ├── catalog-service/     # Prototype catalog service (Spring Boot 4.1)
│   ├── review-service/      # Prototype review service (Spring Boot 4.1)
│   ├── docker-compose.yml   # Prototype infrastructure (postgres×2, redis, rabbitmq)
│   ├── docker-compose.scale.yml  # Catalog behind NGINX LB for scale tests
│   ├── nginx/lb.conf        # NGINX load-balancer config
│   ├── smoke-test.sh        # End-to-end event-flow smoke test
│   └── loadtest/            # k6 scalability experiments
├── buraka-fornt/
│   ├── web/                 # React (Vite) storefront
│   └── mobile/              # Flutter storefront
├── buraka-docs/
│   ├── ERD/README.md        # Entity-relationship diagrams in Mermaid
│   ├── architecture/        # Architecture documentation
│   └── project-requirements/# Project requirements (README.md + PDF)
└── AGENTS.md                # This file
```

## Technology Stack

- **Language:** Java 25 for the prototype services; the original `buraka-back/Buraka/` scaffold is still configured for Java 17.
- **Build Tool:** Gradle 9.5.1 via wrapper (`gradlew`)
- **Framework:** Spring Boot 4.1.0
  - Spring Framework 7.0.8
  - Spring MVC / Servlet stack (`spring-boot-starter-webmvc`)
  - Embedded Tomcat 11.0.22
  - Jackson 3 for JSON
- **Data:** PostgreSQL 16, Spring Data JPA, Redis 7 (cache-aside)
- **Messaging:** RabbitMQ 3.13 (`JacksonJsonMessageConverter` with trusted package)
- **Frontends:** React (Vite), Flutter 3.44.5
- **Load Testing:** k6 (via `grafana/k6` Docker image)
- **Testing:** JUnit 5 + AssertJ + Mockito; `@WebMvcTest` slices; vitest; Flutter tests

## Build, Run and Test Commands

### Backend services

Run from the service directory (`buraka-back/catalog-service/` or `buraka-back/review-service/`):

| Command | Purpose |
|---|---|
| `./gradlew build` | Compile, run tests, and package the service |
| `./gradlew test` | Run the service test suite |
| `./gradlew bootRun` | Run the service locally with embedded Tomcat |
| `./gradlew bootJar` | Build the executable Spring Boot JAR |

### Infrastructure and end-to-end smoke test

From the repository root:

```bash
# Start PostgreSQL, Redis, and RabbitMQ
docker compose -f buraka-back/docker-compose.yml up -d

# Run the end-to-end smoke test (requires catalog-service on :8081 and review-service on :8082)
./buraka-back/smoke-test.sh
```

### Scaled catalog load test

```bash
# Build the catalog Docker image
docker compose -f buraka-back/docker-compose.yml -f buraka-back/docker-compose.scale.yml build

# Run with 1 replica and cache enabled (see loadtest README for cache-off/3-replica variants)
CACHE_ENABLED=true docker compose -f buraka-back/docker-compose.yml -f buraka-back/docker-compose.scale.yml up -d --scale catalog-service=1

# Run k6
docker run --rm -i -e BASE_URL=http://host.docker.internal:8080 grafana/k6 run - < buraka-back/loadtest/catalog-read.js
```

### Frontends

```bash
# React web app
cd buraka-fornt/web
npm install
npm run build
npx vitest run
npm run dev          # dev server on http://localhost:5173

# Flutter mobile app
cd buraka-fornt/mobile
flutter pub get
flutter test
flutter run -d macos # or -d chrome
```

## Code Organization

- **Base packages:**
  - `org.ali.projects.buraka.catalog` for `catalog-service`
  - `org.ali.projects.buraka.review` for `review-service`
- Each service follows package-by-feature conventions:
  - `...catalog.product`, `...catalog.category`, `...catalog.brand`, `...catalog.config`, `...catalog.messaging`
  - `...review.review`, `...review.config`, `...review.messaging`
- The shared event contract `org.ali.projects.buraka.shared.messaging.ReviewEvent` is duplicated into both services with the identical fully-qualified name so RabbitMQ JSON type headers resolve across services. Keep both copies in sync.

## Development Conventions

- Standard Maven/Gradle source layout (`src/main/java`, `src/test/java`, `src/main/resources`).
- Package names use the group id `org.ali.projects.buraka`.
- `.gitignore` ignores Gradle build outputs, IDE files, and `.gradle`.
- `HELP.md` is auto-generated Spring Boot reference documentation; do not treat it as project-specific instructions.
- Documentation language in the project is English.

## Testing Instructions

- Backend services use JUnit 5 with the JUnit Platform (`useJUnitPlatform()` in `build.gradle`).
- Use `@WebMvcTest` + `@MockitoBean` for controller slices and Mockito for service unit tests.
- Do **not** add `@SpringBootTest` or Testcontainers to the prototype services; keep the suite fast and infra-free.
- Frontend tests:
  - Web: `cd buraka-fornt/web && npx vitest run`
  - Mobile: `cd buraka-fornt/mobile && flutter test`

## Deployment Notes

- The original scaffold deployable artifact is the Spring Boot executable JAR produced by `./gradlew bootJar` inside `buraka-back/Buraka/`.
- The prototype uses Docker Compose for local deployment:
  - `buraka-back/docker-compose.yml` — infrastructure only.
  - `buraka-back/docker-compose.scale.yml` + `nginx/lb.conf` — catalog service behind NGINX for scaling experiments.
- `buraka-back/catalog-service/Dockerfile` builds the catalog JAR inside a multi-stage image.
- No CI/CD workflows or cloud manifests are present.

## Security Considerations

- The prototype has **no authentication or authorization**. Users are identified by an `X-User-Id` request header (default `1` in the frontends).
- CORS is configured as `allowedOrigins("*")` on `/api/**` for local frontend development — this is intentional for the prototype but must be narrowed for any real deployment.
- Database credentials (`buraka/buraka`) are committed in plain text in `application.properties` and Docker Compose files. This is acceptable only for local prototype use.
- If you implement real user management, do **not** store plaintext passwords; use Spring Security with a strong password encoder, keep reset codes short-lived and hashed, and externalize secrets to environment variables or a secrets manager.
- There is no input validation layer beyond the prototype's DTO annotations; add Bean Validation and proper DTOs before exposing endpoints to untrusted clients.

## Useful References

- `buraka-docs/ERD/README.md` — domain model and relationships
- `buraka-docs/project-requirements/README.md` — project requirements
- `buraka-docs/architecture/` — architecture documentation
- `buraka-back/Buraka/build.gradle` — original scaffold dependencies
- `docs/superpowers/plans/2026-07-28-buraka-prototype.md` — prototype implementation plan

## Prototype (added 2026-07-28)

A working prototype now exists alongside the architecture document:

- `buraka-back/catalog-service/` — Spring Boot 4.1 service (port 8081): products/categories/brands REST API, Redis cache-aside (`cache.enabled` toggle), consumes `review.*` RabbitMQ events to maintain rating aggregates. Database `buraka_catalog_db` (postgres on 5433).
- `buraka-back/review-service/` — Spring Boot 4.1 service (port 8082): review CRUD with `X-User-Id` header auth (prototype stand-in), publishes `review.created`/`review.deleted` events. Database `buraka_review_db` (postgres on 5434).
- `buraka-back/docker-compose.yml` — infrastructure: postgres×2, redis, rabbitmq. `docker-compose.scale.yml` + `nginx/lb.conf` — catalog behind NGINX LB on 8080 for scale tests.
- `buraka-back/smoke-test.sh` — end-to-end check (review → event → rating update).
- `buraka-back/loadtest/` — k6 scalability experiments (run via `grafana/k6` Docker image); results in `results.md`.
- `buraka-fornt/web/` — React (Vite) storefront on 5173 with dev proxies to both services.
- `buraka-fornt/mobile/` — Flutter storefront (`buraka_mobile`), runs on macOS/Chrome.

Prototype simplifications (deliberate): no api-gateway, no auth/order services, no Moamalat; CORS is `*`; users identified by `X-User-Id` header. The shared event contract `org.ali.projects.buraka.shared.messaging.ReviewEvent` is duplicated into both services with the identical fully-qualified name — this is required for RabbitMQ JSON type headers to resolve; keep both copies in sync.

Run backend tests: `cd buraka-back/catalog-service && ./gradlew test` (same for `review-service`). Frontend tests: `cd buraka-fornt/web && npx vitest run`; `cd buraka-fornt/mobile && flutter test`.
