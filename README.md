# Buraka

Buraka is an academic software-architecture course project for an e-commerce-style application targeting the Libyan market. This repository contains the architecture documentation and a working prototype of two microservices with React and Flutter frontends.

- **Live repo:** https://github.com/alialghanay/buraka
- **Architecture docs:** [`buraka-docs/architecture/`](buraka-docs/architecture/)
- **Requirements:** [`buraka-docs/project-requirements/README.md`](buraka-docs/project-requirements/README.md)
- **Domain model (ERD):** [`buraka-docs/ERD/README.md`](buraka-docs/ERD/README.md)
- **Agent onboarding:** [`AGENTS.md`](AGENTS.md)

## Prototype

The prototype implements two Spring Boot services:

- **catalog-service** (`buraka-back/catalog-service/`) — products, categories, brands, Redis cache-aside, and RabbitMQ-driven rating aggregates.
- **review-service** (`buraka-back/review-service/`) — review CRUD and RabbitMQ event publishing.

Frontends:

- **React web app** (`buraka-fornt/web/`)
- **Flutter mobile app** (`buraka-fornt/mobile/`)

Infrastructure:

- PostgreSQL, Redis, RabbitMQ via Docker Compose (`buraka-back/docker-compose.yml`)
- Scaled catalog behind NGINX for load tests (`buraka-back/docker-compose.scale.yml`)
- k6 load-test scripts and results (`buraka-back/loadtest/`)

## Quick start

```bash
# Start infrastructure
docker compose -f buraka-back/docker-compose.yml up -d

# Run services
cd buraka-back/catalog-service && ./gradlew bootRun   # port 8081
cd buraka-back/review-service && ./gradlew bootRun    # port 8082

# Run smoke test
./buraka-back/smoke-test.sh

# Run web frontend
cd buraka-fornt/web && npm run dev                    # port 5173

# Run mobile frontend
cd buraka-fornt/mobile && flutter run -d macos        # or -d chrome
```

See [`AGENTS.md`](AGENTS.md) for the full onboarding guide, conventions, and known prototype simplifications.
