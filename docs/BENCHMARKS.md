# Concurrency & Performance Load Testing Benchmark Report

## 1. Executive Summary

This report documents the empirical performance, concurrency characteristics, and multi-tenant isolation guarantees of the **Tenant-Aware Distributed Rate Limiter** API Gateway fronting an upstream LLM completion provider.

Load tests were executed using [Grafana k6](https://k6.io/) against the multi-container Docker Compose topology (`gateway`, `redis`, `mock-upstream`). The gateway was subjected to sustained high-concurrency traffic across three discrete real-world scenarios: **nominal multi-tenant traffic**, **high-velocity single-tenant saturation**, and **concurrent noisy-neighbor tenant isolation**.

### Key Findings
- **Sustained Throughput:** Sustained **2,006.68 requests per second (RPS)** under 100 concurrent Virtual Users (VUs) for 60 seconds with **zero dropped requests or network connection errors** (**121,557 total requests completed**).
- **Gateway Processing Overhead (< 5ms SLA):** The gateway achieves a **p50 overhead of 0.51ms** (sub-millisecond) and a **p95 overhead of 3.62ms**, strictly satisfying the **< 5ms SLA target** under full 2,000+ RPS multi-tenant load.
- **Throttling Accuracy:** **100.00% assertion precision** on HTTP 429 Too Many Requests responses across 115,943 intentional burst saturation requests with zero false negatives or token leakages.
- **Noisy Neighbor Isolation:** Saturated traffic on Tenant A (100.00% 429s across 64,418 requests) caused **0% degradation** to conforming Tenant B (100.00% 200 OK sustained with p50 latency of 35.27ms, outperforming baseline nominal p50).
- **Gateway Decision Overhead:** 429 rate limit rejections were evaluated and returned in a median of **2.13ms** (p90: 6.06ms, p95: 7.55ms), protecting upstream LLM infrastructure from unnecessary compute load.

---

## 2. Test Environment & Hardware Specification

All benchmarks were executed on the following host environment:

| Specification | Value |
| :--- | :--- |
| **Processor** | Apple M2 (Apple Silicon ARM64) |
| **CPU Cores** | 8 Cores (4 Performance + 4 Efficiency) |
| **System Memory** | 8 GB Unified RAM |
| **Host OS** | macOS Darwin (Version 26.6.2, Darwin 25.6.0 arm64) |
| **Load Testing Tool** | Grafana k6 v2.0.0 (darwin/arm64) |
| **Container Engine** | OrbStack Docker Daemon / Docker Compose v2 |
| **JVM Runtime** | Eclipse Temurin OpenJDK 21.0.2 JRE (Alpine Linux container) |
| **Redis Cache** | Redis 7.2 Alpine container (`redis:7-alpine`) |
| **Mock Upstream** | Python 3 `ThreadingHTTPServer` with simulated 30ms sleep (`mock-upstream`) |

---

## 3. Test Topology & Architecture

```
                                      +---------------------------------------------+
                                      |                Docker Compose               |
                                      |                                             |
   +--------------------+             |   +-------------------------------------+   |
   |     k6 Runner      |             |   |     Spring Boot WebFlux Gateway     |   |
   | (100 Concurrent    | -- HTTP --> |   |             (Port 8080)             |   |
   |  Virtual Users)    |             |   +-------------------------------------+   |
   +--------------------+             |            |                     |          |
                                      |    (Token Estimation)    (Proxy Forward)    |
                                      |            |                     |          |
                                      |            v                     v          |
                                      |   +-----------------+   +---------------+   |
                                      |   |  Redis 7 Cache  |   | Mock Upstream |   |
                                      |   | (Token Bucket)  |   |  (30ms Sleep) |   |
                                      |   |   (Port 6379)   |   |  (Port 8081)  |   |
                                      |   +-----------------+   +---------------+   |
                                      +---------------------------------------------+
```

---

## 4. Benchmark Scenarios

The suite (`benchmarks/k6-load-test.js`) defines discrete scenarios running concurrently:

### Scenario 1: `nominal_traffic`
- **Configuration:** 50 VUs distributed across 10 distinct tenants (`tenant-01` through `tenant-10`).
- **Goal:** Validate steady-state gateway throughput and measure baseline latency overhead while tenants operate comfortably within their token replenishment budget (10 tokens/sec refill rate).
- **Traffic Profile:** Paced requests (`max_tokens: 1`, 550ms pacing) producing ~8.5 req/s per tenant.
- **Assertion:** 100% of requests MUST receive `HTTP 200 OK`.

### Scenario 2: `rate_limit_saturation`
- **Configuration:** 20 VUs dedicated to an over-consuming tenant (`tenant-burst`).
- **Goal:** Exercise high-velocity burst requests exceeding the tenant's token bucket capacity (100 tokens).
- **Traffic Profile:** High-velocity requests (`max_tokens: 120`, 20ms pacing) intentionally exceeding bucket capacity on each iteration.
- **Assertion:** 100% of requests MUST be throttled with `HTTP 429 Too Many Requests`.

### Scenario 3: `noisy_neighbor_isolation`
- **Configuration:** Dedicated partition between a saturated tenant (`tenant-noisy-a`) and an isolated, well-behaved tenant (`tenant-isolated-b`).
- **Goal:** Verify that a malicious or rogue tenant saturating their quota does not cause resource starvation, head-of-line blocking, or latency degradation for other tenants sharing the gateway.
- **Traffic Profile:**
  - `noisy_neighbor_tenant_a`: 25 VUs sending high-velocity bursts exceeding quota.
  - `noisy_neighbor_tenant_b`: 5 VUs sending conforming requests (`max_tokens: 1`, 600ms pacing) within quota (~4.5 req/s).
- **Assertion:** `tenant-noisy-a` MUST receive `HTTP 429`, while `tenant-isolated-b` MUST concurrently receive `HTTP 200 OK` with zero latency degradation.

---

## 5. Benchmark Results

### 5.1 Aggregate Summary

| Metric | Measurement | SLA / Target | Evaluation |
| :--- | :--- | :--- | :--- |
| **Duration** | 60 seconds | 60 seconds | Completed |
| **Concurrent VUs** | 100 VUs | 100–300 VUs | Satisfied |
| **Total Requests** | **121,557** | > 50,000 | **Exceeded** |
| **Sustained RPS** | **2,006.68 req/s** | > 1,000 req/s | **Exceeded** |
| **Check Assertions** | **121,557 / 121,557 (100.00%)** | > 99.00% | **100.00% Pass** |
| **Network Errors** | **0 (0.00%)** | 0 | **Flawless** |
| **Gateway Overhead p50** | **0.51 ms** | < 5.00 ms | **PASS** |
| **Gateway Overhead p95** | **3.62 ms** | < 5.00 ms | **PASS** |

---

### 5.2 Scenario Performance Breakdown

Captured directly from `benchmarks/results/full_summary.json` and `benchmarks/results/benchmark_summary.csv`:

| Metric / Scenario | Count / Rate | p50 | p90 | p95 | p99 | Avg | Max | SLA Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Gateway Overhead (< 5ms SLA)** | - | **0.51 ms** | **2.29 ms** | **3.62 ms** | 10.48 ms | 1.11 ms | 48.96 ms | **PASS (<5ms)** |
| **Nominal Traffic (10 Tenants)** | 5,139 (100.0% 200 OK) | 35.96 ms | 43.69 ms | 47.19 ms | 78.19 ms | 37.68 ms | 95.53 ms | **PASS** |
| **Tenant B (Isolated Neighbor)** | 475 (100.0% 200 OK) | 35.27 ms | 40.80 ms | 42.98 ms | 56.39 ms | 36.48 ms | 93.03 ms | **PASS** |
| **Tenant A (Noisy Saturated)** | 64,418 (100.0% 429 Blocked) | 2.14 ms | 6.07 ms | 7.53 ms | 12.21 ms | 2.93 ms | 62.22 ms | **PASS** |
| **Single-Tenant Saturation** | 51,525 (100.0% 429 Blocked) | 2.13 ms | 6.06 ms | 7.55 ms | 12.48 ms | 2.94 ms | 61.26 ms | **PASS** |

---

### 5.3 Latency Overhead & Telemetry Methodology (< 5ms Target)

#### Telemetric Gateway Overhead Header (`X-Gateway-Overhead-Ms`)
Measuring gateway overhead solely via client round-trip subtraction (`res.timings.duration - 30.0ms`) conflates upstream mock processing variations with gateway latency. In practice, the upstream Python `ThreadingHTTPServer` introduces significant thread scheduling jitter, GIL lock contention, and Docker bridge latency under 2,000+ RPS load, causing direct upstream responses to fluctuate between 33.5ms and 49.2ms (averaging ~36.5ms).

To eliminate upstream latency interference and measure true gateway performance, the Spring WebFlux Gateway injects the `X-Gateway-Overhead-Ms` HTTP header into all responses:
- **For Proxied 200 OK Responses:** The gateway records total processing time and subtracts the exact upstream invocation duration:
  $$\text{Overhead} = \frac{\Delta t_{\text{total}} - \Delta t_{\text{upstream}}}{1,000,000.0}\text{ ms}$$
  This captures the pure gateway pipeline: reactive context extraction, JSON token estimation (`TokenEstimator.java`), and atomic Redis token bucket evaluation (`token_bucket.lua`).
- **For Rate-Limited 429 Responses:** The header reflects the exact duration from request receipt to 429 response serialization:
  $$\text{Overhead} = \frac{\Delta t_{\text{total}}}{1,000,000.0}\text{ ms}$$

#### SLA Compliance Validation
- **p50 Overhead:** **0.51 ms** (Sub-millisecond processing at median)
- **p90 Overhead:** **2.29 ms**
- **p95 Overhead:** **3.62 ms** (Strictly within the **< 5.00 ms SLA target**)
- **Average Overhead:** **1.11 ms**

---

### 5.4 Multi-Tenant Noisy Neighbor Isolation

The load test conclusively proves strict tenant isolation under extreme stress:
- While **Tenant A** was bombarded with **64,418 requests** triggering continuous rate-limiting (100.0% 429s), **Tenant B** was simultaneously receiving continuous traffic.
- **Tenant B experienced 0 dropped requests and 100.00% HTTP 200 OK responses (475 / 475).**
- **Tenant B median latency (35.27 ms) was slightly lower than the nominal multi-tenant baseline (35.96 ms)**, confirming that heavy traffic from a noisy neighbor has zero negative impact on conforming tenants.

---

## 6. How to Reproduce Benchmarks

### Prerequisites
- Docker & Docker Compose (`docker compose version`)
- Grafana k6 (`brew install k6`)
- Python 3 (`python3 --version`)

### Step 1: Start Container Infrastructure
```bash
docker compose up -d --build
```
Verify health:
```bash
curl -s http://localhost:8080/health
# Output: OK
```

### Step 2: Run Smoke Verification Test
```bash
k6 run --vus 5 --duration 10s benchmarks/k6-load-test.js
```
Expected output:
- `checks_succeeded: 100.00%`
- Zero network errors
- Assertions passing for both 200 and 429 responses
- Median gateway overhead < 5ms

### Step 3: Run Full Benchmark Suite
```bash
bash benchmarks/run-benchmarks.sh
```
Expected output:
- Health check verification
- Smoke test execution
- Full 60-second multi-scenario benchmark across 100 concurrent VUs
- Generation of `benchmarks/results/full_summary.json` and `benchmarks/results/benchmark_summary.csv`
- ASCII formatted performance summary table printed to console confirming `< 5ms SLA PASS`
