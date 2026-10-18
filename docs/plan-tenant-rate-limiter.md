# Tenant-Aware Rate Limiter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Java Spring WebFlux API Gateway that rate-limits outgoing LLM requests based on estimated token cost using a Redis Token Bucket Lua script.

**Architecture:** A standalone reverse proxy that intercepts POST requests, reads the `X-Tenant-Id` header, streams the JSON payload to estimate token cost (using `max_tokens` or prompt length), deducts from Redis via Lua script, and proxies to upstream.

**Tech Stack:** Java 21, Spring Boot 3 (WebFlux), Redis (Lettuce), Jackson Streaming API, Docker Compose, k6.

---

### Task 1: Project Setup & Basic Proxy Routing

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/ratelimiter/GatewayApplication.java`
- Create: `src/main/resources/application.yml`
- Test: `src/test/java/com/ratelimiter/GatewayRoutingTest.java`

- [x] **Step 1: Write the failing test**

```java
package com.ratelimiter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class GatewayRoutingTest {

    @Autowired
    private WebTestClient webClient;

    @Test
    public void testHealthEndpoint() {
        webClient.get().uri("/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("OK");
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=GatewayRoutingTest`
Expected: FAIL (files missing/compilation error)

- [x] **Step 3: Write minimal implementation**

Create `pom.xml` with `spring-boot-starter-webflux` and `spring-boot-starter-data-redis-reactive`.
Create `GatewayApplication.java` and a basic `/health` router in Spring WebFlux.

- [x] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=GatewayRoutingTest`
Expected: PASS

- [x] **Step 5: Commit**

```bash
git add .
git commit -m "feat: project setup and health endpoint"
```

---

### Task 2: Tenant Identification Filter

**Files:**
- Create: `src/main/java/com/ratelimiter/filter/TenantFilter.java`
- Test: `src/test/java/com/ratelimiter/filter/TenantFilterTest.java`

- [x] **Step 1: Write the failing test**

```java
// Test extracting X-Tenant-Id, X-Forwarded-For fallback, connection IP fallback, and 401 rejection when toggle is disabled
```

- [x] **Step 2: Run test to verify it fails**
- [x] **Step 3: Write minimal implementation**
Extract `X-Tenant-Id` in a Spring WebFilter. If missing, check `ratelimiter.fallback.ip.enabled` property. If true, extract from `X-Forwarded-For` or connection IP. If false, return 401. Add result to reactor context.
- [x] **Step 4: Run test to verify it passes**
- [x] **Step 5: Commit**

---

### Task 3: Token Estimation Parsing (Jackson Streaming)

**Files:**
- Create: `src/main/java/com/ratelimiter/service/TokenEstimator.java`
- Test: `src/test/java/com/ratelimiter/service/TokenEstimatorTest.java`

- [x] **Step 1: Write failing test**
Verify it correctly extracts `max_tokens` from JSON. Verify fallback logic calculates `prompt` length + buffer.
- [x] **Step 2: Run test**
- [x] **Step 3: Write implementation**
Use `JsonFactory` and `JsonParser` to non-blockingly parse byte arrays and extract token values.
- [x] **Step 4: Run test**
- [x] **Step 5: Commit**

---

### Task 4: Redis Token Bucket Lua Script

**Files:**
- Create: `src/main/resources/scripts/token_bucket.lua`
- Create: `src/main/java/com/ratelimiter/service/RateLimitService.java`
- Test: `src/test/java/com/ratelimiter/service/RateLimitServiceTest.java`

- [x] **Step 1: Write failing test**
Use Testcontainers Redis to verify bucket deduction and 429 rejections.
- [x] **Step 2: Run test**
- [x] **Step 3: Write implementation**
Write the Lua script for token bucket. Use Spring `ReactiveRedisTemplate` to execute the script asynchronously.
- [x] **Step 4: Run test**
- [x] **Step 5: Commit**

---

### Task 5: Integration

**Files:**
- Modify: `GatewayApplication.java`
- Test: `src/test/java/com/ratelimiter/GatewayIntegrationTest.java`

- [x] **Step 1: Write failing test**
Send a complete POST request, verify Redis decreases, verify successful route.
- [x] **Step 2: Run test**
- [x] **Step 3: Write implementation**
Tie the TenantFilter, TokenEstimator, and RateLimitService together in the main WebFlux routing flow.
- [x] **Step 4: Run test**
- [x] **Step 5: Commit**

---

### Task 6: Docker Containerization & Multi-Container Setup

**Files:**
- Create: `Dockerfile`
- Create: `docker-compose.yml`
- Create: `mock-upstream/Dockerfile`
- Create: `mock-upstream/server.py`
- Create: `scripts/verify-docker.sh`

- [x] **Step 1: Write upstream mock and verification script**

Create `mock-upstream/server.py` using Python `http.server` to return HTTP 200 with `{"choices": [{"text": "Mock LLM response"}]}` and simulated 30ms latency.
Create `scripts/verify-docker.sh` to automate health check, valid request verification, and 429 rate limit testing against docker containers.

- [x] **Step 2: Write multi-stage Dockerfile for Gateway & docker-compose.yml**

Create a multi-stage `Dockerfile`:
- Stage 1: Build JAR using `maven:3.9.6-eclipse-temurin-21-alpine`
- Stage 2: Runtime image using `eclipse-temurin:21-jre-alpine`
Create `docker-compose.yml` wiring:
- `redis:7-alpine` on port 6379
- `mock-upstream` on port 8081
- `gateway` on port 8080 (configured with `SPRING_DATA_REDIS_HOST=redis` and `UPSTREAM_URL=http://mock-upstream:8081`)

- [x] **Step 3: Run docker-compose build and launch services**

Run: `docker compose up -d --build`
Expected: Containers `redis`, `mock-upstream`, and `gateway` start in healthy status.

- [x] **Step 4: Run verification script to confirm end-to-end routing**

Run: `bash scripts/verify-docker.sh`
Expected:
- GET `http://localhost:8080/health` -> HTTP 200 "OK"
- POST `http://localhost:8080/v1/chat/completions` with `X-Tenant-Id: test-tenant` -> HTTP 200
- Redis key `rate_limit:test-tenant` decremented
- Repeated requests exceeding budget -> HTTP 429 "Too Many Requests"

- [x] **Step 5: Commit**

```bash
git add Dockerfile docker-compose.yml mock-upstream/ scripts/verify-docker.sh
git commit -m "feat: add multi-stage Dockerfile and docker-compose orchestration"
```

---

### Task 7: Concurrency & Performance Load Testing (k6)

**Files:**
- Create: `benchmarks/k6-load-test.js`
- Create: `benchmarks/run-benchmarks.sh`
- Create: `docs/BENCHMARKS.md`

- [ ] **Step 1: Write k6 load testing suite**

Create `benchmarks/k6-load-test.js` implementing three discrete test scenarios:
1. `nominal_traffic`: 50 VUs across 10 tenants within budget to measure baseline latency overhead (p50, p95, p99).
2. `rate_limit_saturation`: High-velocity burst on a single tenant asserting exact HTTP 429 status code returns.
3. `noisy_neighbor_isolation`: Concurrent load where Tenant A saturates budget and gets 429s, while Tenant B continues receiving 200 OK without latency degradation.

- [ ] **Step 2: Run smoke verification test**

Run: `k6 run --vus 5 --duration 10s benchmarks/k6-load-test.js`
Expected: 0 network errors, successful assertions on 200 and 429 responses.

- [ ] **Step 3: Execute full benchmark run**

Run: `bash benchmarks/run-benchmarks.sh`
Expected: 100-300 concurrent VUs sustained for 60 seconds; exports JSON/CSV summary statistics.

- [ ] **Step 4: Document benchmark results in `docs/BENCHMARKS.md`**

Synthesize test outputs:
- Nominal RPS sustained
- Gateway p50, p95, p99 latency overhead (< 5ms target)
- Error rate / 429 assertion accuracy
- Hardware context (e.g. Apple Silicon M-series / CPU / Memory)

- [ ] **Step 5: Commit**

```bash
git add benchmarks/ docs/BENCHMARKS.md
git commit -m "feat: add k6 load testing suite and performance benchmark report"
```

---

### Task 8: System Architecture & Visual Design Artifacts

**Files:**
- Create: `docs/architecture/ARCHITECTURE.md`
- Create: `docs/architecture/diagrams/request-lifecycle.mermaid`
- Create: `docs/architecture/diagrams/sequence-flows.mermaid`

- [ ] **Step 1: Draft Mermaid component & request lifecycle diagram**

Create `docs/architecture/diagrams/request-lifecycle.mermaid`:
- Client -> `TenantFilter` (Extract `X-Tenant-Id` / Fallback to IP) -> `TokenEstimator` (Jackson Streaming `max_tokens` / prompt) -> `RateLimitService` (Lettuce Reactive Redis Lua) -> Upstream LLM Router / 429 Handler.

- [ ] **Step 2: Draft Mermaid sequence diagram for request flows**

Create `docs/architecture/diagrams/sequence-flows.mermaid`:
- Sequence 1: Happy path (200 OK + token deduction)
- Sequence 2: Budget exhausted (429 Too Many Requests)
- Sequence 3: Missing tenant header with IP fallback disabled (401 Unauthorized)
- Sequence 4: Redis connection failure (Fail-open path)

- [ ] **Step 3: Write comprehensive `ARCHITECTURE.md`**

Detail core engineering decisions and SDE-2 trade-offs:
- *Memory Safety:* Streaming `JsonParser` vs `ObjectMapper.readTree()` (O(1) memory vs O(N) heap allocation).
- *Atomicity:* Single Redis Lua script vs Distributed Locks / Redlock (eliminating two-phase lock contention and network roundtrips).
- *Reactive Pipeline:* Spring WebFlux / Reactor Netty event loops vs blocking Servlet threads (100k+ concurrent connections with minimal threads).

- [ ] **Step 4: Verify diagrams and markdown rendering**

Verify all Mermaid syntax blocks render cleanly and match the production codebase.

- [ ] **Step 5: Commit**

```bash
git add docs/architecture/
git commit -m "docs: add system architecture diagrams and deep dive documentation"
```

---

### Task 9: Production Flagship README & 5-Minute Interview Demo Guide

**Files:**
- Create: `scripts/demo.sh`
- Create: `docs/interview-talking-points.md`
- Create: `README.md`

- [ ] **Step 1: Write automated 60-second interactive demo script**

Create `scripts/demo.sh` with colored terminal output:
1. Health check verification (`GET /health`)
2. Normal request with token estimation (`POST /v1/chat/completions` -> 200 OK)
3. Token bucket decrement verification via Redis query
4. Burst exhaustion demonstrating 429 Too Many Requests
5. DDoS IP fallback toggle demonstration (401 Unauthorized)
6. Graceful degradation / fail-open demonstration

- [ ] **Step 2: Run demo script to verify all steps pass**

Run: `bash scripts/demo.sh`
Expected: Clean terminal output demonstrating all 6 stages without errors.

- [ ] **Step 3: Create flagship `README.md` in repository root**

Include:
- Project title, badges, and high-impact elevator pitch
- Problem Statement & Why Request-Based Rate Limiting Fails for LLMs
- Architecture Diagram (embedded Mermaid)
- Tech Stack with rationale (Java 21, WebFlux, Redis Lua, Jackson Streaming)
- Performance & Benchmarking Highlights (RPS, p95 latency)
- Quickstart Guide (`docker compose up -d` + curl examples)
- Configuration Reference table

- [ ] **Step 4: Write `docs/interview-talking-points.md`**

Create structured cheat-sheet for SDE-2 interviews:
- 5-minute Gate 1 live demo walkthrough script
- 8 tough interviewer probing questions & model answers (e.g., "Why not use Envoy or Kong?", "How do you handle clock drift in Redis?", "What happens if a prompt length estimation is inaccurate?")
- Quantified resume bullet points ready for Phase 2 (Nov 23).

- [ ] **Step 5: Commit**

```bash
git add README.md docs/interview-talking-points.md scripts/demo.sh
git commit -m "docs: add production README, demo script, and interview talking points"
```
