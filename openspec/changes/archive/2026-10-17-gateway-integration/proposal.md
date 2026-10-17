## Why

With tenant identification (`TenantFilter`), payload cost estimation (`TokenEstimator`), and distributed rate limiting (`RateLimitService`) individually implemented, the gateway now requires an end-to-end routing pipeline that connects these components in Spring WebFlux. Without this integration, incoming client requests cannot be evaluated against tenant rate limits or routed to downstream endpoints.

## What Changes

- Modify `GatewayApplication.java` to define the main WebFlux routing and filtering flow for incoming requests (e.g., POST `/v1/chat/completions` or generic proxy/API endpoints).
- Connect `TenantFilter`, `TokenEstimator`, and `RateLimitService` into the WebFlux processing pipeline:
  - Extract tenant identity from request context (or header/IP fallback via `TenantFilter`).
  - Read request body and estimate required tokens using `TokenEstimator`.
  - Evaluate and deduct tokens atomically against Redis using `RateLimitService`.
  - Allow and route the request when sufficient tokens exist (HTTP 200).
  - Reject requests with HTTP 429 Too Many Requests when token limits are exceeded.
  - Reject malformed requests with HTTP 400 Bad Request if JSON parsing fails.
- Create an end-to-end integration test in `src/test/java/com/ratelimiter/GatewayIntegrationTest.java` using Testcontainers Redis to verify successful routing, token bucket deduction in Redis, and rate-limiting rejections.

## Capabilities

### New Capabilities
- `gateway-integration`: End-to-end WebFlux routing and rate-limiting pipeline combining tenant identification, request token estimation, and distributed Redis token bucket enforcement with HTTP 200, 400, 401, and 429 responses.

### Modified Capabilities

## Impact

- Modified file: `src/main/java/com/ratelimiter/GatewayApplication.java`.
- New test file: `src/test/java/com/ratelimiter/GatewayIntegrationTest.java`.
- Requires Redis for integration testing (using existing Testcontainers setup).
- Completes the core end-to-end pipeline of the tenant-aware distributed rate limiter.
