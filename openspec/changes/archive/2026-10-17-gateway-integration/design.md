## Context

The gateway components have been implemented modularly across previous tasks:
- `TenantFilter` (Task 2) identifies tenants from `X-Tenant-Id` or falls back to IP address, propagating the tenant identifier into the Reactor Context under key `TenantFilter.TENANT_KEY`.
- `TokenEstimator` (Task 3) parses request payloads using Jackson's streaming API to estimate token costs from `max_tokens` or prompt length.
- `RateLimitService` (Task 4) executes an atomic Redis Lua script to check and deduct tokens from a per-tenant bucket.

This task integrates these components into the main WebFlux routing pipeline in `GatewayApplication.java` and validates the end-to-end behavior with a comprehensive integration test suite (`GatewayIntegrationTest.java`) using Testcontainers Redis.

## Goals / Non-Goals

**Goals:**
- Connect `TenantFilter`, `TokenEstimator`, and `RateLimitService` in `GatewayApplication.java` into an end-to-end WebFlux routing flow.
- Intercept incoming POST requests (e.g., `/v1/chat/completions` and API routes), retrieve tenant ID from Reactor Context, estimate token cost from the body, and deduct tokens in Redis.
- Return appropriate HTTP status codes:
  - `200 OK` when allowed and routed successfully.
  - `429 Too Many Requests` when tenant bucket capacity is exceeded.
  - `400 Bad Request` when the request payload is malformed JSON.
  - Maintain `401 Unauthorized` behavior from `TenantFilter` when tenant identification fails.
  - Maintain `/health` endpoint accessibility without rate limiting.
- Write a complete integration test (`GatewayIntegrationTest`) with Testcontainers Redis verifying request processing, Redis token balance decrement, and limit enforcement.

**Non-Goals:**
- Actual proxying or forwarding to upstream third-party LLM providers (e.g. OpenAI or Anthropic). The gateway handler terminates with a success response for routed requests.
- Dynamic per-tenant capacity/refill configuration overrides (all tenants use configured defaults from `application.yml`).

## Decisions

- **WebFlux RouterFunction Integration**:
  - *Decision*: Configure an API `RouterFunction<ServerResponse>` in `GatewayApplication.java` handling POST requests.
  - *Rationale*: Spring WebFlux functional routing offers lightweight, non-blocking composition and fits the existing `healthRouter` structure.
  - *Alternative considered*: A dedicated `WebFilter` for rate limiting. However, a router function directly represents route endpoints, body binding, and upstream routing handlers.

- **Reactor Context for Tenant Resolution**:
  - *Decision*: Read tenant ID from `Mono.deferContextual(ctx -> ctx.getOrDefault(TenantFilter.TENANT_KEY, ...))`.
  - *Rationale*: Integrates seamlessly with `TenantFilter` which already injects the tenant ID into the Reactor Context via `.contextWrite()`.

- **Reactive Payload Handling & Error Mapping**:
  - *Decision*: Extract payload bytes via `request.bodyToMono(byte[].class).defaultIfEmpty(new byte[0])`. Catch `IllegalArgumentException` from `TokenEstimator.estimate()` and map to `ServerResponse.badRequest()`.
  - *Rationale*: Handles both populated and empty payloads without blocking, and ensures malformed payloads fail fast with HTTP 400 before querying Redis.

- **Redis Assertion in Integration Tests**:
  - *Decision*: Use `ReactiveRedisTemplate` in `GatewayIntegrationTest` to read `rate_limit:{tenantId}` hash field `tokens` directly from Redis after requests.
  - *Rationale*: Explicitly verifies that Redis token balances decrease by the exact expected amount, satisfying Step 1 requirements.

## Risks / Trade-offs

- **[Risk] High memory consumption from buffering large request payloads** → Mitigation: `TokenEstimator` parses byte arrays; WebFlux enforces default codec buffer limits (`maxInMemorySize`). Future optimizations can stream chunks directly to the tokenizer parser.
- **[Risk] Missing or blank tenant in context despite filter execution** → Mitigation: Explicit null-check in the route handler returning 401 Unauthorized if tenant context is missing.
- **[Trade-off] Route terminates locally rather than proxying** → Acceptable for rate limiter gateway unit/integration milestone before downstream proxy client integration.
