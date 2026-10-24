# concurrency-performance-benchmarks Specification

## Purpose
Provide a standardized k6 load testing suite, automated benchmark execution runner, and comprehensive empirical performance documentation validating throughput, tiered gateway latency overhead SLOs (p50 < 2ms, p95 < 5ms, p99 < 15ms), rate limit throttling accuracy, and multi-tenant noisy-neighbor isolation.

## Requirements

### Requirement: k6 load test scenario definitions
The system SHALL provide a modular k6 load testing suite in `benchmarks/k6-load-test.js` defining scenarios for nominal traffic, rate limit saturation, and noisy neighbor tenant isolation. The suite SHALL configure realistic HTTP requests targeting the `/v1/chat/completions` endpoint with standard JSON payloads, `X-Tenant-ID` identification headers, and validation assertions for response status codes and timing metrics.

#### Scenario: Nominal traffic scenario execution
- **WHEN** the `nominal_traffic` scenario executes with 50 virtual users distributed across 10 distinct tenants within quota limits
- **THEN** all requests receive HTTP 200 OK responses with zero network errors, and gateway roundtrip latency percentiles (p50, p95, p99) are tracked.

#### Scenario: Rate limit saturation scenario execution
- **WHEN** the `rate_limit_saturation` scenario executes an intentional high-velocity burst of requests against a single tenant exceeding their configured token bucket limit
- **THEN** requests exceeding the quota return HTTP 429 Too Many Requests responses.

#### Scenario: Noisy neighbor isolation scenario execution
- **WHEN** the `noisy_neighbor_isolation` scenario executes concurrently with Tenant A saturating their token quota and Tenant B sending traffic within their normal quota
- **THEN** Tenant A receives HTTP 429 responses once saturated, while Tenant B concurrently receives HTTP 200 OK responses with no latency degradation or dropped requests.

### Requirement: Automated benchmark runner script
The system SHALL provide an executable shell script `benchmarks/run-benchmarks.sh` to automate benchmark execution, validate environmental prerequisites, support configurable target endpoints and concurrency, and export benchmark summaries.

#### Scenario: Prerequisites and connectivity verification
- **WHEN** `bash benchmarks/run-benchmarks.sh` is invoked
- **THEN** the script verifies that `k6` is installed and that the rate-limiting gateway is reachable at the target host/port, exiting with an informative error if unavailable.

#### Scenario: Smoke test verification
- **WHEN** the runner executes the smoke test stage (`k6 run --vus 5 --duration 10s`)
- **THEN** the execution finishes with 0 network errors and confirms successful 200 and 429 assertion handling before running extended benchmarks.

#### Scenario: Full benchmark execution and export
- **WHEN** the runner executes the full load test suite with 100 to 300 concurrent VUs sustained for 60 seconds
- **THEN** the runner outputs detailed performance metrics to stdout and exports summary metrics to structured JSON and CSV formats in the benchmark output directory.

### Requirement: Gateway overhead telemetry header
The rate-limiting gateway SHALL calculate its internal processing overhead (excluding upstream service round-trip duration for proxied calls) and attach an `X-Gateway-Overhead-Ms` HTTP header to all client responses (both HTTP 200 proxy responses and HTTP 429 rate limit responses). The load testing suite SHALL read this header to empirically evaluate SLO compliance against realistic tiered latency budgets: p50 < 2ms, p95 < 5ms, and p99 < 15ms.

#### Scenario: Proxied response telemetry
- **WHEN** an allowed client request is forwarded to upstream service and returns HTTP 200 OK
- **THEN** the gateway injects the `X-Gateway-Overhead-Ms` header reflecting total gateway processing time minus upstream network/processing duration.

#### Scenario: Rate-limited response telemetry
- **WHEN** a client request exceeds rate limit budget and returns HTTP 429 Too Many Requests
- **THEN** the gateway injects the `X-Gateway-Overhead-Ms` header measuring the exact gateway rate limiter decision latency.

### Requirement: Benchmark performance report documentation
The system SHALL provide a structured benchmark report in `docs/BENCHMARKS.md` that synthesizes empirical load test findings, assesses compliance with latency Service Level Objectives (SLOs), and provides the test hardware environment context.

#### Scenario: Latency overhead and throughput documentation
- **WHEN** the benchmark results are recorded in `docs/BENCHMARKS.md`
- **THEN** the document records sustained nominal requests per second (RPS) and confirms whether gateway processing overhead satisfies the tiered latency SLOs: p50 < 2ms, p95 < 5ms, and p99 < 15ms above upstream service latency.

#### Scenario: Rate limit accuracy and isolation verification
- **WHEN** the benchmark results are reviewed in `docs/BENCHMARKS.md`
- **THEN** the document reports the 429 HTTP status assertion accuracy under saturation and details empirical evidence of noisy-neighbor isolation.

#### Scenario: Hardware and environment specification context
- **WHEN** reproducing benchmark results from `docs/BENCHMARKS.md`
- **THEN** the report details the test environment hardware context including CPU architecture (e.g., Apple Silicon M-series), CPU core count, system memory, OS version, and container topology.
