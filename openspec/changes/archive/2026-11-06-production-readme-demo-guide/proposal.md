## Why

The Tenant-Aware Distributed Rate Limiter is fully engineered, containerized, benchmarked (>2,000 RPS sustained, <2.3ms p95 gateway overhead), and architecturally documented. However, the repository lacks a flagship production-grade `README.md` at the repository root, an automated interactive demonstration script (`scripts/demo.sh`) for rapid live verification, and an SDE-2 interview guide (`docs/interview-talking-points.md`) to articulate the technical depth, design trade-offs, and quantified achievements during technical interviews. Adding these flagship artifacts completes Task 9 of the implementation roadmap, providing immediate visibility and evaluation readiness for hiring teams and engineering reviewers.

## What Changes

- Add `scripts/demo.sh`: An automated, colorized 60-second interactive terminal demonstration script verifying 6 operational scenarios:
  1. Health check verification (`GET /health` -> 200 OK)
  2. Normal request with token estimation (`POST /v1/chat/completions` -> 200 OK)
  3. Token bucket decrement verification via Redis query (`hget rate_limit:<tenant> tokens`)
  4. Burst exhaustion demonstrating HTTP 429 Too Many Requests
  5. DDoS IP fallback toggle demonstration (`ratelimiter.fallback.ip.enabled` toggle / HTTP 401 Unauthorized)
  6. Graceful degradation / resilience demonstration (Redis service failure behavior)
- Add `README.md`: Flagship repository root README with badges, high-impact elevator pitch, LLM token-aware vs request-based rate limiting problem statement, embedded Mermaid system architecture diagram, tech stack rationale (Java 21, Spring Boot WebFlux, Redis Lua script, Jackson Streaming), verified benchmark performance highlights (2,002.31 RPS, 0.40ms p50, 2.24ms p95), quickstart instructions (`docker compose up -d` + curl), and configuration reference table.
- Add `docs/interview-talking-points.md`: An SDE-2 interview cheat-sheet and walkthrough script containing:
  - 5-minute Gate 1 live demo walkthrough script
  - 8 tough interviewer probing questions & model answers (e.g., Envoy/Kong vs custom gateway, clock drift handling in Redis, prompt length estimation trade-offs, noisy neighbor isolation, fail-closed vs fail-open security)
  - Quantified resume bullet points highlighting 2,002+ RPS, 100% burst enforcement, and multi-tenant isolation.
- Update `docs/plan-tenant-rate-limiter.md`: Synchronize Task 9 checkmarks upon completion.

## Capabilities

### New Capabilities
- `production-documentation-and-demo`: Covers the flagship repository README, automated multi-stage live demo script (`scripts/demo.sh`), and the SDE-2 interview talking points & demo guide (`docs/interview-talking-points.md`).

### Modified Capabilities

None.

## Impact

- Root Directory: Creates flagship `README.md`.
- Scripts: Creates executable `scripts/demo.sh`.
- Documentation: Creates `docs/interview-talking-points.md`.
- Dependencies / Runtime: No modifications to Java source code, pom dependencies, or Docker configuration; builds upon the existing Docker Compose topology and Gateway endpoints.
