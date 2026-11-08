## Context

The tenant-aware distributed rate limiter operates as an API gateway fronting upstream LLM completion providers. It enforces per-tenant token bucket quotas utilizing non-blocking Spring WebFlux and atomic Redis Lua scripts. Functional correctness has been verified across tenant identification, token estimation, token-bucket rate limiting, gateway routing, and Docker orchestration.

To validate production readiness, empirical benchmarks are needed to prove that:
1. The gateway can handle sustained concurrent traffic across multiple tenants without errors or thread pool starvation.
2. The processing overhead introduced by the gateway (header extraction, token estimation, and Redis roundtrip) satisfies the Service Level Objective of < 5ms overhead at p95 and p99 percentiles above upstream latency.
3. Burst traffic from an over-consuming tenant is immediately and accurately throttled with HTTP 429 and rate-limiting headers.
4. Tenant isolation is strictly preserved so that saturated tenants ("noisy neighbors") do not degrade latency or cause false-positive throttles for conforming tenants.

## Goals / Non-Goals

**Goals:**
- Provide a standardized, scriptable k6 load testing suite (`benchmarks/k6-load-test.js`) implementing three discrete scenarios: `nominal_traffic`, `rate_limit_saturation`, and `noisy_neighbor_isolation`.
- Implement a turnkey benchmark runner script (`benchmarks/run-benchmarks.sh`) that verifies gateway availability, runs a smoke test (`5 VUs`, `10s`), executes a full load test (`100–300 VUs`, `60s`), and exports structured JSON/CSV metrics.
- Produce an empirical benchmark report in `docs/BENCHMARKS.md` detailing sustained RPS, p50/p95/p99 latency percentiles, gateway overhead relative to the < 5ms SLA, HTTP 429 precision, multi-tenant isolation, and execution hardware context.

**Non-Goals:**
- Modifying gateway routing rules, Redis Lua scripts, or mock upstream contracts.
- Distributed multi-host load generation (tests are executed from the local development host against either Docker Compose or local gateway).
- Profiling external cloud LLM providers (tests target the controlled mock upstream with deterministic latency).

## Decisions

### 1. Tool Selection: k6 over JMeter / wrk / Apache Bench
- **Decision**: Use Grafana k6 for all performance and concurrency benchmarks.
- **Rationale**: k6 provides modern ES6 scripting, native support for multi-scenario orchestration, custom metrics (Counter, Trend, Rate), rich assertion thresholds, low memory overhead (Go runtime), and straightforward JSON/CSV data export.
- **Alternatives Considered**: 
  - *Apache Bench / wrk*: High raw throughput, but lacks multi-scenario orchestration, JSON payload generation, and multi-tenant assertion logic.
  - *JMeter*: Heavyweight Java GUI/XML configuration, harder to version-control and run cleanly in developer workflows.

### 2. Multi-scenario Architecture in a Single Script
- **Decision**: Configure k6 `scenarios` with execution tags and conditional selection via an environment variable (`SCENARIO_NAME`), while allowing combined end-to-end execution.
- **Rationale**: Enables focused single-scenario execution during debugging or smoke verification as well as comprehensive end-to-end test runs without duplicating payload structures or custom metric counters.
- **Tenant Partitioning**:
  - `nominal_traffic`: Uniformly rotates across `tenant-01` through `tenant-10` with request rates calibrated within their token bucket allowances.
  - `rate_limit_saturation`: Sends maximum-velocity requests from a dedicated `tenant-burst` to immediately exhaust bucket tokens and assert consecutive HTTP 429s.
  - `noisy_neighbor_isolation`: Concurrently runs `tenant-noisy` at saturation alongside `tenant-polite` within quota, asserting 429 responses for `tenant-noisy` and 100% 200 OK responses with uncompromised latency for `tenant-polite`.

### 3. Measuring Gateway Latency Overhead
- **Decision**: Calculate Gateway overhead as `Total Latency - Mock Upstream Latency (~30ms)`.
- **Rationale**: The mock upstream introduces a deterministic ~30ms sleep. Any roundtrip latency observed beyond ~30ms directly reflects Gateway request ingestion, JSON body buffering, token estimation, Redis reactive command dispatch, Lua script execution, and response proxying. The target overhead is < 5ms for p95/p99.

### 4. Automated Runner Script Design
- **Decision**: Implement `benchmarks/run-benchmarks.sh` in portable Bash, supporting environment variable overrides (`GATEWAY_URL`, `VUS`, `DURATION`, `OUTPUT_DIR`).
- **Rationale**: Facilitates seamless execution across both local host processes (`http://localhost:8080`) and containerized Docker Compose stacks, automating smoke validation, full benchmark execution, and metric report generation into `benchmarks/results/`.

## Risks / Trade-offs

- **[Risk] Host Resource Contention**: Running k6, Spring Boot Gateway, Redis, and Python mock upstream on a single machine can lead to CPU throttling and skewed latency measurements.
  - *Mitigation*: Run smoke tests first to verify baseline resource stability. Profile and document exact machine specifications (Apple Silicon chip, memory, OS) in `docs/BENCHMARKS.md` so results are contextualized and reproducible.
- **[Risk] Continuous Token Refill in Saturation Tests**: A low burst rate might be continuously replenished by the token bucket refill rate.
  - *Mitigation*: Ensure the saturation scenario deploys sufficiently high VU concurrency and minimal sleep delays to guarantee immediate quota exhaustion.
- **[Risk] Redis State Residuals**: Residual tokens from prior runs could affect initial saturation timing.
  - *Mitigation*: The benchmark script or scenarios can reset or use isolated tenant keys for each distinct scenario execution.
