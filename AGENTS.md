# Buraka — Agent Onboarding Guide

This guide is written for AI coding agents that need to work on the Buraka project. It reflects the repository as it currently exists; do not assume features or tooling that are not present.

## Project Overview

Buraka is an academic software-architecture course project for an e-commerce-style application. The domain, documented in `buraka-docs/ERD/README.md`, covers:

- `USER`, `ORDER`, `ORDER_ITEM`
- `CART`, `CART_ITEM`
- `REVIEW`
- `BRAND`, `PRODUCT`, `CATEGORY` (with parent/child categories)

As of today only the backend skeleton exists under `buraka-back/Buraka`. The frontend directory (`buraka-fornt/`) is empty and there is no client-side code or configuration.

## Repository Layout

```
buraka/
├── buraka-back/
│   └── Buraka/              # Gradle-based Spring Boot application
│       ├── build.gradle
│       ├── settings.gradle
│       ├── gradlew / gradlew.bat
│       ├── gradle/wrapper/
│       ├── HELP.md
│       ├── .gitignore
│       └── src/
│           ├── main/java/org/ali/projects/buraka/
│           │   └── BurakaApplication.java
│           ├── main/resources/
│           │   └── application.properties
│           └── test/java/org/ali/projects/buraka/
│               └── BurakaApplicationTests.java
├── buraka-docs/
│   ├── ERD/README.md        # Entity-relationship diagrams in Mermaid
│   └── Software Architecture Course Project.pdf
└── buraka-fornt/            # Empty placeholder directory
```

## Technology Stack

- **Language:** Java 17 (configured in `build.gradle` toolchain)
- **Build Tool:** Gradle 9.5.1 via wrapper (`gradlew`)
- **Framework:** Spring Boot 4.1.0
  - Spring Framework 7.0.8
  - Spring MVC / Servlet stack (`spring-boot-starter-webmvc`)
  - Embedded Tomcat 11.0.22
  - Jackson for JSON
- **Testing:** JUnit 5 (JUnit Platform) + AssertJ
- **Other:** Jakarta Annotations 3.0.0, Micrometer observation, jSpecify annotations

## Build, Run and Test Commands

All commands should be run from `buraka-back/Buraka/`.

| Command | Purpose |
|---|---|
| `./gradlew build` | Compile, run tests, and package the application |
| `./gradlew test` | Run the test suite |
| `./gradlew bootRun` | Run the application with the embedded Tomcat server |
| `./gradlew bootJar` | Build the executable Spring Boot JAR |
| `./gradlew bootBuildImage` | Build an OCI image using the Spring Boot Gradle plugin |
| `./gradlew dependencies --configuration runtimeClasspath` | Inspect resolved runtime dependencies |

The build was verified with `./gradlew test` on the current checkout.

## Code Organization

- **Base package:** `org.ali.projects.buraka`
- **Entry point:** `BurakaApplication.java` (`@SpringBootApplication`)
- **Configuration:** `src/main/resources/application.properties` currently only sets `spring.application.name=Buraka`
- **Domain / controllers / services / repositories:** None exist yet. The project is a bare Spring Boot scaffold. Add new packages under `org.ali.projects.buraka` following Java conventions (e.g., `...buraka.controller`, `...buraka.service`, `...buraka.repository`, `...buraka.entity`).

## Development Conventions

- Standard Maven/Gradle source layout (`src/main/java`, `src/test/java`, `src/main/resources`).
- Package names use the group id `org.ali.projects.buraka`.
- `.gitignore` ignores Gradle build outputs, IDE files (IntelliJ IDEA, VS Code, STS, NetBeans), and `.gradle`.
- `HELP.md` is auto-generated Spring Boot reference documentation; do not treat it as project-specific instructions.
- Documentation language in the project is English.

## Testing Instructions

- Tests use JUnit 5 with the JUnit Platform (`useJUnitPlatform()` in `build.gradle`).
- The only existing test is `BurakaApplicationTests.contextLoads()`, which currently contains a trivial AssertJ assertion (`assertThat(42).isEqualTo(42)`) to verify that the Spring context loads.
- Add real unit and integration tests under `src/test/java/org/ali/projects/buraka/` mirroring the main package structure.

## Deployment Notes

- No Dockerfile, `docker-compose.yml`, CI/CD workflows, or cloud manifests are present.
- The deployable artifact is the Spring Boot executable JAR produced by `./gradlew bootJar`.
- An OCI image can be produced with `./gradlew bootBuildImage` if a builder/Pack environment is available.
- Default port and runtime profile behavior are not configured; Spring Boot will use its defaults (port `8080`) unless overridden in `application.properties` or via command-line arguments.

## Security Considerations

- The application currently has **no authentication or authorization** implemented, even though the ERD defines password and password-reset fields on `USER`.
- `application.properties` contains no secrets, database credentials, or tokens.
- If you implement user management, do **not** store plaintext passwords; use Spring Security with a strong password encoder, keep reset codes short-lived and hashed, and externalize secrets to environment variables or a secrets manager.
- There is no input validation layer yet; add Bean Validation and proper DTOs before exposing endpoints.

## Useful References

- `buraka-docs/ERD/README.md` — domain model and relationships
- `buraka-back/Buraka/build.gradle` — dependencies and build configuration
- `buraka-back/Buraka/HELP.md` — generic Spring Boot/Gradle links

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
