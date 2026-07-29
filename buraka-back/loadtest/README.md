# Buraka Scalability Experiments

Load-tests the catalog read path behind the NGINX load balancer, validating the architecture document's claims:

- **NFR-PER-01:** cached catalog reads p95 < 300 ms (Run 2)
- **NFR-SCA-01:** throughput scales horizontally with replicas (Run 3 vs Run 2)

All commands run from the repository root. k6 runs inside Docker (`grafana/k6`), so no local install is needed; `host.docker.internal` lets the k6 container reach the host-published NGINX port.

## Setup (once)

```bash
docker compose -f buraka-back/docker-compose.yml -f buraka-back/docker-compose.scale.yml build
```

The first build downloads Gradle and dependencies inside Docker — allow 10+ minutes.

## Run 1 — 1 replica, cache OFF (baseline, uncached)

```bash
CACHE_ENABLED=false docker compose -f buraka-back/docker-compose.yml -f buraka-back/docker-compose.scale.yml up -d --scale catalog-service=1
sleep 20   # allow seed data load
docker run --rm -i -e BASE_URL=http://host.docker.internal:8080 grafana/k6 run - < buraka-back/loadtest/catalog-read.js
docker compose -f buraka-back/docker-compose.yml -f buraka-back/docker-compose.scale.yml down
```

## Run 2 — 1 replica, cache ON (validates NFR-PER-01)

Same commands with `CACHE_ENABLED=true` and `--scale catalog-service=1`.

## Run 3 — 3 replicas, cache ON (validates NFR-SCA-01)

Same commands with `CACHE_ENABLED=true` and `--scale catalog-service=3`. The NGINX config re-resolves Docker DNS every 5 s, so all three replicas receive traffic without restarting the LB.

## What to record

From each k6 end-of-run summary, copy into `results.md`: `http_reqs` (total/s rate), `http_req_duration` avg/p(95)/p(99), `http_req_failed` rate.

## Expected outcomes

- Run 2 p(95) is dramatically lower than Run 1 and under the 300 ms cached target — the Redis cache-aside absorbs the hot product-detail traffic (70% of requests).
- Run 3 sustains a higher request rate than Run 2 at similar latency — horizontal scaling behind the LB adds capacity.
- Failure rate stays under 1% in all runs (k6 threshold fails the run otherwise).
