# PRD: Tenant-Aware Distributed Rate Limiter (API Gateway)

## Problem Statement
In multi-tenant LLM systems, a "noisy neighbor" can quickly exhaust shared API budgets and rate limits, causing cascading outages for other tenants. Traditional API gateways only restrict raw "requests per minute" (RPM), which is ineffective for LLM applications where a single request can consume anywhere from 10 to 32,000+ tokens. 

This project builds an intelligent, payload-aware API Gateway that enforces distributed rate limits based on **Tokens per Hour (TPH)** budget by parsing outgoing JSON payloads via streaming, estimating token cost, and atomically executing distributed rate limit checks using Redis Lua scripts at high concurrency with sub-5ms overhead.

## Target Architecture & Tech Stack
Based on architectural best practices for 100k+ RPS systems and SDE-2 interview benchmarks:
- **Language & Runtime:** Java 21+ with Spring Boot 3 (WebFlux / Reactor Netty for fully non-blocking asynchronous I/O).
- **Distributed State Management:** Redis (via reactive Lettuce client) utilizing an atomic **Token Bucket algorithm implemented in a single Lua script** with automated TTL expiration.
- **Memory-Safe Payload Streaming:** Jackson Streaming API (`JsonParser`) over reactive `DataBuffer` chunks. Avoids loading entire multi-megabyte JSON payloads into heap memory, preventing Out-Of-Memory (OOM) vulnerabilities.
- **Containerization & Orchestration:** Multi-stage `Dockerfile` and `docker-compose.yml` for unified local evaluation (Gateway + Redis + Mock Upstream LLM).
- **Performance Verification:** `k6` distributed load testing suite for throughput, concurrency, and latency profiling under simulated traffic.

## Design Decisions
- **Tenant Identification:** Identified via the `X-Tenant-Id` HTTP header, with an automatic fallback to client IP (via `X-Forwarded-For` or remote connection IP) when missing.
- **Dynamic Payload Estimation:** If `max_tokens` is present in the JSON payload, it is extracted as the token budget reservation. If missing, the cost is dynamically estimated from the character length of the `prompt` plus a configurable default buffer.
- **Pre-Routing Token Reservation:** To preserve extreme throughput and non-blocking backpressure, token costs are estimated and reserved upfront *before* routing to the upstream LLM.
- **Fail-Open Resilience:** If Redis becomes unavailable, the gateway fails open (passes requests with warning headers/logs) rather than causing a complete upstream outage.

## Non-Functional Requirements & Performance Targets
- **Gateway Overhead:** < 5ms p95 latency added to requests under nominal load.
- **Memory Footprint:** Constant streaming memory overhead; zero payload buffering into heap memory.
- **Concurrency & Atomicity:** Zero race conditions (TOCTOU) during token decrement across distributed gateway instances via Redis Lua execution.
- **Packaging:** 100% reproducible local run with a single command (`docker compose up`).

## User Stories
1. **As a tenant**, I want my API requests routed efficiently to the upstream LLM service without perceptible gateway latency.
2. **As an infrastructure engineer**, I want the gateway to inspect outgoing OpenAI/LLM requests, estimate token cost, and decrement the tenant's hourly budget in a centralized Redis store.
3. **As an infrastructure engineer**, I want requests exceeding a tenant's token budget to be immediately rejected with HTTP 429 Too Many Requests, saving upstream LLM costs.
4. **As an application developer**, I want drop-in integration by simply pointing my base API URL to the gateway and setting an `X-Tenant-Id` header.
5. **As an SRE / Evaluator**, I want a containerized deployment (`docker-compose`) so that I can spin up and test the Gateway, Redis, and Upstream Mock locally in seconds.
6. **As a system architect**, I want an automated load-testing suite (`k6`) providing reproducible benchmarks of throughput (RPS), error rates, and p50/p95/p99 latency.
7. **As an interviewer / code reviewer**, I want clear architecture diagrams, design trade-off rationales, and an interactive 5-minute demo script showcasing edge cases.

## Edge Cases Handled
1. **Redis Unavailability:** Gateway fails open, allowing traffic to pass through gracefully to prevent total outage.
2. **Missing Payload Fields:** Graceful fallback to prompt length token estimation.
3. **Invalid JSON / Malformed Payloads:** Rejected immediately with 400 Bad Request before hitting Redis or upstream.
4. **Upstream Timeouts:** Strict non-blocking timeouts enforced to return 504 Gateway Timeout.
5. **DDoS / Unauthenticated Floods:** IP fallback can be dynamically toggled via `ratelimiter.fallback.ip.enabled` to reject all unauthenticated requests with 401 Unauthorized.

## Deliverables & Roadmap Milestone Checklist
- [x] Phase 1: Core Proxy & Reactive WebFlux Routing
- [x] Phase 2: Tenant Identification Filter with IP fallback toggle
- [x] Phase 3: Token Estimation Parsing with Jackson Streaming API
- [x] Phase 4: Redis Token Bucket Lua Script & RateLimitService
- [x] Phase 5: Routing Integration & Gateway Error Handling
- [x] Phase 6: Docker Containerization & Local Multi-Container Environment
- [x] Phase 7: Concurrency & Performance Load Testing (`k6`) with Benchmark Report
- [x] Phase 8: System Architecture & Data Flow Visual Documentation
- [ ] Phase 9: Production Flagship `README.md` & 5-Minute Interview Walkthrough Guide
