## Context

The gateway already identifies tenants (via `TenantFilter`) and estimates token cost for each request (via `TokenEstimator`). The missing piece is a mechanism to atomically deduct estimated tokens from a per-tenant budget and reject requests that would exceed the budget. Redis is already a dependency (`spring-boot-starter-data-redis-reactive` in `pom.xml`, configured in `application.yml`).

The classic token bucket algorithm fits naturally: each tenant gets a bucket with a fixed capacity that refills at a constant rate. Before proxying a request, we deduct the estimated token cost from the bucket. If insufficient tokens remain, the request is rejected with HTTP 429.

## Goals / Non-Goals

**Goals:**
- Atomic check-and-deduct of tokens per tenant using a single Redis roundtrip (Lua script).
- Configurable bucket capacity and refill rate per second, settable via `application.yml`.
- Reactive, non-blocking execution via `ReactiveRedisTemplate.execute(RedisScript)`.
- Return enough metadata (allowed/denied, remaining tokens) for upstream callers.

**Non-Goals:**
- Per-tenant configuration (all tenants share the same capacity/refill settings for now; per-tenant overrides are a Task 5+ concern).
- Sliding window or leaky bucket algorithms. Token bucket is sufficient.
- Distributed locking or multi-key transactions. The Lua script is atomic on a single key.

## Decisions

- **Lua Script for Atomicity**: Redis Lua scripts execute atomically, eliminating race conditions between reading and writing bucket state. Alternative considered: Redis transactions (`MULTI/EXEC`), but they don't support conditional logic within the transaction.
- **Single-Key Design**: Store `tokens` (remaining count) and `last_refill_ts` (timestamp in milliseconds) as Redis hash fields under key `rate_limit:{tenantId}`. Alternative considered: two separate keys, but a hash keeps related state co-located and reduces key proliferation.
- **Time-Based Refill in Lua**: Calculate elapsed time since last refill and add tokens proportionally (capped at max capacity) within the script. This avoids needing a background refill process. Alternative considered: a scheduled refill job, but it adds complexity and doesn't scale per-tenant.
- **Spring `RedisScript<Long>`**: Use Spring's `RedisScript` abstraction to load the Lua file from classpath and cache the SHA. The script returns `1` for allowed, `0` for denied — simple numeric return avoids serialization complexity.
- **Testcontainers for Integration Tests**: Use `org.testcontainers:testcontainers` with a Redis container to test the full Lua + Redis flow. Alternative considered: embedded Redis (`it.ozimov:embedded-redis`), but Testcontainers is more reliable and closer to production behavior.

## Risks / Trade-offs

- **Risk: Redis single point of failure** → Mitigation: Out of scope for this task, but Redis Sentinel or Cluster can be configured later without changing the Lua script logic.
- **Risk: Clock drift in refill calculation** → Mitigation: The Lua script uses `redis.call('TIME')` for server-side timestamps, so all timing is relative to the Redis server clock, not the application.
- **Risk: Cold start — first request for a new tenant** → Mitigation: The Lua script initializes the bucket at full capacity on first access (no pre-provisioning needed).
- **Trade-off: All tenants share the same capacity config** → Acceptable for MVP; per-tenant overrides can layer on top via a config lookup before calling the script.
