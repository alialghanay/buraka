# Scalability Experiment Results

| Run | Replicas | Cache | Total reqs | Req rate (req/s) | p50 (ms) | p95 (ms) | p99 (ms) | Error rate |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 1 | off | 84958 | 707.46 | 3.50 | 13.77 | 19.84 | 0.00% |
| 2 | 1 | on | 85432 | 711.57 | 1.95 | 11.55 | 30.06 | 0.00% |
| 3 | 3 | on | 86812 | 722.78 | 1.62 | 7.66 | 23.33 | 0.00% |

## Notes

- Hardware: MacBook Pro 14,7, Apple M2, 8 GB RAM, macOS 15.5 (Darwin 25.5.0 arm64)
- Date: 2026-07-29
- Does Run 2 meet NFR-PER-01 (p95 < 300 ms cached)? Yes — p95 = 11.55 ms, well under 300 ms.
- Does Run 3 show horizontal scaling over Run 2? Yes — request rate increased from 711.57 req/s to 722.78 req/s and p95 dropped from 11.55 ms to 7.66 ms with 3 replicas versus 1.
- All runs used the default script peak of 150 VUs; no VU reduction was needed because error rates were 0.00% in every run.
- k6 was invoked with `--summary-trend-stats="avg,min,med,max,p(90),p(95),p(99)"` so p(99) is included in the table.
