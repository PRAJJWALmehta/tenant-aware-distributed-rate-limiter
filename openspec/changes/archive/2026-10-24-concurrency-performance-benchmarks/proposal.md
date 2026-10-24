## Why

While functional tests verify tenant identification and token bucket rate limiting under single-request and basic integration flows, the system lacks empirical validation under heavy concurrent traffic. To prove production-readiness, we need structured load testing that validates gateway throughput, ensures Gateway latency overhead remains within tight Service Level Objectives (< 5ms p95/p99 overhead over upstream latency), rigorously asserts HTTP 429 throttling under budget exhaustion, and verifies strict multi-tenant noisy-neighbor isolation under saturated loads.

## What Changes

- Add a modular k6 load testing suite in `benchmarks/k6-load-test.js` supporting three discrete scenarios:
  1. `nominal_traffic`: 50 Virtual Users (VUs) distributed across 10 tenants within quota to measure baseline latency overhead (p50, p95, p99).
  2. `rate_limit_saturation`: High-velocity burst against a single tenant validating accurate HTTP 429 Too Many Requests responses and standard rate limit headers.
  3. `noisy_neighbor_isolation`: Concurrent load where Tenant A saturates budget and receives 429s, while Tenant B concurrently sends traffic within budget and continues receiving 200 OK with no latency degradation.
- Add an automated benchmark runner script `benchmarks/run-benchmarks.sh` that checks prerequisites (k6, active gateway), executes smoke and full multi-stage load tests (100–300 VUs over 60s), and exports JSON/CSV summary performance metrics.
- Add a comprehensive benchmark report in `docs/BENCHMARKS.md` documenting:
  - Nominal RPS sustained across tenants.
  - Gateway latency overhead percentiles (p50, p95, p99) against the < 5ms SLA target.
  - Rate limiting assertion accuracy and error rates.
  - Multi-tenant isolation verification and noisy neighbor resilience.
  - Test environment hardware specifications (Apple Silicon CPU/RAM context).

## Capabilities

### New Capabilities
- `concurrency-performance-benchmarks`: Concurrency load testing suite with k6 covering nominal traffic, rate limit saturation, and noisy-neighbor isolation, automated execution tooling, and performance documentation.

### Modified Capabilities

## Impact

- New directory and files: `benchmarks/k6-load-test.js`, `benchmarks/run-benchmarks.sh`, `docs/BENCHMARKS.md`.
- Tooling requirements: `k6` CLI for executing load test scenarios.
- Dependencies & systems: Exercises the containerized or local Spring WebFlux Gateway, Redis token bucket Lua scripts, and Mock Upstream LLM service under high concurrency.
- No breaking changes to existing gateway application code, Redis scripts, or upstream mock contracts.
