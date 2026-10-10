## 1. Write Failing Tests

- [x] 1.1 Add Testcontainers Redis dependency to `pom.xml` (`org.testcontainers:testcontainers` and `org.testcontainers:junit-jupiter`, test scope).
- [x] 1.2 Write `RateLimitServiceTest.java` using Testcontainers Redis: test successful token deduction (bucket has enough tokens), test rejection when tokens are insufficient (no partial deduction), test bucket refill after elapsed time, and test first-request initialization at full capacity.
- [x] 1.3 Run tests to verify they fail (service and Lua script don't exist yet): `mvn test -Dtest=RateLimitServiceTest`.

## 2. Lua Script Implementation

- [x] 2.1 Create `src/main/resources/scripts/token_bucket.lua` implementing atomic token bucket: accept `KEYS[1]` (tenant bucket key), `ARGV[1]` (requested tokens), `ARGV[2]` (max capacity), `ARGV[3]` (refill rate per second). Use `redis.call('TIME')` for server-side timestamps. Store `tokens` and `last_refill_ts` as hash fields. Return `1` for allowed, `0` for denied.

## 3. RateLimitService Implementation

- [x] 3.1 Create `RateLimitService.java` as a Spring `@Service`. Load `token_bucket.lua` via `RedisScript<Long>` from classpath. Inject `ReactiveRedisTemplate<String, String>`. Expose `Mono<Boolean> isAllowed(String tenantId, int requestedTokens)`.
- [x] 3.2 Add rate limiter configuration properties to `application.yml` (`ratelimiter.bucket.capacity`, `ratelimiter.bucket.refill-rate`).

## 4. Verify and Commit

- [x] 4.1 Run all tests to verify they pass: `mvn test -Dtest=RateLimitServiceTest`.
- [x] 4.2 Run full test suite to ensure no regressions: `mvn test`.
- [x] 4.3 Commit: `git add . && git commit -m "feat: Redis token bucket Lua script and RateLimitService"`.
