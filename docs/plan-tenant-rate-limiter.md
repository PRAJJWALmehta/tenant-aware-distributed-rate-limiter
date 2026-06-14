# Tenant-Aware Rate Limiter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Java Spring WebFlux API Gateway that rate-limits outgoing LLM requests based on estimated token cost using a Redis Token Bucket Lua script.

**Architecture:** A standalone reverse proxy that intercepts POST requests, reads the `X-Tenant-Id` header, streams the JSON payload to estimate token cost (using `max_tokens` or prompt length), deducts from Redis via Lua script, and proxies to upstream.

**Tech Stack:** Java 21, Spring Boot 3 (WebFlux), Redis (Lettuce), Jackson.

---

### Task 1: Project Setup & Basic Proxy Routing

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/ratelimiter/GatewayApplication.java`
- Create: `src/main/resources/application.yml`
- Test: `src/test/java/com/ratelimiter/GatewayRoutingTest.java`

- [ ] **Step 1: Write the failing test**

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

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=GatewayRoutingTest`
Expected: FAIL (files missing/compilation error)

- [ ] **Step 3: Write minimal implementation**

Create `pom.xml` with `spring-boot-starter-webflux` and `spring-boot-starter-data-redis-reactive`.
Create `GatewayApplication.java` and a basic `/health` router in Spring WebFlux.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=GatewayRoutingTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat: project setup and health endpoint"
```

---

### Task 2: Tenant Identification Filter

**Files:**
- Create: `src/main/java/com/ratelimiter/filter/TenantFilter.java`
- Test: `src/test/java/com/ratelimiter/filter/TenantFilterTest.java`

- [ ] **Step 1: Write the failing test**

```java
// Test that requests missing X-Tenant-Id return 401 Unauthorized
```

- [ ] **Step 2: Run test to verify it fails**
- [ ] **Step 3: Write minimal implementation**
Extract `X-Tenant-Id` in a Spring WebFilter. If missing, return 401. If present, add to reactor context.
- [ ] **Step 4: Run test to verify it passes**
- [ ] **Step 5: Commit**

---

### Task 3: Token Estimation Parsing (Jackson Streaming)

**Files:**
- Create: `src/main/java/com/ratelimiter/service/TokenEstimator.java`
- Test: `src/test/java/com/ratelimiter/service/TokenEstimatorTest.java`

- [ ] **Step 1: Write failing test**
Verify it correctly extracts `max_tokens` from JSON. Verify fallback logic calculates `prompt` length + buffer.
- [ ] **Step 2: Run test**
- [ ] **Step 3: Write implementation**
Use `JsonFactory` and `JsonParser` to non-blockingly parse byte arrays and extract token values.
- [ ] **Step 4: Run test**
- [ ] **Step 5: Commit**

---

### Task 4: Redis Token Bucket Lua Script

**Files:**
- Create: `src/main/resources/scripts/token_bucket.lua`
- Create: `src/main/java/com/ratelimiter/service/RateLimitService.java`
- Test: `src/test/java/com/ratelimiter/service/RateLimitServiceTest.java`

- [ ] **Step 1: Write failing test**
Use Testcontainers Redis to verify bucket deduction and 429 rejections.
- [ ] **Step 2: Run test**
- [ ] **Step 3: Write implementation**
Write the Lua script for token bucket. Use Spring `ReactiveRedisTemplate` to execute the script asynchronously.
- [ ] **Step 4: Run test**
- [ ] **Step 5: Commit**

---

### Task 5: Integration

**Files:**
- Modify: `GatewayApplication.java`
- Test: `src/test/java/com/ratelimiter/GatewayIntegrationTest.java`

- [ ] **Step 1: Write failing test**
Send a complete POST request, verify Redis decreases, verify successful route.
- [ ] **Step 2: Run test**
- [ ] **Step 3: Write implementation**
Tie the TenantFilter, TokenEstimator, and RateLimitService together in the main WebFlux routing flow.
- [ ] **Step 4: Run test**
- [ ] **Step 5: Commit**
