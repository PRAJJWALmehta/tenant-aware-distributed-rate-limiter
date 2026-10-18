## Why

The gateway needs to enforce per-tenant token budgets to prevent any single tenant from exhausting LLM API capacity. We already have tenant identification (Task 2) and token estimation (Task 3), but no mechanism to atomically deduct estimated tokens from a shared budget. A Redis-backed token bucket with a Lua script provides atomic, distributed rate limiting that scales horizontally without race conditions.

## What Changes

- Implement a Redis Lua script (`token_bucket.lua`) that atomically checks and deducts tokens from a per-tenant bucket, supporting configurable capacity and refill rate.
- Create a `RateLimitService` that loads and executes the Lua script via Spring `ReactiveRedisTemplate`, returning allow/deny decisions as reactive types.
- Add Testcontainers-based integration tests that verify bucket deduction logic, refill behavior, and 429 rejection when tokens are exhausted.
- Add Testcontainers Redis dependency to `pom.xml`.

## Capabilities

### New Capabilities
- `token-bucket-rate-limiting`: Atomic token bucket rate limiting per tenant, backed by Redis Lua script with configurable capacity, refill rate, and reactive async execution.

### Modified Capabilities

## Impact

- New files: `src/main/resources/scripts/token_bucket.lua`, `src/main/java/com/ratelimiter/service/RateLimitService.java`.
- New test file: `src/test/java/com/ratelimiter/service/RateLimitServiceTest.java`.
- Modified: `pom.xml` (adds Testcontainers dependency for tests).
- Requires a running Redis instance (already configured in `application.yml`).
- Will be integrated into the request filter chain in Task 5.
